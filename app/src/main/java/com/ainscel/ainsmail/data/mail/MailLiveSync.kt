package com.ainscel.ainsmail.data.mail

import android.util.Log
import com.ainscel.ainsmail.data.imap.FetchedHeaders
import com.ainscel.ainsmail.data.imap.ImapConnectResult
import com.ainscel.ainsmail.data.imap.fetchHeadersAfterUid
import com.ainscel.ainsmail.data.imap.fetchHeadersSince
import com.ainscel.ainsmail.data.imap.markSeen
import com.ainscel.ainsmail.data.imap.openImapStore
import com.ainscel.ainsmail.data.imap.readHeadersAfterUid
import com.ainscel.ainsmail.data.imap.resolveRemoteFolders
import com.ainscel.ainsmail.data.imap.storeSeen
import com.ainscel.ainsmail.data.imap.storeSupportsIdle
import com.ainscel.ainsmail.model.RemoteFolder
import com.ainscel.ainsmail.model.Section
import com.ainscel.ainsmail.security.AccountBook
import jakarta.mail.Folder
import jakarta.mail.Store
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.eclipse.angus.mail.imap.IMAPFolder

/**
 * 当前范围的 UID 增量、IDLE，以及已读回写。
 * 归档只留在本机，这里不移动服务器上的信。
 */
