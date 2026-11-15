package com.ainscel.ainsmail.ui.mailbox

import androidx.compose.runtime.Immutable
import com.ainscel.ainsmail.model.Folder
import com.ainscel.ainsmail.model.MailType
import com.ainscel.ainsmail.model.ReadFilter
import com.ainscel.ainsmail.model.TagPool

@Immutable
data class MailboxUiState(
    val revision: Long,
    val accountId: String?,
    val selectedAccountName: String?,
    val folder: Folder,
    val rows: List<MailRowUi>,
    val readFilter: ReadFilter,
    val selectedTagNames: Set<String>,
    val typeFilter: MailType?,
    val multiSelect: Boolean,
    val selectedCount: Int,
    val tagGroups: List<TagGroupUi>,
    val accounts: List<AccountUi>,
    val notice: NoticeUi?,
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
data class NoticeUi(
    val id: Long,
    val text: String,
    val canUndo: Boolean,
)

@Immutable
data class MailDetailUi(
    val subject: String,
    val fromName: String,
    val metaLine: String,
    val body: String,
)

sealed interface MailboxIntent {
    data class SelectAccount(val id: String?) : MailboxIntent
    data class SelectFolder(val folder: Folder) : MailboxIntent
    data class SetReadFilter(val filter: ReadFilter) : MailboxIntent
    data class ToggleTagFilter(val name: String) : MailboxIntent
    data class SetTypeFilter(val type: MailType?) : MailboxIntent
    data object EnterMultiSelect : MailboxIntent
    data object ExitMultiSelect : MailboxIntent
    data class ToggleSelected(val id: String) : MailboxIntent
    data object MarkVisibleRead : MailboxIntent
    data class DeleteSelected(val message: String) : MailboxIntent
    data class DeleteMail(val id: String, val message: String) : MailboxIntent
    data class ArchiveSelected(val message: String) : MailboxIntent
    data class SetType(val type: MailType) : MailboxIntent
    data class ShowMessage(val text: String) : MailboxIntent
    data class ConsumeNotice(val id: Long) : MailboxIntent
    data object Undo : MailboxIntent
}

enum class AddTagResult {
    Added,
    Blank,
    NeedsType,
    AtLimit,
}
