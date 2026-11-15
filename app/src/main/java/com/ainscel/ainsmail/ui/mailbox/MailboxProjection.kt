package com.ainscel.ainsmail.ui.mailbox

import com.ainscel.ainsmail.model.Mail
import com.ainscel.ainsmail.model.MailType
import com.ainscel.ainsmail.model.MailboxScope
import com.ainscel.ainsmail.model.Section
import com.ainscel.ainsmail.model.TagPool
import java.time.Instant
import java.time.ZoneId

fun project(session: MailboxSession, now: Long): MailboxUiState {
    val showAccount = session.scope is MailboxScope.All
    val sectionMails = session.mailsInSection()
    return MailboxUiState(
        scope = session.scope,
        selectedAccountName = selectedAccountName(session),
        section = session.section,
        sectionMailIds = sectionMails.map { it.id }.toSet(),
        rows = session.visibleMails()
            .sortedByDescending { it.timeMillis }
            .map { toRow(session, it, now, showAccount) },
        readFilter = session.readFilter,
        selectedTagNames = session.selectedTags,
        typeFilter = session.typeFilter,
        multiSelect = session.multiSelect,
        selectedCount = session.selectedIds.size,
        tagGroups = tagGroups(session),
        accounts = session.accounts.map { account ->
            AccountUi(
                id = account.id,
                name = account.name,
                label = avatarLabel(account.name),
                hue = avatarHue(account.name),
            )
        },
        notice = session.notice,
    )
}

/** 正文查找。调用方要先读过界面状态，这里不再碰快照。 */
fun MailboxSession.detailOf(id: String, now: Long): MailDetailUi? {
    val mail = mails.find { it.id == id } ?: return null
    val accountName = accounts.find { it.id == mail.accountId }?.name.orEmpty()
    return MailDetailUi(
        subject = mail.subject,
        fromName = mail.fromName,
        metaLine = "$accountName · ${formatMailTime(mail.timeMillis, now)}",
        body = mail.body,
    )
}

fun MailboxSession.subjectOf(id: String): String? = mails.find { it.id == id }?.subject

private fun selectedAccountName(session: MailboxSession): String? = when (val scope = session.scope) {
    MailboxScope.All -> null
    is MailboxScope.Account -> session.accounts.find { it.id == scope.id }?.name
}

private fun toRow(session: MailboxSession, mail: Mail, now: Long, showAccount: Boolean): MailRowUi {
    val account = session.accounts.find { it.id == mail.accountId }
    return MailRowUi(
        id = mail.id,
        fromName = mail.fromName,
        subject = mail.subject,
        preview = mail.preview,
        timeLabel = formatMailTime(mail.timeMillis, now),
        unread = mail.unread,
        accountName = if (showAccount) account?.name else null,
        senderLabel = avatarLabel(mail.fromName),
        senderHue = avatarHue(mail.fromName),
        accountLabel = account?.let { avatarLabel(it.name) }.orEmpty(),
        accountHue = account?.let { avatarHue(it.name) } ?: 0f,
        tags = mail.tagNames.asReversed().map { name ->
            TagChipUi(name, session.catalog.poolOf(name) ?: TagPool.Pending)
        },
        showClassifying = session.section == Section.Inbox &&
            session.typeFilter == null &&
            mail.type == null,
        checked = mail.id in session.selectedIds,
    )
}

private fun tagGroups(session: MailboxSession): List<TagGroupUi> {
    val grouped = linkedMapOf<Pair<TagPool, MailType>, MutableList<String>>()
    session.catalog.words.forEach { word ->
        grouped.getOrPut(word.pool to word.type) { mutableListOf() }.add(word.name)
    }
    return grouped.map { (key, names) ->
        TagGroupUi(pool = key.first, type = key.second, names = names.toList())
    }.sortedWith(compareBy({ it.pool.ordinal }, { it.type.ordinal }))
}

/** 名称哈希映射到稳重的色相，避免每一行都是荧光色。 */
fun avatarHue(name: String): Float = ((name.hashCode() and 0x7fffffff) % 360).toFloat()

/** 中文取前两个字，拉丁字母取前两个字母。空白不算。 */
fun avatarLabel(name: String): String = name.filter { !it.isWhitespace() }.take(2)

fun formatMailTime(epochMillis: Long, nowMillis: Long): String {
    val zone = ZoneId.systemDefault()
    val moment = Instant.ofEpochMilli(epochMillis).atZone(zone)
    val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    return if (moment.toLocalDate() == today) {
        "%02d:%02d".format(moment.hour, moment.minute)
    } else {
        "${moment.monthValue}月${moment.dayOfMonth}日"
    }
}
