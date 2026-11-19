package com.ainscel.ainsmail.ui.mailbox

import com.ainscel.ainsmail.model.Account
import com.ainscel.ainsmail.model.AccountBadgeStyle
import com.ainscel.ainsmail.model.Mail
import com.ainscel.ainsmail.model.MailType
import com.ainscel.ainsmail.model.MailboxScope
import com.ainscel.ainsmail.model.Section
import com.ainscel.ainsmail.model.WeekDateStyle
import com.ainscel.ainsmail.model.inScope
import com.ainscel.ainsmail.model.inSection
import com.ainscel.ainsmail.model.TagPool
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class MailboxProjection(
    val chrome: MailboxChrome,
    val list: MailboxList,
)

/** 条目内容没变时沿用上一份。勾选不在里面。 */
class ListReuse(
    val entries: List<MailListEntry>,
    val sectionMailIds: Set<String>,
    val tagGroups: List<TagGroupUi>,
)

/**
 * 同一分钟里，信件、目录、账号、范围、板块和筛选都没变，列表条目就可以复用。
 * 行上的时间文字和时间分隔都只跟「现在」是否还在同一分钟有关，勾选不算。
 */
fun rowsReusable(
    previous: MailboxSession,
    next: MailboxSession,
    previousMinute: Long,
    now: Long,
): Boolean {
    return previousMinute == now / 60_000L &&
        previous.mails === next.mails &&
        previous.catalog == next.catalog &&
        previous.accounts == next.accounts &&
        previous.scope == next.scope &&
        previous.section == next.section &&
        previous.readFilter == next.readFilter &&
        previous.selectedTags == next.selectedTags &&
        previous.typeFilter == next.typeFilter
}

/**
 * [previousAccounts] 的 id 和名字都没变时沿用那一份。
 * 根界面拿着同一个列表实例，账号没变就不会因为列表刷新去重组头像列。
 */
fun project(
    session: MailboxSession,
    now: Long,
    previousAccounts: List<AccountUi> = emptyList(),
    reuse: ListReuse? = null,
    badgeStyle: AccountBadgeStyle = AccountBadgeStyle.EveryRow,
    weekDate: WeekDateStyle = WeekDateStyle.Date,
    zone: ZoneId = ZoneId.systemDefault(),
): MailboxProjection {
    val sectionMailIds: Set<String>
    val entries: List<MailListEntry>
    val groups: List<TagGroupUi>
    if (reuse != null) {
        sectionMailIds = reuse.sectionMailIds
        entries = reuse.entries
        groups = reuse.tagGroups
    } else {
        val built = scanRows(session, now, badgeStyle, weekDate, zone)
        sectionMailIds = built.sectionMailIds
        entries = built.entries
        groups = tagGroups(session)
    }
    return MailboxProjection(
        chrome = MailboxChrome(
            scope = session.scope,
            selectedAccountName = selectedAccountName(session),
            section = session.section,
            sectionMailIds = sectionMailIds,
            accounts = accountUi(session, previousAccounts),
            notice = session.notice,
        ),
        list = MailboxList(
            readFilter = session.readFilter,
            selectedTagNames = session.selectedTags,
            typeFilter = session.typeFilter,
            multiSelect = session.multiSelect,
            selectedIds = session.selectedIds,
            tagGroups = groups,
            entries = entries,
        ),
    )
}

private class ScannedRows(
    val sectionMailIds: Set<String>,
    val entries: List<MailListEntry>,
)

/** 全部邮件只扫一遍：板块 id、时间分隔和可见行一起收齐。 */
private fun scanRows(
    session: MailboxSession,
    now: Long,
    badgeStyle: AccountBadgeStyle,
    weekDate: WeekDateStyle,
    zone: ZoneId,
): ScannedRows {
    val showAccount = session.scope is MailboxScope.All
    val sectionMailIds = HashSet<String>()
    val visible = ArrayList<Mail>()
    for (mail in session.mails) {
        if (!mail.inScope(session.scope) || !mail.inSection(session.section)) continue
        sectionMailIds.add(mail.id)
        if (session.matchesFilters(mail)) visible.add(mail)
    }
    if (visible.size > 1) visible.sortByDescending { it.timeMillis }
    val accountsById = HashMap<String, Account>(session.accounts.size)
    for (account in session.accounts) accountsById.putIfAbsent(account.id, account)
    val entries = ArrayList<MailListEntry>(visible.size * 2)
    var previousBucket: TimeBucket? = null
    var previousAccountId: String? = null
    var previousFromName: String? = null
    for (mail in visible) {
        val bucket = timeBucket(mail.timeMillis, now, zone)
        if (bucket != previousBucket) {
            entries.add(MailListEntry.TimeDivider(bucket))
            previousBucket = bucket
        }
        val showBadge = showAccount && when (badgeStyle) {
            AccountBadgeStyle.EveryRow -> true
            AccountBadgeStyle.FirstOfRun -> mail.accountId != previousAccountId
            AccountBadgeStyle.SameSender ->
                mail.accountId != previousAccountId || mail.fromName != previousFromName
        }
        entries.add(MailListEntry.Row(toRow(session, mail, now, zone, weekDate, showBadge, accountsById)))
        previousAccountId = mail.accountId
        previousFromName = mail.fromName
    }
    return ScannedRows(sectionMailIds, entries)
}

