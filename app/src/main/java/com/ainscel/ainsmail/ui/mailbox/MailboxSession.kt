package com.ainscel.ainsmail.ui.mailbox

import com.ainscel.ainsmail.model.Account
import com.ainscel.ainsmail.model.Mail
import com.ainscel.ainsmail.model.MailType
import com.ainscel.ainsmail.model.MailboxScope
import com.ainscel.ainsmail.model.ReadFilter
import com.ainscel.ainsmail.model.RemoteFolder
import com.ainscel.ainsmail.model.Section
import com.ainscel.ainsmail.model.TagCatalog

/** 一封信被挪走之前的位置。撤销只恢复这些，不把整张表换回去。 */
data class MailPlacement(
    val remoteFolder: RemoteFolder,
    val archived: Boolean,
)

data class UndoRecord(
    val placements: Map<String, MailPlacement>,
)

sealed interface Notice {
    val id: Long

    data class Deleted(override val id: Long) : Notice
    data class Archived(override val id: Long) : Notice
    data class TagLimit(override val id: Long) : Notice
    data class TagNeedsType(override val id: Long) : Notice
    data class TagMixedTypes(override val id: Long) : Notice
    data class NeedsReauth(override val id: Long, val accountId: String) : Notice
    data class SyncFailed(override val id: Long) : Notice

    val canUndo: Boolean
        get() = this is Deleted || this is Archived
}

data class MailboxSession(
    val accounts: List<Account>,
    val mails: List<Mail>,
    val catalog: TagCatalog,
    val scope: MailboxScope = MailboxScope.All,
    val section: Section = Section.Inbox,
    val readFilter: ReadFilter = ReadFilter.Any,
    val selectedTags: Set<String> = emptySet(),
    val typeFilter: MailType? = null,
    val multiSelect: Boolean = false,
    val selectedIds: Set<String> = emptySet(),
    val notice: Notice? = null,
    val undo: UndoRecord? = null,
    /** 访问令牌已过期、需要在设置里重新登录的账号。 */
    val reauthAccountIds: Set<String> = emptySet(),
)

data class ReduceOutcome(
    val session: MailboxSession,
    val tagResult: AddTagResult? = null,
)
