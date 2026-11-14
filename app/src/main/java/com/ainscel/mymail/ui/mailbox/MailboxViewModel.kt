package com.ainscel.mymail.ui.mailbox

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.lifecycle.ViewModel
import com.ainscel.mymail.data.SampleAccounts
import com.ainscel.mymail.data.sampleMails
import com.ainscel.mymail.model.Account
import com.ainscel.mymail.model.Folder
import com.ainscel.mymail.model.Mail
import com.ainscel.mymail.model.MailTag
import com.ainscel.mymail.model.MailType
import com.ainscel.mymail.model.ReadFilter
import com.ainscel.mymail.model.TagPool

/** 骨架阶段的邮箱状态。邮件在内存里，旋转屏幕还在，进程被杀掉就回到假数据。 */
class MailboxViewModel : ViewModel() {
    val accounts: List<Account> = SampleAccounts
    val mails: SnapshotStateList<Mail> = sampleMails(System.currentTimeMillis()).toMutableStateList()

    /** null 表示全部邮箱。 */
    var accountId by mutableStateOf<String?>(null)
    var folder by mutableStateOf(Folder.Inbox)
    var readFilter by mutableStateOf(ReadFilter.Any)
    val selectedTags = mutableListOf<String>().toMutableStateList()
    var typeFilter by mutableStateOf<MailType?>(null)
    var capsuleExpanded by mutableStateOf(false)
    var multiSelect by mutableStateOf(false)
    val selectedIds = mutableListOf<String>().toMutableStateList()
    var notice by mutableStateOf<Notice?>(null)

    fun visibleMails(): List<Mail> {
        return mails.filter { mail ->
            val accountOk = accountId == null || mail.accountId == accountId
            val readOk = when (readFilter) {
                ReadFilter.Any -> true
                ReadFilter.Read -> !mail.unread
                ReadFilter.Unread -> mail.unread
            }
            val typeOk = folder != Folder.Inbox || typeFilter == null || mail.type == typeFilter
            val tagOk = selectedTags.isEmpty() || mail.tags.any { it.name in selectedTags }
            accountOk && mail.folder == folder && readOk && typeOk && tagOk
        }.sortedByDescending { it.timeMillis }
    }

    fun account(id: String): Account? = accounts.find { it.id == id }

    fun selectAccount(id: String?) {
        accountId = id
    }

    fun selectFolder(next: Folder) {
        folder = next
    }

    fun toggleTag(name: String) {
        if (name in selectedTags) selectedTags.remove(name) else selectedTags.add(name)
    }

    fun enterMultiSelect() {
        multiSelect = true
        selectedIds.clear()
    }

    fun exitMultiSelect() {
        multiSelect = false
        selectedIds.clear()
    }

    fun toggleSelected(id: String) {
        if (id in selectedIds) selectedIds.remove(id) else selectedIds.add(id)
    }

    fun markVisibleRead() {
        val ids = visibleMails().filter { it.unread }.map { it.id }.toSet()
        ids.forEach { id -> replace(id) { it.copy(unread = false) } }
    }

    fun deleteSelected(message: String) {
        mutateFolders(selectedIds.toSet(), Folder.Trash, message)
    }

    fun deleteMail(id: String, message: String) {
        mutateFolders(setOf(id), Folder.Trash, message)
    }

    fun archiveSelected(message: String) {
        mutateFolders(selectedIds.toSet(), Folder.Archive, message)
    }

    fun setType(type: MailType) {
        val ids = selectedIds.toSet()
        ids.forEach { id -> replace(id) { it.copy(type = type) } }
        exitMultiSelect()
    }

    /** 还没分类的信不添加。已经有三个不同标签的信，这一批都不加。 */
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
        exitMultiSelect()
        return AddTagResult.Added
    }

    fun message(text: String) {
        notice = Notice(id = System.nanoTime(), text = text, undo = null)
    }

    private fun mutateFolders(ids: Set<String>, folder: Folder, message: String) {
        if (ids.isEmpty()) return
        val before = mails.toList()
        ids.forEach { id -> replace(id) { it.copy(folder = folder) } }
        exitMultiSelect()
        notice = Notice(
            id = System.nanoTime(),
            text = message,
            undo = {
                mails.clear()
                mails.addAll(before)
            },
        )
    }

    private inline fun replace(id: String, block: (Mail) -> Mail) {
        val index = mails.indexOfFirst { it.id == id }
        if (index >= 0) mails[index] = block(mails[index])
    }

    data class Notice(
        val id: Long,
        val text: String,
        val undo: (() -> Unit)?,
    )

    enum class AddTagResult {
        Added,
        Blank,
        NeedsType,
        AtLimit,
    }
}