/** 按导航给出的 id 取正文展示。打开哪封信不写进会话，这里也不读列表行。 */
fun MailboxSession.detailOf(
    id: String,
    now: Long,
    weekDate: WeekDateStyle = WeekDateStyle.Date,
): MailDetailUi? {
    val mail = mails.find { it.id == id } ?: return null
    val accountName = accounts.find { it.id == mail.accountId }?.name.orEmpty()
    return MailDetailUi(
        subject = mail.subject,
        fromName = mail.fromName,
        metaLine = "$accountName · ${formatMailTime(mail.timeMillis, now, weekDate = weekDate)}",
        body = mail.body,
    )
}

fun MailboxSession.subjectOf(id: String): String? = mails.find { it.id == id }?.subject

private fun accountUi(session: MailboxSession, previous: List<AccountUi>): List<AccountUi> {
    val accounts = session.accounts
    val unchanged = previous.size == accounts.size && previous.indices.all { index ->
        val account = accounts[index]
        val ui = previous[index]
        ui.id == account.id && ui.name == account.name && ui.avatarFile == account.avatarFile
    }
    if (unchanged) return previous
    return accounts.map { account ->
        AccountUi(
            id = account.id,
            name = account.name,
            label = avatarLabel(account.name),
            hue = avatarHue(account.name),
            avatarFile = account.avatarFile,
        )
    }
}

private fun selectedAccountName(session: MailboxSession): String? = when (val scope = session.scope) {
    MailboxScope.All -> null
    is MailboxScope.Account -> session.accounts.find { it.id == scope.id }?.name
}

private fun toRow(
    session: MailboxSession,
    mail: Mail,
    now: Long,
    zone: ZoneId,
    weekDate: WeekDateStyle,
    showAccountBadge: Boolean,
    accountsById: Map<String, Account>,
): MailRowUi {
    val account = accountsById[mail.accountId]
    return MailRowUi(
        id = mail.id,
        fromName = mail.fromName,
        subject = mail.subject,
        preview = mail.preview,
        timeLabel = formatMailTime(mail.timeMillis, now, zone, weekDate),
        unread = mail.unread,
        accountName = if (showAccountBadge) account?.name else null,
        senderLabel = avatarLabel(mail.fromName),
        senderHue = avatarHue(mail.fromName),
        accountLabel = account?.let { avatarLabel(it.name) }.orEmpty(),
        accountHue = account?.let { avatarHue(it.name) } ?: 0f,
        accountAvatar = if (showAccountBadge) account?.avatarFile else null,
        tags = mail.tagNames.asReversed().map { name ->
            TagChipUi(name, session.catalog.poolOf(name) ?: TagPool.Pending)
        },
        showClassifying = session.section == Section.Inbox &&
            session.typeFilter == null &&
            mail.type == null,
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

private val WeekdayLabels = arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

/** 把绝对时刻格式化成设备时区里的本地时间。今天只显示时分，日期从昨天开始。 */
fun formatMailTime(
    epochMillis: Long,
    nowMillis: Long,
    zone: ZoneId = ZoneId.systemDefault(),
    weekDate: WeekDateStyle = WeekDateStyle.Date,
): String {
    val moment = Instant.ofEpochMilli(epochMillis).atZone(zone)
    val mailDate = moment.toLocalDate()
    val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    if (mailDate == today) return "%02d:%02d".format(moment.hour, moment.minute)
    if (weekDate == WeekDateStyle.Relative) {
        relativeWeekLabel(mailDate, today)?.let { return it }
    }
    return "${moment.monthValue}月${moment.dayOfMonth}日"
}

/**
 * 昨天，以及同一自然周里更早或更晚的周几。今天不进这里。
 * 昨天即使落在上一周也用「昨天」。更早又跨周的返回空，改显示月日。
 */
private fun relativeWeekLabel(mailDate: LocalDate, today: LocalDate): String? {
    if (mailDate == today.minusDays(1)) return "昨天"
    if (!sameCalendarWeek(mailDate, today)) return null
    return WeekdayLabels[mailDate.dayOfWeek.value - 1]
}
