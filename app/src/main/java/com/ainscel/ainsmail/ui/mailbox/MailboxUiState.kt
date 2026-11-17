package com.ainscel.ainsmail.ui.mailbox

import androidx.compose.runtime.Immutable
import com.ainscel.ainsmail.model.Account
import com.ainscel.ainsmail.model.Mail
import com.ainscel.ainsmail.model.MailType
import com.ainscel.ainsmail.model.MailboxScope
import com.ainscel.ainsmail.model.ReadFilter
import com.ainscel.ainsmail.model.Section
import com.ainscel.ainsmail.model.TagPool

/**
 * 根界面和侧栏要的那一份：当前范围、板块、账号和提示。
 * 列表不读这份。列表另订 [MailboxPlace]，提示和账号变化不会重组列表。
 */
@Immutable
data class MailboxChrome(
    val scope: MailboxScope,
    val selectedAccountName: String?,
    val section: Section,
    /** 当前账号范围和板块里的信。筛选不放进来，打开的正文不会因为筛选被关掉。 */
    val sectionMailIds: Set<String>,
    val accounts: List<AccountUi>,
    val notice: Notice?,
)

/**
 * 列表页订阅的范围和板块。
 * 和 [MailboxChrome] 分开，这样提示、账号、板块内的信 id 变化不会重启列表。
 */
@Immutable
data class MailboxPlace(
    val scope: MailboxScope,
    val section: Section,
)

/** 只有列表页订阅。筛选和行放在一起，根界面不读。勾选不写进行里。 */
@Immutable
data class MailboxList(
    val readFilter: ReadFilter,
    val selectedTagNames: Set<String>,
    val typeFilter: MailType?,
    val multiSelect: Boolean,
    val selectedIds: Set<String>,
    val tagGroups: List<TagGroupUi>,
    val rows: List<MailRowUi>,
) {
    val selectedCount: Int get() = selectedIds.size
}

@Immutable
data class MailRowUi(
    val id: String,
    val fromName: String,
    val subject: String,
    val preview: String,
    val timeLabel: String,
    val unread: Boolean,
    val accountName: String?,
    val senderLabel: String,
    val senderHue: Float,
    val accountLabel: String,
    val accountHue: Float,
    /** 全部账号时，角标用的头像文件。没有就画缩小的账号字。 */
    val accountAvatar: String? = null,
    val tags: List<TagChipUi>,
    val showClassifying: Boolean,
)

@Immutable
data class TagChipUi(
    val name: String,
    val pool: TagPool,
)

@Immutable
data class TagGroupUi(
    val pool: TagPool,
    val type: MailType,
    val names: List<String>,
)

@Immutable
data class AccountUi(
    val id: String,
    val name: String,
    val label: String,
    val hue: Float,
    val avatarFile: String? = null,
)

@Immutable
data class MailDetailUi(
    val subject: String,
    val fromName: String,
    val metaLine: String,
    val body: String,
)

sealed interface MailboxIntent {
    data class SelectScope(val scope: MailboxScope) : MailboxIntent
    data class SelectSection(val section: Section) : MailboxIntent
    data class SetReadFilter(val filter: ReadFilter) : MailboxIntent
    data class ToggleTagFilter(val name: String) : MailboxIntent
    data class SetTypeFilter(val type: MailType?) : MailboxIntent
    data object EnterMultiSelect : MailboxIntent
    data object ExitMultiSelect : MailboxIntent
    data class ToggleSelected(val id: String) : MailboxIntent
    data object MarkVisibleRead : MailboxIntent
    data object DeleteSelected : MailboxIntent
    data class DeleteMail(val id: String) : MailboxIntent
    data object ArchiveSelected : MailboxIntent
    data class SetType(val type: MailType) : MailboxIntent
    data class AddTag(val name: String) : MailboxIntent
    data class ConsumeNotice(val id: Long) : MailboxIntent
    data object Undo : MailboxIntent
    data class UseRealAccounts(val accounts: List<Account>) : MailboxIntent
    data class ReplaceAccounts(val accounts: List<Account>) : MailboxIntent
    data class RestoreSamples(val accounts: List<Account>, val mails: List<Mail>) : MailboxIntent
    /** 本地库 Flow 推送的邮件头；不改筛选与多选。 */
    data class ReplaceMails(val mails: List<Mail>) : MailboxIntent
    data class ReportNeedsReauth(val accountId: String) : MailboxIntent
    data class ClearReauth(val accountId: String) : MailboxIntent
    data object ReportSyncFailed : MailboxIntent
}

enum class AddTagResult {
    Added,
    Blank,
    NeedsType,
    AtLimit,
    MixedTypes,
}
