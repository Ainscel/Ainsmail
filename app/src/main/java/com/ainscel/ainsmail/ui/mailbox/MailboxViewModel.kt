package com.ainscel.ainsmail.ui.mailbox

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.ainscel.ainsmail.data.SampleAccounts
import com.ainscel.ainsmail.data.sampleMails
import com.ainscel.ainsmail.model.Account
import com.ainscel.ainsmail.model.Folder
import com.ainscel.ainsmail.model.Mail
import com.ainscel.ainsmail.model.MailTag
import com.ainscel.ainsmail.model.MailType
import com.ainscel.ainsmail.model.ReadFilter
import com.ainscel.ainsmail.model.TagPool
import com.ainscel.ainsmail.ui.list.avatarHue
import com.ainscel.ainsmail.ui.list.avatarLabel
import com.ainscel.ainsmail.ui.list.formatMailTime

/** 骨架阶段的邮箱。邮件在内存里，旋转屏幕还在，进程被杀掉就回到假数据。 */
class MailboxViewModel : ViewModel() {
    private val accountsById: Map<String, Account> = SampleAccounts.associateBy { it.id }
    private val accountUis: List<AccountUi> = SampleAccounts.map { account ->
        AccountUi(
            id = account.id,
            name = account.name,
            label = avatarLabel(account.name),
            hue = avatarHue(account.name),
        )
    }
    private val mails = sampleMails(System.currentTimeMillis()).toMutableList()
    private val selectedIds = linkedSetOf<String>()
    private val selectedTags = linkedSetOf<String>()
    private var undoSnapshot: List<Mail>? = null
    private var accountId: String? = null
    private var folder: Folder = Folder.Inbox
    private var readFilter: ReadFilter = ReadFilter.Any
    private var typeFilter: MailType? = null
    private var multiSelect: Boolean = false
    private var notice: NoticeUi? = null
    private var revision: Long = 0

    var state by mutableStateOf(project())
        private set

    fun onIntent(intent: MailboxIntent) {
        when (intent) {
            is MailboxIntent.SelectAccount -> accountId = intent.id
            is MailboxIntent.SelectFolder -> folder = intent.folder
            is MailboxIntent.SetReadFilter -> readFilter = intent.filter
            is MailboxIntent.ToggleTagFilter -> {
                if (!selectedTags.add(intent.name)) selectedTags.remove(intent.name)
            }
            is MailboxIntent.SetTypeFilter -> typeFilter = intent.type
            MailboxIntent.EnterMultiSelect -> {
                multiSelect = true
                selectedIds.clear()
            }
            MailboxIntent.ExitMultiSelect -> clearSelection()
            is MailboxIntent.ToggleSelected -> {
                if (!selectedIds.add(intent.id)) selectedIds.remove(intent.id)
            }
            MailboxIntent.MarkVisibleRead -> {
                visibleMails().filter { it.unread }.forEach { mail ->
                    replace(mail.id) { it.copy(unread = false) }
                }
                revision++
            }
            is MailboxIntent.DeleteSelected -> mutateFolders(selectedIds.toSet(), Folder.Trash, intent.message)
            is MailboxIntent.DeleteMail -> mutateFolders(setOf(intent.id), Folder.Trash, intent.message)
            is MailboxIntent.ArchiveSelected -> mutateFolders(selectedIds.toSet(), Folder.Archive, intent.message)
            is MailboxIntent.SetType -> {
                selectedIds.forEach { id -> replace(id) { it.copy(type = intent.type) } }
                clearSelection()
                revision++
            }
            is MailboxIntent.ShowMessage -> {
                notice = NoticeUi(id = System.nanoTime(), text = intent.text, canUndo = false)
            }
            is MailboxIntent.ConsumeNotice -> {
                if (notice?.id == intent.id) notice = null
            }
            MailboxIntent.Undo -> restoreUndo()
        }
        if (intent !is MailboxIntent.DeleteSelected &&
            intent !is MailboxIntent.DeleteMail &&
            intent !is MailboxIntent.ArchiveSelected
        ) {
            publish()
        }
    }

