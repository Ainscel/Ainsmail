package com.ainscel.ainsmail.ui.mailbox

import com.ainscel.ainsmail.model.Mail
import com.ainscel.ainsmail.model.MailType
import com.ainscel.ainsmail.model.MailboxScope
import com.ainscel.ainsmail.model.Section
import com.ainscel.ainsmail.model.TagPool
import java.time.Instant
import java.time.ZoneId

data class MailboxProjection(
    val chrome: MailboxChrome,
    val list: MailboxList,
)

/**
 * [previousAccounts] 的 id 和名字都没变时沿用那一份。
 * 根界面拿着同一个列表实例，账号没变就不会因为列表刷新去重组头像列。
 */
fun project(
    session: MailboxSession,
    now: Long,
    previousAccounts: List<AccountUi> = emptyList(),
): MailboxProjection {
    val showAccount = session.scope is MailboxScope.All
    val sectionMails = session.mailsInSection()
    return MailboxProjection(
        chrome = MailboxChrome(
            scope = session.scope,
            selectedAccountName = selectedAccountName(session),
            section = session.section,
            sectionMailIds = sectionMails.map { it.id }.toSet(),
            accounts = accountUi(session, previousAccounts),
            notice = session.notice,
        ),
        list = MailboxList(
            readFilter = session.readFilter,
            selectedTagNames = session.selectedTags,
            typeFilter = session.typeFilter,
            multiSelect = session.multiSelect,
            selectedCount = session.selectedIds.size,
            tagGroups = tagGroups(session),
            rows = session.visibleMails()
                .sortedByDescending { it.timeMillis }
                .map { toRow(session, it, now, showAccount) },
        ),
    )
}

/** 按导航给出的 id 取正文展示。打开哪封信不写进会话，这里也不读列表行。 */
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

private fun accountUi(session: MailboxSession, previous: List<AccountUi>): List<AccountUi> {
    val accounts = session.accounts
    val unchanged = previous.size == accounts.size && previous.indices.all { index ->
        val account = accounts[index]
        val ui = previous[index]
        ui.id == account.id && ui.name == account.name
    }
    if (unchanged) return previous
    return accounts.map { account ->
        AccountUi(
            id = account.id,
            name = account.name,
            label = avatarLabel(account.name),
            hue = avatarHue(account.name),
        )
    }
}

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
