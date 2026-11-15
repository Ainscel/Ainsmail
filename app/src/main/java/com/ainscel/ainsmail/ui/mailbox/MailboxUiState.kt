package com.ainscel.ainsmail.ui.mailbox

import androidx.compose.runtime.Immutable
import com.ainscel.ainsmail.model.MailType
import com.ainscel.ainsmail.model.MailboxScope
import com.ainscel.ainsmail.model.ReadFilter
import com.ainscel.ainsmail.model.Section
import com.ainscel.ainsmail.model.TagPool

@Immutable
data class MailboxUiState(
    val scope: MailboxScope,
    val selectedAccountName: String?,
    val section: Section,
    /** 当前账号范围和板块里的信。筛选不放进来，打开的正文不会因为筛选被关掉。 */
    val sectionMailIds: Set<String>,
    val rows: List<MailRowUi>,
    val readFilter: ReadFilter,
    val selectedTagNames: Set<String>,
    val typeFilter: MailType?,
    val multiSelect: Boolean,
    val selectedCount: Int,
    val tagGroups: List<TagGroupUi>,
    val accounts: List<AccountUi>,
    val notice: Notice?,
)

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
    val tags: List<TagChipUi>,
    val showClassifying: Boolean,
    val checked: Boolean,
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
    data object ShowLoginPending : MailboxIntent
    data object ShowGroupPending : MailboxIntent
    data object ShowOriginalPending : MailboxIntent
    data object ShowEditPending : MailboxIntent
    data class ConsumeNotice(val id: Long) : MailboxIntent
    data object Undo : MailboxIntent
}

enum class AddTagResult {
    Added,
    Blank,
    NeedsType,
    AtLimit,
    MixedTypes,
}