class MailLiveSync(
    private val book: AccountBook,
    private val store: MailLocalStore,
    private val io: CoroutineDispatcher,
) {
    /** 后台只补已经有游标的收件箱，不按日期重扫，也不拉别的夹。 */
    suspend fun syncInboxes(accountIds: List<String>) {
        if (MailSyncGate.foreground) return
        withContext(io) {
            for (accountId in accountIds) {
                if (MailSyncGate.foreground) return@withContext
                if (MailSyncGate.isDropped(accountId)) continue
                val cursor = store.cursor(accountId, RemoteFolder.Inbox.name) ?: continue
                if (!cursor.recentSyncDone) continue
                refreshAccount(accountId, RemoteFolder.Inbox, allowWindow = false)
            }
        }
    }

    suspend fun pushSeen(accountIds: List<String>) = withContext(io) {
        for (accountId in accountIds.distinct()) {
            if (MailSyncGate.isDropped(accountId)) continue
            if (store.pendingSeen(accountId).isEmpty()) continue
            pushSeenAccount(accountId)
        }
    }

    /** 直到调用方取消。服务器没有 IDLE 的账号在这里改成 15 分钟拉同一夹。 */
    suspend fun watch(
        accountIds: List<String>,
        section: Section,
        onHint: (FirstSyncOutcome) -> Unit,
    ) {
        val folder = idleFolder(section)
        withContext(io) {
            coroutineScope {
                for (accountId in accountIds) {
                    launch { watchAccount(accountId, folder, onHint) }
                }
            }
        }
    }

    private suspend fun refreshAccount(
        accountId: String,
        folder: RemoteFolder,
        allowWindow: Boolean,
    ): FirstSyncOutcome {
        if (MailSyncGate.isDropped(accountId)) return FirstSyncOutcome.Ok
        return MailSyncGate.withAccount(accountId) {
            if (MailSyncGate.isDropped(accountId)) return@withAccount FirstSyncOutcome.Ok
            withStore(accountId) { imap ->
                pull(accountId, folder, imap, allowWindow, openFolder = null, FolderPathCache(imap))
            }
        }
    }

    private suspend fun pushSeenAccount(accountId: String) {
        MailSyncGate.withAccount(accountId) {
            if (MailSyncGate.isDropped(accountId)) return@withAccount
            withStore(accountId) { imap ->
                pushSeenOn(imap, accountId, openFolder = null, openFolderName = null)
                FirstSyncOutcome.Ok
            }
        }
    }

    private suspend fun watchAccount(
        accountId: String,
        folder: RemoteFolder,
        onHint: (FirstSyncOutcome) -> Unit,
    ) {
        val slot = MailSyncGate.slot(accountId)
        var imap: Store? = null
        var idleFolder: IMAPFolder? = null
        var paths: FolderPathCache? = null
        try {
            while (currentCoroutineContext().isActive && !MailSyncGate.isDropped(accountId)) {
                var idling = false
                var poll = false
                slot.lock.withLock {
                    if (MailSyncGate.isDropped(accountId)) return
                    val link = ensureStore(imap, accountId)
                    when (link) {
                        is StoreLink.Halt -> {
                            onHint(link.outcome)
                            closeStore(accountId, imap)
                            imap = null
                            paths = null
                            if (link.outcome is FirstSyncOutcome.NeedsReauth) return
                            poll = true
                        }
                        is StoreLink.Open -> {
                            if (imap !== link.store) {
                                imap = link.store
                                paths = FolderPathCache(link.store)
                            }
                            val cache = paths ?: FolderPathCache(link.store).also { paths = it }
                            val outcome = pull(accountId, folder, link.store, allowWindow = true, idleFolder, cache)
                            if (outcome !is FirstSyncOutcome.Ok) onHint(outcome)
                            if (outcome is FirstSyncOutcome.NeedsReauth) return
                            val path = cache.path(folder)
                            val next = path?.let { link.store.getFolder(it) as? IMAPFolder }
                            if (next == null || !storeSupportsIdle(link.store)) {
                                poll = true
                            } else {
                                if (!next.isOpen) next.open(Folder.READ_WRITE)
                                idleFolder = next
                                MailSyncGate.markIdling(slot, next)
                                idling = true
                            }
                        }
                    }
                }
                if (!currentCoroutineContext().isActive || MailSyncGate.isDropped(accountId)) break
                if (poll) {
                    closeStore(accountId, imap)
                    imap = null
                    paths = null
                    idleFolder = null
                    delay(SYNC_POLL_MS)
                    continue
                }
                if (!idling) continue
                val waiting = idleFolder
                if (waiting == null) continue
                try {
                    awaitIdle(accountId, waiting)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Log.w(TAG, "idle account=$accountId ${error.javaClass.simpleName}: ${error.message}")
                    poll = true
                } finally {
                    releaseIdle(slot)
                }
                if (poll) {
                    closeStore(accountId, imap)
                    imap = null
                    paths = null
                    idleFolder = null
                    delay(SYNC_POLL_MS)
                }
            }
        } finally {
            withContext(NonCancellable) {
                releaseIdle(slot)
                closeStore(accountId, imap)
            }
        }
    }

    /** 取消已经发生时仍要放开槽，否则下一轮保活会盖住这条连接。 */
    private suspend fun releaseIdle(slot: AccountSlot) {
        withContext(NonCancellable) {
            slot.lock.withLock { MailSyncGate.markFree(slot) }
        }
    }

    private suspend fun pull(
        accountId: String,
        folder: RemoteFolder,
        imap: Store,
        allowWindow: Boolean,
        openFolder: Folder?,
        paths: FolderPathCache,
    ): FirstSyncOutcome {
        if (MailSyncGate.isDropped(accountId)) return FirstSyncOutcome.Ok
        val path = paths.path(folder) ?: return FirstSyncOutcome.Ok
        val cursor = store.cursor(accountId, folder.name)
        if (cursor != null && cursor.recentSyncDone) {
            val probed = readNew(openFolder, imap, path, cursor.highestUid)
            if (uidValidityChanged(cursor.uidValidity, probed.uidValidity)) {
                if (openFolder?.isOpen == true) openFolder.close(false)
                rebuildFolder(accountId, folder, path, imap)
                pushSeenOn(imap, accountId, openFolder = null, openFolderName = null)
                return FirstSyncOutcome.Ok
            }
            saveIncremental(accountId, folder, path, cursor, probed)
            if (cursor.historySyncDone || !allowWindow) {
                pushSeenOn(imap, accountId, openFolder, folder.name)
                return FirstSyncOutcome.Ok
            }
        } else if (!allowWindow) {
            return FirstSyncOutcome.Ok
        }
        if (openFolder?.isOpen == true) openFolder.close(false)
        fillWindow(accountId, folder, path, imap)
        pushSeenOn(imap, accountId, openFolder = null, openFolderName = null)
        return FirstSyncOutcome.Ok
    }

    /**
     * 在单独线程上等待 IDLE。取消时立刻打断，不等这条阻塞调用自己结束。
     * 这样 IO 调度器也不会被一次最长约半小时的等待占住。
     */
    private suspend fun awaitIdle(accountId: String, folder: IMAPFolder) {
        suspendCancellableCoroutine { cont ->
            cont.invokeOnCancellation { MailSyncGate.requestAbort(accountId) }
            if (!cont.isActive) return@suspendCancellableCoroutine
            Thread {
                val result = runCatching { folder.idle() }
                if (!cont.isActive) return@Thread
                try {
                    val error = result.exceptionOrNull()
                    if (error == null) cont.resume(Unit) else cont.resumeWithException(error)
                } catch (closed: IllegalStateException) {
                    // 取消和 idle 返回撞在一起时，协程已经结束。
                }
            }.apply {
                isDaemon = true
                name = "ainsmail-idle"
            }.start()
        }
    }

    private fun readNew(openFolder: Folder?, imap: Store, path: String, highestUid: Long): FetchedHeaders {
        if (openFolder != null && openFolder.isOpen) return readHeadersAfterUid(openFolder, highestUid)
        return fetchHeadersAfterUid(imap, path, highestUid)
    }

    private suspend fun fillWindow(accountId: String, folder: RemoteFolder, path: String, imap: Store) {
        if (MailSyncGate.isDropped(accountId)) return
        val now = System.currentTimeMillis()
        val recentStart = now - FIRST_SYNC_RECENT_DAYS * DAY_MS
        val historyStart = now - FIRST_SYNC_HISTORY_DAYS * DAY_MS
        var cursor = store.cursor(accountId, folder.name)
        if (cursor?.recentSyncDone != true) {
            val recent = fetchHeadersSince(imap, path, recentStart, null, null)
            saveWindow(accountId, folder, path, recent, cursor, recentDone = true, historyDone = cursor?.historySyncDone == true)
            cursor = store.cursor(accountId, folder.name)
        }
        if (cursor?.historySyncDone == true || MailSyncGate.isDropped(accountId)) return
        if (!shouldFillHistory(store.countAll())) {
            markThisHistoryDone(cursor, accountId, folder, path)
            return
        }
        val budget = historyFillBudget(store.countAll())
        if (budget <= 0) {
            markThisHistoryDone(cursor, accountId, folder, path)
            return
        }
        val history = fetchHeadersSince(imap, path, historyStart, recentStart - 1, budget)
        saveWindow(accountId, folder, path, history, cursor, recentDone = true, historyDone = true)
    }

    private suspend fun rebuildFolder(accountId: String, folder: RemoteFolder, path: String, imap: Store) {
        if (MailSyncGate.isDropped(accountId)) return
        val existing = store.headersInFolder(accountId, folder.name)
        val preserved = existing.map { it.toPreserved() }
        val now = System.currentTimeMillis()
        val recentStart = now - FIRST_SYNC_RECENT_DAYS * DAY_MS
        val historyStart = now - FIRST_SYNC_HISTORY_DAYS * DAY_MS
        val recent = fetchHeadersSince(imap, path, recentStart, null, null)
        val others = (store.countAll() - existing.size).coerceAtLeast(0)
        var history = FetchedHeaders(uidValidity = recent.uidValidity, headers = emptyList())
        if (shouldFillHistory(others + recent.headers.size)) {
            val budget = historyFillBudget(others + recent.headers.size)
            if (budget > 0) {
                history = fetchHeadersSince(imap, path, historyStart, recentStart - 1, budget)
            }
        }
        val validity = recent.uidValidity.takeIf { it > 0L } ?: history.uidValidity
        if (validity <= 0L || MailSyncGate.isDropped(accountId)) return
        val incoming = (recent.headers + history.headers).map { it.toEntity(accountId, folder) }
        val rebuilt = reapplyPreserved(incoming, preserved)
        val kept = rebuilt.kept.map { row ->
            if (row.uidValidity == validity) row else row.copy(uidValidity = validity)
        }
        store.replaceFolder(accountId, folder.name, rebuilt.synced + kept)
        val highest = rebuilt.synced.maxOfOrNull { it.uid } ?: 0L
        store.upsertCursor(
            FolderSyncCursorEntity(
                accountId = accountId,
                remoteFolder = folder.name,
                folderPath = path,
                uidValidity = validity,
                highestUid = highest,
                recentSyncDone = true,
                historySyncDone = true,
            ),
        )
    }

    private suspend fun saveIncremental(
        accountId: String,
        folder: RemoteFolder,
        path: String,
        cursor: FolderSyncCursorEntity,
        fetched: FetchedHeaders,
    ) {
        if (MailSyncGate.isDropped(accountId)) return
        if (fetched.headers.isNotEmpty()) {
            store.upsertHeaders(fetched.headers.map { it.toEntity(accountId, folder) })
        }
        val validity = fetched.uidValidity.takeIf { it > 0L } ?: cursor.uidValidity
        val highest = maxOf(cursor.highestUid, fetched.headers.maxOfOrNull { it.uid } ?: 0L)
        store.upsertCursor(
            cursor.copy(folderPath = path, uidValidity = validity, highestUid = highest),
        )
    }

    private suspend fun saveWindow(
        accountId: String,
        folder: RemoteFolder,
        path: String,
        fetched: FetchedHeaders,
        cursor: FolderSyncCursorEntity?,
        recentDone: Boolean,
        historyDone: Boolean,
    ) {
        if (MailSyncGate.isDropped(accountId)) return
        store.upsertHeaders(fetched.headers.map { it.toEntity(accountId, folder) })
        val highest = maxOf(cursor?.highestUid ?: 0L, fetched.headers.maxOfOrNull { it.uid } ?: 0L)
        store.upsertCursor(
            FolderSyncCursorEntity(
                accountId = accountId,
                remoteFolder = folder.name,
                folderPath = path,
                uidValidity = fetched.uidValidity.takeIf { it > 0L } ?: cursor?.uidValidity ?: 0L,
                highestUid = highest,
                recentSyncDone = recentDone,
                historySyncDone = historyDone,
            ),
        )
    }

    private suspend fun markThisHistoryDone(
        cursor: FolderSyncCursorEntity?,
        accountId: String,
        folder: RemoteFolder,
        path: String,
    ) {
        val current = cursor ?: store.cursor(accountId, folder.name) ?: return
        if (current.historySyncDone) return
        store.upsertCursor(current.copy(folderPath = path, historySyncDone = true))
    }

    private suspend fun pushSeenOn(
        imap: Store,
        accountId: String,
        openFolder: Folder?,
        openFolderName: String?,
    ) {
        val pending = store.pendingSeen(accountId)
        if (pending.isEmpty() || MailSyncGate.isDropped(accountId)) return
        val byFolder = LinkedHashMap<String, MutableList<MailHeaderEntity>>()
        for (row in pending) {
            byFolder.getOrPut(row.remoteFolder) { ArrayList() }.add(row)
        }
        for ((folderName, rows) in byFolder) {
            if (openFolder != null && folderName != openFolderName) continue
            val cursor = store.cursor(accountId, folderName) ?: continue
            if (cursor.folderPath.isBlank()) continue
            try {
                if (openFolder != null && openFolder.isOpen && folderName == openFolderName) {
                    markSeen(openFolder, rows.map { it.uid })
                } else {
                    storeSeen(imap, cursor.folderPath, rows.map { it.uid })
                }
                store.clearSeenPending(rows.map { it.id })
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w(TAG, "seen account=$accountId ${error.javaClass.simpleName}: ${error.message}")
            }
        }
    }

    private suspend fun withStore(
        accountId: String,
        block: suspend (Store) -> FirstSyncOutcome,
    ): FirstSyncOutcome {
        val credentials = book.credentialsFor(accountId)
            ?: return FirstSyncOutcome.Failed(accountId, "missing_credentials")
        return when (val connected = openImapStore(credentials)) {
            is ImapConnectResult.NeedsReauth -> FirstSyncOutcome.NeedsReauth(accountId)
            is ImapConnectResult.Failed -> {
                Log.w(TAG, "imap_connect account=$accountId reason=${connected.reason}")
                FirstSyncOutcome.Failed(accountId, connected.reason)
            }
            is ImapConnectResult.Ok -> {
                try {
                    block(connected.store)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Log.w(TAG, "imap_sync account=$accountId ${error.javaClass.simpleName}: ${error.message}")
                    FirstSyncOutcome.Failed(
                        accountId,
                        error.javaClass.simpleName + ": " + error.message.orEmpty().take(120),
                    )
                } finally {
                    closeStore(accountId, connected.store)
                }
            }
        }
    }

    private fun ensureStore(existing: Store?, accountId: String): StoreLink {
        if (existing != null && existing.isConnected) return StoreLink.Open(existing)
        closeStore(accountId, existing)
        val credentials = book.credentialsFor(accountId)
            ?: return StoreLink.Halt(FirstSyncOutcome.Failed(accountId, "missing_credentials"))
        return when (val connected = openImapStore(credentials)) {
            is ImapConnectResult.NeedsReauth -> StoreLink.Halt(FirstSyncOutcome.NeedsReauth(accountId))
            is ImapConnectResult.Failed -> {
                Log.w(TAG, "imap_connect account=$accountId reason=${connected.reason}")
                StoreLink.Halt(FirstSyncOutcome.Failed(accountId, connected.reason))
            }
            is ImapConnectResult.Ok -> StoreLink.Open(connected.store)
        }
    }

    private fun closeStore(accountId: String, store: Store?) {
        if (store == null) return
        try {
            store.close()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.w(TAG, "imap_close account=$accountId ${error.javaClass.simpleName}: ${error.message}")
        }
    }

    private sealed interface StoreLink {
        data class Open(val store: Store) : StoreLink
        data class Halt(val outcome: FirstSyncOutcome) : StoreLink
    }

    /** 同一条连接只列一次文件夹。换了 Store 就丢掉，下次重连再列。 */
    private class FolderPathCache(private val imap: Store) {
        private var paths: Map<RemoteFolder, String>? = null

        fun path(folder: RemoteFolder): String? {
            val known = paths ?: resolveRemoteFolders(imap).also { paths = it }
            return known[folder]
        }
    }

    private companion object {
        const val TAG = "AinsmailSync"
        const val DAY_MS = 24L * 60L * 60L * 1000L
    }
}
