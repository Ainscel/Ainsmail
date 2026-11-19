package com.ainscel.ainsmail.data.mail

import android.util.Log
import com.ainscel.ainsmail.data.imap.ImapConnectResult
import com.ainscel.ainsmail.data.imap.TextBodyFetch
import com.ainscel.ainsmail.data.imap.fetchTextBody
import com.ainscel.ainsmail.data.imap.openImapStore
import com.ainscel.ainsmail.security.AccountBook
import jakarta.mail.Store
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 前台把未处理的信洗成文件。一次只开一条额外的 IMAP，不占用 IDLE 那条连接。
 * 离开前台只停后续任务，当前这一封写完再断开。
 */
class MailBodyPipeline(
    private val book: AccountBook,
    private val store: MailLocalStore,
    private val io: CoroutineDispatcher,
) {
    private val queueLock = Mutex()
    private val priority = ArrayDeque<String>()
    private val failures = HashMap<String, Failure>()
    private val accountHold = HashMap<String, Long>()
    private val reauthSent = HashSet<String>()
    private val wake = Channel<Unit>(Channel.CONFLATED)

    private var scope: CoroutineScope? = null
    private var job: Job? = null
    private var onNeedsReauth: (String) -> Unit = {}

    @Volatile
    private var paused = true

    private var openAccount: String? = null
    private var imap: Store? = null

    fun attach(scope: CoroutineScope) {
        this.scope = scope
    }

    /** 回到前台时清掉上一轮的退避，重新扫未处理的信。 */
    fun resume(onNeedsReauth: (String) -> Unit) {
        this.onNeedsReauth = onNeedsReauth
        failures.clear()
        accountHold.clear()
        reauthSent.clear()
        paused = false
        if (job?.isActive == true) {
            wake.trySend(Unit)
            return
        }
        val currentScope = scope ?: return
        job = currentScope.launch(io) {
            try {
                loop()
            } finally {
                withContext(NonCancellable) { closeConnection() }
            }
        }
    }

    fun pause() {
        paused = true
        wake.trySend(Unit)
    }

    fun stop() {
        paused = true
        job?.cancel()
        job = null
        wake.trySend(Unit)
    }

    fun prioritize(id: String) {
        val currentScope = scope ?: return
        currentScope.launch {
            queueLock.withLock {
                priority.remove(id)
                priority.addFirst(id)
            }
            wake.trySend(Unit)
        }
    }

    private suspend fun loop() {
        while (currentCoroutineContext().isActive) {
            if (paused) {
                closeConnection()
                wake.receive()
                continue
            }
            val id = nextId()
            if (id == null) {
                withTimeoutOrNull(IDLE_WAIT_MS) { wake.receive() }
                continue
            }
            try {
                process(id)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w(TAG, "wash id=$id ${error.javaClass.simpleName}")
                closeConnection()
                noteFailure(id)
            }
        }
    }

    private suspend fun nextId(): String? {
        val preferred = queueLock.withLock {
            if (priority.isEmpty()) null else priority.removeFirst()
        }
        if (preferred != null) return preferred
        val now = System.currentTimeMillis()
        for (id in store.unprocessedIds()) {
            if (ready(id, now)) return id
        }
        return null
    }

    private fun ready(id: String, now: Long): Boolean {
        val accountId = id.substringBefore(':')
        if ((accountHold[accountId] ?: 0L) > now) return false
        val failure = failures[id] ?: return true
        return failure.nextAt <= now
    }

    private suspend fun process(id: String) {
        val header = store.findHeader(id) ?: return
        if (header.bodyProcessedAt != null) return
        if (MailSyncGate.isDropped(header.accountId)) return
        if (header.uid <= 0L) {
            finish(id, prepareMailBody(html = null, plain = null))
            return
        }
        val cursor = store.cursor(header.accountId, header.remoteFolder)
        if (cursor == null || cursor.folderPath.isBlank()) {
            noteFailure(id)
            return
        }
        if (book.credentialsFor(header.accountId) == null) {
            noteFailure(id)
            return
        }
        val imapStore = when (val link = connection(header.accountId)) {
            Link.Reauth -> return
            Link.Failed -> {
                noteFailure(id)
                return
            }
            is Link.Open -> link.store
        }
        when (val fetched = fetchTextBody(imapStore, cursor.folderPath, header.uid)) {
            TextBodyFetch.NeedsReauth -> {
                closeConnection()
                reportReauth(header.accountId)
            }
            is TextBodyFetch.Failed -> {
                Log.w(TAG, "fetch id=$id ${fetched.reason}")
                closeConnection()
                noteFailure(id)
            }
            TextBodyFetch.Missing -> finish(id, prepareMailBody(html = null, plain = null))
            is TextBodyFetch.Found -> finish(id, prepareMailBody(fetched.html, fetched.plain))
        }
    }

    private fun connection(accountId: String): Link {
        val current = imap
        if (openAccount == accountId && current != null && current.isConnected) return Link.Open(current)
        closeConnection()
        val credentials = book.credentialsFor(accountId) ?: return Link.Failed
        return when (val connected = openImapStore(credentials)) {
            is ImapConnectResult.Ok -> {
                openAccount = accountId
                imap = connected.store
                Link.Open(connected.store)
            }
            ImapConnectResult.NeedsReauth -> {
                reportReauth(accountId)
                Link.Reauth
            }
            is ImapConnectResult.Failed -> {
                Log.w(TAG, "connect account=$accountId ${connected.reason}")
                holdAccount(accountId, CONNECT_HOLD_MS)
                Link.Failed
            }
        }
    }

    private suspend fun finish(id: String, prepared: PreparedBody) {
        val saved = store.saveBody(
            id = id,
            html = prepared.html,
            markdown = prepared.markdown,
            preview = prepared.preview,
            processedAt = System.currentTimeMillis(),
        )
        if (saved) failures.remove(id) else noteFailure(id)
    }

    private fun noteFailure(id: String) {
        val count = (failures[id]?.count ?: 0) + 1
        val delay = if (count >= FAIL_LIMIT) Long.MAX_VALUE / 4 else FAIL_STEP_MS * count
        failures[id] = Failure(count, System.currentTimeMillis() + delay)
    }

    private fun holdAccount(accountId: String, delayMs: Long) {
        accountHold[accountId] = System.currentTimeMillis() + delayMs
    }

    private fun reportReauth(accountId: String) {
        holdAccount(accountId, REAUTH_HOLD_MS)
        if (!reauthSent.add(accountId)) return
        onNeedsReauth(accountId)
    }

    private fun closeConnection() {
        val current = imap
        imap = null
        openAccount = null
        if (current == null) return
        runCatching {
            if (current.isConnected) current.close()
        }
    }

    private sealed interface Link {
        data class Open(val store: Store) : Link
        data object Reauth : Link
        data object Failed : Link
    }

    private data class Failure(val count: Int, val nextAt: Long)

    private companion object {
        const val TAG = "AinsmailBody"
        const val IDLE_WAIT_MS = 15_000L
        const val CONNECT_HOLD_MS = 60_000L
        const val REAUTH_HOLD_MS = 5 * 60_000L
        const val FAIL_STEP_MS = 20_000L
        const val FAIL_LIMIT = 3
    }
}
