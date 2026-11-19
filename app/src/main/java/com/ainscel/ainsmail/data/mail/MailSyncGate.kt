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

    /** abort 在另一条线程上读，不拿这把锁。先写文件夹，再写阶段，读到 Idling 时文件夹已经可见。 */
    @Volatile var folder: IMAPFolder? = null

    @Volatile var phase: SlotPhase = SlotPhase.Free
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
        slot.folder = folder
        slot.phase = SlotPhase.Idling
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
        while (System.nanoTime() < deadline && slot.phase == SlotPhase.Idling && currentCoroutineContext().isActive) {
            if (folder != null && imapIdleWaiting(folder)) {
                runCatching { folder.doCommand { null } }
            }
            if (slot.phase != SlotPhase.Idling) return
            delay(ABORT_POLL_MS)
        }
        while (slot.phase == SlotPhase.Idling && currentCoroutineContext().isActive) {
            runCatching { folder?.doCommand { null } }
            if (slot.phase != SlotPhase.Idling) return
            delay(ABORT_POLL_MS)
        }
    }
}

private const val ABORT_WAIT_NS = 5_000_000_000L
private const val ABORT_POLL_MS = 10L

/**
 * 等到 IDLE 真正开始再打断，并且在阶段变回 [SlotPhase.Free] 之前继续打。
 * 只打一次就返回的话，poke 可能落在 [IMAPFolder.idle] 进入等待之前，连接会一直挂着。
 */
private fun abortBlocking(slot: AccountSlot) {
    val started = System.nanoTime()
    while (slot.phase == SlotPhase.Idling) {
        val folder = slot.folder
        val waiting = folder != null && imapIdleWaiting(folder)
        val overdue = System.nanoTime() - started >= ABORT_WAIT_NS
        if (folder != null && (waiting || overdue)) {
            runCatching { folder.doCommand { null } }
        }
        if (slot.phase != SlotPhase.Idling) return
        try {
            Thread.sleep(ABORT_POLL_MS)
        } catch (interrupted: InterruptedException) {
            return
        }
    }
}

/** 给忘记账号用：先停掉 IDLE，再排到这条账号的锁后面。 */
internal suspend fun AccountSlot.awaitTurn() {
    lock.withLock { }
}
