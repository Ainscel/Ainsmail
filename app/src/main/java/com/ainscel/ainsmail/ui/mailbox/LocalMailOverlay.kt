package com.ainscel.ainsmail.ui.mailbox

import com.ainscel.ainsmail.model.Mail

/** 这些意图会改已读、归档或本机删除，需要写回本地库。类型和标签先只留在内存。 */
fun MailboxIntent.writesLocalMail(): Boolean = when (this) {
    MailboxIntent.MarkVisibleRead,
    MailboxIntent.DeleteSelected,
    MailboxIntent.ArchiveSelected,
    MailboxIntent.Undo,
    -> true
    is MailboxIntent.DeleteMail -> true
    else -> false
}

fun Mail.sameLocalFlags(other: Mail): Boolean =
    unread == other.unread &&
        archived == other.archived &&
        locallyDeleted == other.locallyDeleted

/**
 * 库行已经带上同样的本机标记时，丢掉挂起项；否则继续用挂起项盖住旧快照。
 * 会改 [pending]。
 */
fun mergePending(pending: MutableMap<String, Mail>, fromDb: List<Mail>): List<Mail> {
    if (pending.isEmpty()) return fromDb
    val ids = fromDb.map { it.id }.toSet()
    pending.keys.retainAll(ids)
    return fromDb.map { mail ->
        val held = pending[mail.id] ?: return@map mail
        if (held.sameLocalFlags(mail)) {
            pending.remove(mail.id)
            mail
        } else {
            held
        }
    }
}
