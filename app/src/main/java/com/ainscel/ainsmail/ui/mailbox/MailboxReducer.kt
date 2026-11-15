package com.ainscel.ainsmail.ui.mailbox

import com.ainscel.ainsmail.model.Account
import com.ainscel.ainsmail.model.Mail
import com.ainscel.ainsmail.model.MailType
import com.ainscel.ainsmail.model.MailboxScope
import com.ainscel.ainsmail.model.ReadFilter
import com.ainscel.ainsmail.model.RemoteFolder
import com.ainscel.ainsmail.model.Section
import com.ainscel.ainsmail.model.inScope
import com.ainscel.ainsmail.model.inSection

/** 意图的唯一入口。调用方负责在返回之后发布一次投影。 */
fun reduce(session: MailboxSession, intent: MailboxIntent, noticeId: Long): ReduceOutcome {
    return when (intent) {
        is MailboxIntent.SelectScope -> {
            if (session.scope == intent.scope) {
                ReduceOutcome(session)
            } else {
                ReduceOutcome(session.copy(scope = intent.scope).clearSelection())
            }
        }
        is MailboxIntent.SelectSection -> {
            if (session.section == intent.section) {
                ReduceOutcome(session)
            } else {
                ReduceOutcome(session.copy(section = intent.section).clearSelection())
            }
        }
        is MailboxIntent.SetReadFilter -> ReduceOutcome(session.copy(readFilter = intent.filter))
        is MailboxIntent.ToggleTagFilter -> {
            val next = if (intent.name in session.selectedTags) {
                session.selectedTags - intent.name
            } else {
                session.selectedTags + intent.name
            }
            ReduceOutcome(session.copy(selectedTags = next))
        }
        is MailboxIntent.SetTypeFilter -> ReduceOutcome(session.copy(typeFilter = intent.type))
        MailboxIntent.EnterMultiSelect -> {
            ReduceOutcome(session.copy(multiSelect = true, selectedIds = emptySet()))
        }
        MailboxIntent.ExitMultiSelect -> ReduceOutcome(session.clearSelection())
        is MailboxIntent.ToggleSelected -> {
            val next = if (intent.id in session.selectedIds) {
                session.selectedIds - intent.id
            } else {
                session.selectedIds + intent.id
            }
            ReduceOutcome(session.copy(selectedIds = next))
        }
        MailboxIntent.MarkVisibleRead -> ReduceOutcome(session.markVisibleRead())
        MailboxIntent.DeleteSelected -> ReduceOutcome(session.delete(session.selectedIds, noticeId))
        is MailboxIntent.DeleteMail -> ReduceOutcome(session.delete(setOf(intent.id), noticeId))
        MailboxIntent.ArchiveSelected -> ReduceOutcome(session.archive(session.selectedIds, noticeId))
        is MailboxIntent.SetType -> ReduceOutcome(session.setType(intent.type))
        is MailboxIntent.AddTag -> addTag(session, intent.name, noticeId)
        is MailboxIntent.ConsumeNotice -> {
            if (session.notice?.id == intent.id) {
                ReduceOutcome(session.copy(notice = null))
            } else {
                ReduceOutcome(session)
            }
        }
        MailboxIntent.Undo -> ReduceOutcome(session.restoreUndo())
        is MailboxIntent.UseRealAccounts -> ReduceOutcome(
            session.copy(
                accounts = intent.accounts,
                mails = emptyList(),
                scope = retainedScope(session.scope, intent.accounts),
                undo = null,
                notice = null,
            ).clearSelection(),
        )
        is MailboxIntent.ReplaceAccounts -> {
            val scope = retainedScope(session.scope, intent.accounts)
            val next = session.copy(accounts = intent.accounts, scope = scope)
                .let { if (it.scope == session.scope) it else it.clearSelection() }
            if (next == session) ReduceOutcome(session) else ReduceOutcome(next)
        }
        is MailboxIntent.RestoreSamples -> ReduceOutcome(
            session.copy(
                accounts = intent.accounts,
                mails = intent.mails,
                scope = MailboxScope.All,
                undo = null,
                notice = null,
            ).clearSelection(),
        )
    }
}

private fun retainedScope(scope: MailboxScope, accounts: List<Account>): MailboxScope = when (scope) {
    MailboxScope.All -> scope
    is MailboxScope.Account -> if (accounts.any { it.id == scope.id }) scope else MailboxScope.All
}

