package com.ainscel.ainsmail.ui.mailbox

data class SnapshotStep<T>(
    val held: T?,
    val apply: T?,
)

/**
 * 暂停时只留最后一份。恢复时把留下的那份交出去，没有新快照就交暂停前的那份。
 */
fun <T> nextSnapshot(paused: Boolean, held: T?, incoming: T?): SnapshotStep<T> {
    if (paused) return SnapshotStep(held = incoming ?: held, apply = null)
    return SnapshotStep(held = null, apply = incoming ?: held)
}
