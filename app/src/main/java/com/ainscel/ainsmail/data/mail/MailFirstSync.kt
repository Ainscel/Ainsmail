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

private data class WindowSync(
    val outcome: FirstSyncOutcome,
    val budgetExhausted: Boolean,
)

private enum class SyncWindow {
    Recent,
    History,
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

    /** 与 [syncAll] 同一把锁。进行中的首拉先放开锁，再删，避免旧连接把信写回来。 */
    suspend fun forgetAccounts(accountIds: List<String>) {
        if (accountIds.isEmpty()) return
        mutex.withLock {
            accountIds.forEach { MailSyncGate.drop(it) }
            try {
                for (id in accountIds) MailSyncGate.slot(id).awaitTurn()
                store.deleteAccounts(accountIds)
            } finally {
                accountIds.forEach { MailSyncGate.undrop(it) }
            }
        }
    }

    suspend fun syncAll(accountIds: List<String>): List<FirstSyncOutcome> = mutex.withLock {
        withContext(io) {
            val outcomes = ArrayList<FirstSyncOutcome>()
            val now = System.currentTimeMillis()
            val recentStart = now - FIRST_SYNC_RECENT_DAYS * DAY_MS
            val historyStart = now - FIRST_SYNC_HISTORY_DAYS * DAY_MS
            val recentOk = ArrayList<String>()

            for (accountId in accountIds) {
                val recent = syncWindow(
                    accountId = accountId,
                    sinceEpochMs = recentStart,
                    untilEpochMs = null,
                    budget = null,
                    window = SyncWindow.Recent,
                )
                outcomes += recent.outcome
                if (recent.outcome is FirstSyncOutcome.Ok) recentOk += accountId
            }

            if (!shouldFillHistory(store.countAll())) {
                store.markHistoryDone(recentOk)
                return@withContext outcomes
            }

            val capped = ArrayList<String>()
            for (accountId in recentOk) {
                val budget = historyFillBudget(store.countAll())
                if (budget <= 0) {
                    capped += accountId
                    continue
                }
                val history = syncWindow(
                    accountId = accountId,
                    sinceEpochMs = historyStart,
                    untilEpochMs = recentStart - 1,
                    budget = budget,
                    window = SyncWindow.History,
                )
                outcomes += history.outcome
                if (history.outcome is FirstSyncOutcome.Ok && history.budgetExhausted) {
                    capped += accountId
                }
            }
            store.markHistoryDone(capped)
            outcomes
        }
    }

    private suspend fun syncWindow(
        accountId: String,
        sinceEpochMs: Long,
        untilEpochMs: Long?,
        budget: Int?,
        window: SyncWindow,
    ): WindowSync {
        val credentials = book.credentialsFor(accountId)
            ?: return WindowSync(FirstSyncOutcome.Failed(accountId, "missing_credentials"), false)
        var exhausted = false
        val outcome = MailSyncGate.withAccount(accountId) {
            if (MailSyncGate.isDropped(accountId)) {
                return@withAccount FirstSyncOutcome.Ok
            }
            withStore(accountId, credentials) { imap ->
            val folders = resolveRemoteFolders(imap)
            var remaining = budget
            for ((folder, path) in folders) {
                if (MailSyncGate.isDropped(accountId)) return@withStore FirstSyncOutcome.Ok
                if (remaining != null && remaining <= 0) {
                    exhausted = true
                    break
                }
                val cursor = store.cursor(accountId, folder.name)
                if (cursor.isDone(window)) continue
                val fetched = fetchHeadersSince(
                    store = imap,
                    folderPath = path,
                    sinceEpochMs = sinceEpochMs,
                    untilEpochMs = untilEpochMs,
                    limit = remaining,
                )
                val (recentDone, historyDone) = cursor.doneFlags(window)
                saveFetched(
                    accountId = accountId,
                    folder = folder,
                    path = path,
                    fetched = fetched,
                    cursor = cursor,
                    recentDone = recentDone,
                    historyDone = historyDone,
                )
                if (remaining != null) remaining -= fetched.headers.size
            }
            FirstSyncOutcome.Ok
            }
        }
        if (outcome !is FirstSyncOutcome.Ok) exhausted = false
        return WindowSync(outcome, exhausted)
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
                    closeStore(accountId, connected.store)
                }
            }
        }
    }

    private fun closeStore(accountId: String, store: Store) {
        try {
            store.close()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.w(TAG, "imap_close account=$accountId ${error.javaClass.simpleName}: ${error.message}")
        }
    }

    private companion object {
        const val TAG = "AinsmailSync"
        const val DAY_MS = 24L * 60L * 60L * 1000L
    }
}

/** 当前这窗已经拉完就跳过。最近一窗不改历史完成位，历史一窗不改最近完成位。 */
private fun FolderSyncCursorEntity?.isDone(window: SyncWindow): Boolean = when (window) {
    SyncWindow.Recent -> this?.recentSyncDone == true
    SyncWindow.History -> this?.historySyncDone == true
}

private fun FolderSyncCursorEntity?.doneFlags(window: SyncWindow): Pair<Boolean, Boolean> = when (window) {
    SyncWindow.Recent -> true to (this?.historySyncDone == true)
    SyncWindow.History -> (this?.recentSyncDone == true) to true
}
