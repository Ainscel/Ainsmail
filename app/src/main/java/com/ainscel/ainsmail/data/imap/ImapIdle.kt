package com.ainscel.ainsmail.data.imap

import jakarta.mail.Store
import org.eclipse.angus.mail.imap.IMAPFolder
import org.eclipse.angus.mail.imap.IMAPStore

private val idleStateField = runCatching {
    IMAPFolder::class.java.getDeclaredField("idleState").apply { isAccessible = true }
}.getOrNull()

fun storeSupportsIdle(store: Store): Boolean {
    val imap = store as? IMAPStore ?: return false
    return try {
        imap.hasCapability("IDLE")
    } catch (error: Exception) {
        false
    }
}

/**
 * Angus 里 IDLE = 1。还没进入这个状态时，idleAbort 什么都不做。
 * 读不到字段就当成还没进入，调用方会再试一次 abort。
 */
fun imapIdleWaiting(folder: IMAPFolder): Boolean {
    val field = idleStateField ?: return false
    return try {
        field.getInt(folder) == 1
    } catch (error: Exception) {
        false
    }
}
