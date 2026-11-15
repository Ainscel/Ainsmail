package com.ainscel.ainsmail.data.mail

import android.util.Log
import com.ainscel.ainsmail.data.imap.FetchedHeaders
import com.ainscel.ainsmail.data.imap.ImapConnectResult
import com.ainscel.ainsmail.data.imap.fetchHeadersSince
import com.ainscel.ainsmail.data.imap.openImapStore
import com.ainscel.ainsmail.data.imap.resolveRemoteFolders
import com.ainscel.ainsmail.model.RemoteFolder
import com.ainscel.ainsmail.security.AccountBook
import com.ainscel.ainsmail.security.ProviderCredentials
import jakarta.mail.Store
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

sealed interface FirstSyncOutcome {
    data object Ok : FirstSyncOutcome
    data class NeedsReauth(val accountId: String) : FirstSyncOutcome
    data class Failed(val accountId: String, val reason: String) : FirstSyncOutcome
}

/**
 * 首次同步：先各夹最近 7 天信头，再按全局 50 封 / 30 天补历史。
 * 失败不清库。网络与解析都在 [io]。
 */
class MailFirstSync(
    private val book: AccountBook,
    private val store: MailLocalStore,
    private val io: CoroutineDispatcher,
) {
    private val mutex = Mutex()

    suspend fun syncAll(accountIds: List<String>): List<FirstSyncOutcome> = mutex.withLock {
        withContext(io) {
            val outcomes = ArrayList<FirstSyncOutcome>()
            val now = System.currentTimeMillis()
            val recentStart = now - FIRST_SYNC_RECENT_DAYS * DAY_MS
            val historyStart = now - FIRST_SYNC_HISTORY_DAYS * DAY_MS

            for (accountId in accountIds) {
                outcomes += syncAccountRecent(accountId, recentStart)
            }

            val recentTotal = store.countAll()
            if (!shouldFillHistory(recentCount = recentTotal, totalCount = recentTotal)) {
                return@withContext outcomes
            }

            for (accountId in accountIds) {
                val budget = historyFillBudget(store.countAll())
                if (budget <= 0) break
                val history = syncAccountHistory(accountId, historyStart, recentStart, budget)
                if (history != null) outcomes += history
            }
            outcomes
        }
    }

    private suspend fun syncAccountRecent(
        accountId: String,
        recentStart: Long,
    ): FirstSyncOutcome {
        val credentials = book.credentialsFor(accountId)
            ?: return FirstSyncOutcome.Failed(accountId, "missing_credentials")
        return withStore(accountId, credentials) { imap ->
            val folders = resolveRemoteFolders(imap)
            for ((folder, path) in folders) {
                val cursor = store.cursor(accountId, folder.name)
                if (cursor?.recentSyncDone == true) continue
                val fetched = fetchHeadersSince(
                    store = imap,
                    folderPath = path,
                    sinceEpochMs = recentStart,
                )
                saveFetched(accountId, folder, path, fetched, cursor, recentDone = true, historyDone = false)
            }
            // 存档不是服务器夹，不拉。
            FirstSyncOutcome.Ok
        }
    }

    private suspend fun syncAccountHistory(
        accountId: String,
        historyStart: Long,
        recentStart: Long,
        budget: Int,
    ): FirstSyncOutcome? {
        if (budget <= 0) return null
        val credentials = book.credentialsFor(accountId) ?: return null
        return withStore(accountId, credentials) { imap ->
            val folders = resolveRemoteFolders(imap)
            var remaining = budget
            for ((folder, path) in folders) {
                if (remaining <= 0) break
                val cursor = store.cursor(accountId, folder.name)
                if (cursor?.historySyncDone == true) continue
                val fetched = fetchHeadersSince(
                    store = imap,
                    folderPath = path,
                    sinceEpochMs = historyStart,
                    untilEpochMs = recentStart - 1,
                    limit = remaining,
                )
                saveFetched(
                    accountId,
                    folder,
                    path,
                    fetched,
                    cursor,
                    recentDone = cursor?.recentSyncDone == true,
                    historyDone = true,
                )
                remaining -= fetched.headers.size
            }
            FirstSyncOutcome.Ok
        }
    }

    private suspend fun saveFetched(
        accountId: String,
        folder: RemoteFolder,
        path: String,
        fetched: FetchedHeaders,
        cursor: FolderSyncCursorEntity?,
        recentDone: Boolean,
        historyDone: Boolean,
    ) {
        store.upsertHeaders(fetched.headers.map { it.toEntity(accountId, folder) })
        val highest = maxOf(cursor?.highestUid ?: 0L, fetched.headers.maxOfOrNull { it.uid } ?: 0L)
        store.upsertCursor(
            FolderSyncCursorEntity(
                accountId = accountId,
                remoteFolder = folder.name,
                folderPath = path,
                uidValidity = fetched.uidValidity.takeIf { it > 0 } ?: cursor?.uidValidity ?: 0L,
                highestUid = highest,
                recentSyncDone = recentDone,
                historySyncDone = historyDone,
            ),
        )
    }

    private suspend fun withStore(
        accountId: String,
        credentials: ProviderCredentials,
        block: suspend (Store) -> FirstSyncOutcome,
    ): FirstSyncOutcome {
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
                    Log.w(
                        TAG,
                        "imap_sync account=$accountId ${error.javaClass.simpleName}: ${error.message}",
                    )
                    FirstSyncOutcome.Failed(
                        accountId,
                        error.javaClass.simpleName + ": " + error.message.orEmpty().take(120),
                    )
                } finally {
                    runCatching { connected.store.close() }
                }
            }
        }
    }

    private companion object {
        const val TAG = "AinsmailSync"
        const val DAY_MS = 24L * 60L * 60L * 1000L
    }
}