    /** 还没分类的信不添加。已经有三个不同标签的信，这一批都不加。新词进这封信自己类型的待定池。 */
    fun addTag(name: String): AddTagResult {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return AddTagResult.Blank
        val targets = selectedIds.mapNotNull { id -> mails.find { it.id == id } }
        if (targets.any { it.type == null }) return AddTagResult.NeedsType
        val overLimit = targets.any { mail ->
            mail.tags.size >= 3 && mail.tags.none { it.name == trimmed }
        }
        if (overLimit) return AddTagResult.AtLimit
        targets.forEach { mail ->
            if (mail.tags.any { it.name == trimmed }) return@forEach
            val type = mail.type ?: return@forEach
            replace(mail.id) {
                it.copy(tags = it.tags + MailTag(trimmed, TagPool.Pending, type))
            }
        }
        clearSelection()
        revision++
        publish()
        return AddTagResult.Added
    }

    fun detail(id: String): MailDetailUi? {
        state
        val mail = mails.find { it.id == id } ?: return null
        val accountName = accountsById[mail.accountId]?.name.orEmpty()
        return MailDetailUi(
            subject = mail.subject,
            fromName = mail.fromName,
            metaLine = "$accountName · ${formatMailTime(mail.timeMillis, System.currentTimeMillis())}",
            body = mail.body,
        )
    }

    fun subjectOf(id: String): String? {
        state
        return mails.find { it.id == id }?.subject
    }

    /** 当前账号和板块里还看得到这封信。筛选不把已经打开的正文关掉。 */
    fun staysOpen(id: String): Boolean {
        val snapshot = state
        val mail = mails.find { it.id == id } ?: return false
        val accountOk = snapshot.accountId == null || mail.accountId == snapshot.accountId
        return accountOk && mail.folder == snapshot.folder
    }

    private fun mutateFolders(ids: Set<String>, target: Folder, message: String) {
        if (ids.isEmpty()) return
        undoSnapshot = mails.toList()
        ids.forEach { id -> replace(id) { it.copy(folder = target) } }
        clearSelection()
        revision++
        notice = NoticeUi(id = System.nanoTime(), text = message, canUndo = true)
        publish()
    }

    private fun restoreUndo() {
        val before = undoSnapshot ?: return
        undoSnapshot = null
        mails.clear()
        mails.addAll(before)
        revision++
        notice = null
    }

    private fun clearSelection() {
        multiSelect = false
        selectedIds.clear()
    }

    private fun visibleMails(): List<Mail> = mails.filter(::matches)

    private fun matches(mail: Mail): Boolean {
        val accountOk = accountId == null || mail.accountId == accountId
        val readOk = when (readFilter) {
            ReadFilter.Any -> true
            ReadFilter.Read -> !mail.unread
            ReadFilter.Unread -> mail.unread
        }
        val typeOk = folder != Folder.Inbox || typeFilter == null || mail.type == typeFilter
        val tagOk = selectedTags.isEmpty() || mail.tags.any { it.name in selectedTags }
        return accountOk && mail.folder == folder && readOk && typeOk && tagOk
    }

    private fun publish() {
        state = project()
    }

    private fun project(): MailboxUiState {
        val now = System.currentTimeMillis()
        val showAccount = accountId == null
        return MailboxUiState(
            revision = revision,
            accountId = accountId,
            selectedAccountName = accountId?.let { accountsById[it]?.name },
            folder = folder,
            rows = visibleMails()
                .sortedByDescending { it.timeMillis }
                .map { toRow(it, now, showAccount) },
            readFilter = readFilter,
            selectedTagNames = selectedTags.toSet(),
            typeFilter = typeFilter,
            multiSelect = multiSelect,
            selectedCount = selectedIds.size,
            tagGroups = tagGroups(),
            accounts = accountUis,
            notice = notice,
        )
    }

    private fun toRow(mail: Mail, now: Long, showAccount: Boolean): MailRowUi {
        val account = accountsById[mail.accountId]
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
            tags = mail.tags.asReversed().map { TagChipUi(it.name, it.pool) },
            showClassifying = folder == Folder.Inbox && typeFilter == null && mail.type == null,
            checked = mail.id in selectedIds,
        )
    }

    private fun tagGroups(): List<TagGroupUi> {
        val grouped = linkedMapOf<Pair<TagPool, MailType>, LinkedHashSet<String>>()
        mails.forEach { mail ->
            mail.tags.forEach { tag ->
                grouped.getOrPut(tag.pool to tag.type) { linkedSetOf() }.add(tag.name)
            }
        }
        return grouped.map { (key, names) ->
            TagGroupUi(pool = key.first, type = key.second, names = names.toList())
        }.sortedWith(compareBy({ it.pool.ordinal }, { it.type.ordinal }))
    }

    private inline fun replace(id: String, block: (Mail) -> Mail) {
        val index = mails.indexOfFirst { it.id == id }
        if (index >= 0) mails[index] = block(mails[index])
    }
}
