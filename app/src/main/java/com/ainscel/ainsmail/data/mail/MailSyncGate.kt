package com.ainscel.ainsmail.data.mail

import com.ainscel.ainsmail.data.imap.imapIdleWaiting
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.eclipse.angus.mail.imap.IMAPFolder

internal enum class SlotPhase {
    Free,
    Idling,
}

internal class AccountSlot {
    val lock = Mutex()
    var phase: SlotPhase = SlotPhase.Free
    var folder: IMAPFolder? = null
}

/**
 * 每个账号一把锁。IDLE 阻塞期间不占着锁。
 * 别的同步要进来时，先等 IDLE 真正开始再 abort，避免 abort 丢在进入 IDLE 之前。
 * 进程内只有这一份，后台 Worker 和界面共用。
 */
object MailSyncGate {
    @Volatile
    var foreground: Boolean = false

    private val slots = ConcurrentHashMap<String, AccountSlot>()
    private val dropped = ConcurrentHashMap.newKeySet<String>()

    fun isDropped(accountId: String): Boolean = accountId in dropped

    fun drop(accountId: String) {
        dropped.add(accountId)
        requestAbort(accountId)
    }

    fun undrop(accountId: String) {
        dropped.remove(accountId)
    }

    internal fun slot(accountId: String): AccountSlot = slots.getOrPut(accountId) { AccountSlot() }

    /** 调用方正拿着 [AccountSlot.lock]。 */
    internal fun markIdling(slot: AccountSlot, folder: IMAPFolder) {
        slot.phase = SlotPhase.Idling
        slot.folder = folder
    }

    /** 调用方正拿着 [AccountSlot.lock]。 */
    internal fun markFree(slot: AccountSlot) {
        slot.phase = SlotPhase.Free
        slot.folder = null
    }

    fun requestAbort(accountId: String) {
        val slot = slots[accountId] ?: return
        if (slot.phase != SlotPhase.Idling) return
        Thread {
            abortBlocking(slot)
        }.apply {
            isDaemon = true
            name = "ainsmail-idle-abort"
        }.start()
    }

    suspend fun <T> withAccount(accountId: String, block: suspend () -> T): T {
        val slot = slot(accountId)
        while (currentCoroutineContext().isActive) {
            slot.lock.lock()
            if (slot.phase != SlotPhase.Idling) {
                try {
                    return block()
                } finally {
                    slot.lock.unlock()
                }
            }
            val folder = slot.folder
            slot.lock.unlock()
            abortIdle(slot, folder)
        }
        throw CancellationException()
    }

    private suspend fun abortIdle(slot: AccountSlot, folder: IMAPFolder?) {
        val deadline = System.nanoTime() + ABORT_WAIT_NS
        while (System.nanoTime() < deadline && slot.phase == SlotPhase.Idling) {
            if (folder != null && imapIdleWaiting(folder)) {
                runCatching { folder.doCommand { null } }
                break
            }
            delay(ABORT_POLL_MS)
        }
        if (slot.phase == SlotPhase.Idling) {
            runCatching { folder?.doCommand { null } }
        }
        while (slot.phase == SlotPhase.Idling && currentCoroutineContext().isActive) {
            delay(ABORT_POLL_MS)
        }
    }
}

private const val ABORT_WAIT_NS = 5_000_000_000L
private const val ABORT_POLL_MS = 10L

private fun abortBlocking(slot: AccountSlot) {
    val deadline = System.nanoTime() + ABORT_WAIT_NS
    while (System.nanoTime() < deadline && slot.phase == SlotPhase.Idling) {
        val folder = slot.folder
        if (folder != null && imapIdleWaiting(folder)) {
            runCatching { folder.doCommand { null } }
            return
        }
        Thread.sleep(ABORT_POLL_MS)
    }
    runCatching { slot.folder?.doCommand { null } }
}

/** 给忘记账号用：先停掉 IDLE，再排到这条账号的锁后面。 */
internal suspend fun AccountSlot.awaitTurn() {
    lock.withLock { }
}