/** 当前范围和板块里的信，不含已读、类型、标签筛选。 */
fun MailboxSession.mailsInSection(): List<Mail> =
    mails.filter { it.inScope(scope) && it.inSection(section) }

fun MailboxSession.visibleMails(): List<Mail> = mailsInSection().filter(::matchesFilters)

private fun MailboxSession.matchesFilters(mail: Mail): Boolean {
    val readOk = when (readFilter) {
        ReadFilter.Any -> true
        ReadFilter.Read -> !mail.unread
        ReadFilter.Unread -> mail.unread
    }
    val typeOk = section != Section.Inbox || typeFilter == null || mail.type == typeFilter
    val tagOk = selectedTags.isEmpty() || mail.tagNames.any { it in selectedTags }
    return readOk && typeOk && tagOk
}

private fun MailboxSession.clearSelection(): MailboxSession =
    copy(multiSelect = false, selectedIds = emptySet())

private fun MailboxSession.markVisibleRead(): MailboxSession {
    val unreadIds = visibleMails().filter { it.unread }.map { it.id }.toSet()
    if (unreadIds.isEmpty()) return this
    return copy(mails = mails.map { if (it.id in unreadIds) it.copy(unread = false) else it })
}

private fun MailboxSession.setType(type: MailType): MailboxSession {
    return copy(
        mails = mails.map { if (it.id in selectedIds) it.copy(type = type) else it },
    ).clearSelection()
}

private fun MailboxSession.delete(ids: Set<String>, noticeId: Long): MailboxSession {
    return relocate(ids, Notice.Deleted(noticeId)) { mail ->
        mail.copy(remoteFolder = RemoteFolder.Trash, archived = false)
    }
}

private fun MailboxSession.archive(ids: Set<String>, noticeId: Long): MailboxSession {
    return relocate(ids, Notice.Archived(noticeId)) { mail ->
        mail.copy(archived = true)
    }
}

private fun MailboxSession.relocate(
    ids: Set<String>,
    notice: Notice,
    transform: (Mail) -> Mail,
): MailboxSession {
    if (ids.isEmpty()) return this
    val previous = linkedMapOf<String, MailPlacement>()
    val updated = mails.map { mail ->
        if (mail.id !in ids) {
            mail
        } else {
            previous[mail.id] = MailPlacement(mail.remoteFolder, mail.archived)
            transform(mail)
        }
    }
    if (previous.isEmpty()) return this
    return copy(
        mails = updated,
        undo = UndoRecord(previous.toMap()),
        notice = notice,
    ).clearSelection()
}

private fun MailboxSession.restoreUndo(): MailboxSession {
    val record = undo ?: return this
    val restored = mails.map { mail ->
        val placement = record.placements[mail.id] ?: return@map mail
        mail.copy(remoteFolder = placement.remoteFolder, archived = placement.archived)
    }
    return copy(mails = restored, undo = null, notice = null)
}

private fun addTag(session: MailboxSession, name: String, noticeId: Long): ReduceOutcome {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return ReduceOutcome(session, AddTagResult.Blank)
    val targets = session.selectedIds.mapNotNull { id -> session.mails.find { it.id == id } }
    if (targets.isEmpty()) return ReduceOutcome(session.clearSelection(), AddTagResult.Added)
    if (targets.any { it.type == null }) {
        return ReduceOutcome(
            session.copy(notice = Notice.TagNeedsType(noticeId)),
            AddTagResult.NeedsType,
        )
    }
    val overLimit = targets.any { mail -> mail.tagNames.size >= 3 && trimmed !in mail.tagNames }
    if (overLimit) {
        return ReduceOutcome(
            session.copy(notice = Notice.TagLimit(noticeId)),
            AddTagResult.AtLimit,
        )
    }
    val catalog = if (session.catalog.find(trimmed) != null) {
        session.catalog
    } else {
        val type = targets.mapNotNull { it.type }.toSet().singleOrNull() ?: return ReduceOutcome(
            session.copy(notice = Notice.TagMixedTypes(noticeId)),
            AddTagResult.MixedTypes,
        )
        session.catalog.withPending(trimmed, type)
    }
    val updated = session.mails.map { mail ->
        if (mail.id !in session.selectedIds || trimmed in mail.tagNames) {
            mail
        } else {
            mail.copy(tagNames = mail.tagNames + trimmed)
        }
    }
    return ReduceOutcome(
        session.copy(mails = updated, catalog = catalog).clearSelection(),
        AddTagResult.Added,
    )
}
