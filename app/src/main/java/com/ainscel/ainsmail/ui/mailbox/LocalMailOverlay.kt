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

/** 已写库、库快照还没跟上的三个本机标记。不含信封，也不含类型和标签。 */
data class LocalFlags(
    val unread: Boolean,
    val archived: Boolean,
    val locallyDeleted: Boolean,
)

fun Mail.localFlags(): LocalFlags = LocalFlags(unread, archived, locallyDeleted)

private fun LocalFlags.matches(mail: Mail): Boolean =
    unread == mail.unread && archived == mail.archived && locallyDeleted == mail.locallyDeleted

/** 类型、标签、预览、正文这一阶段只活在会话里。标记仍以库行和挂起项为准。 */
private fun Mail.withSessionFields(from: Mail): Mail {
    if (type == from.type && tagNames == from.tagNames && preview == from.preview && body == from.body) {
        return this
    }
    return copy(type = from.type, tagNames = from.tagNames, preview = from.preview, body = from.body)
}

/**
 * 以库行为底。挂起标记还没跟上时只盖已读、归档和本机删除。
 * 上一份会话里有的类型、标签、预览、正文抄过来；没有这封信就用库行自己的值。
 * 会改 [pending]。没有要盖、也没有要抄的字段时，返回 [fromDb] 原实例。
 */
fun mergeSnapshot(
    pending: MutableMap<String, LocalFlags>,
    fromDb: List<Mail>,
    previous: List<Mail>,
): List<Mail> {
    val dbIds = HashSet<String>(fromDb.size)
    for (mail in fromDb) dbIds.add(mail.id)
    pending.keys.retainAll(dbIds)
    if (pending.isEmpty() && previous.isEmpty()) return fromDb

    val previousById = HashMap<String, Mail>(previous.size)
    for (mail in previous) previousById.putIfAbsent(mail.id, mail)

    var unchanged = true
    val merged = ArrayList<Mail>(fromDb.size)
    for (mail in fromDb) {
        val prior = previousById[mail.id]
        val base = if (prior == null) mail else mail.withSessionFields(prior)
        if (base !== mail) unchanged = false
        val held = pending[mail.id]
        if (held == null) {
            merged.add(base)
        } else if (held.matches(mail)) {
            pending.remove(mail.id)
            merged.add(base)
        } else {
            unchanged = false
            merged.add(
                base.copy(
                    unread = held.unread,
                    archived = held.archived,
                    locallyDeleted = held.locallyDeleted,
                ),
            )
        }
    }
    return if (unchanged) fromDb else merged
}
