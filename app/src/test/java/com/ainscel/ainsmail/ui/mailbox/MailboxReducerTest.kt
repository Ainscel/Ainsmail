package com.ainscel.ainsmail.ui.mailbox

import com.ainscel.ainsmail.model.Mail
import com.ainscel.ainsmail.model.MailType
import com.ainscel.ainsmail.model.MailboxScope
import com.ainscel.ainsmail.model.RemoteFolder
import com.ainscel.ainsmail.model.Section
import com.ainscel.ainsmail.model.TagCatalog
import com.ainscel.ainsmail.model.TagPool
import com.ainscel.ainsmail.model.TagWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MailboxReducerTest {
    @Test
    fun archiveLeavesInboxAndStaysOnTheServerFolder() {
        val session = session(
            mail("inbox"),
            mail("archived", archived = true),
            mail("draft", remoteFolder = RemoteFolder.Drafts),
            mail("trash", remoteFolder = RemoteFolder.Trash),
        )

        assertEquals(setOf("inbox"), session.mailsInSection().ids())
        assertEquals(setOf("archived"), session.copy(section = Section.Archive).mailsInSection().ids())
        assertEquals(setOf("draft"), session.copy(section = Section.Drafts).mailsInSection().ids())
        assertEquals(setOf("trash"), session.copy(section = Section.Trash).mailsInSection().ids())
    }

    @Test
    fun typeFilterDoesNotApplyOutsideInbox() {
        val session = session(
            mail("important", type = MailType.Important),
            mail("notice", type = MailType.Notice),
            mail("draft", type = MailType.Notice, remoteFolder = RemoteFolder.Drafts),
        ).copy(typeFilter = MailType.Important)

        assertEquals(setOf("important"), session.visibleMails().ids())
        assertEquals(
            setOf("draft"),
            session.copy(section = Section.Drafts).visibleMails().ids(),
        )
    }

    @Test
    fun scopeKeepsOnlyThatAccount() {
        val session = session(
            mail("a", accountId = "a1"),
            mail("b", accountId = "a2"),
        )
        val narrowed = reduce(session, MailboxIntent.SelectScope(MailboxScope.Account("a2")), 1L).session
        assertEquals(setOf("b"), narrowed.mailsInSection().ids())
    }

    @Test
    fun changingSectionClearsSelection() {
        val session = session(mail("a"), mail("b")).selecting("a")
        val moved = reduce(session, MailboxIntent.SelectSection(Section.Sent), 1L).session
        assertTrue(moved.selectedIds.isEmpty())
        assertEquals(false, moved.multiSelect)

        val same = reduce(session, MailboxIntent.SelectSection(Section.Inbox), 1L).session
        assertEquals(setOf("a"), same.selectedIds)
    }

    @Test
    fun newTagOnOneTypeEntersPendingPool() {
        val session = session(
            mail("a", type = MailType.Important),
            mail("b", type = MailType.Important),
        ).selecting("a", "b")

        val outcome = reduce(session, MailboxIntent.AddTag(" 课程 "), 7L)
        assertEquals(AddTagResult.Added, outcome.tagResult)
        assertEquals(listOf("课程"), outcome.session.mail("a").tagNames)
        assertEquals(listOf("课程"), outcome.session.mail("b").tagNames)
        assertEquals(TagPool.Pending, outcome.session.catalog.find("课程")?.pool)
        assertEquals(MailType.Important, outcome.session.catalog.find("课程")?.type)
        assertTrue(outcome.session.selectedIds.isEmpty())
        assertNull(outcome.session.notice)
    }

    @Test
    fun existingWordIsAttachedWithoutChangingItsPool() {
        val catalog = TagCatalog(listOf(TagWord("同事", MailType.Important, TagPool.Active)))
        val session = session(mail("a", type = MailType.Notice), catalog = catalog).selecting("a")

        val outcome = reduce(session, MailboxIntent.AddTag("同事"), 1L)
        assertEquals(AddTagResult.Added, outcome.tagResult)
        assertEquals(listOf("同事"), outcome.session.mail("a").tagNames)
        assertEquals(TagPool.Active, outcome.session.catalog.find("同事")?.pool)
        assertEquals(MailType.Important, outcome.session.catalog.find("同事")?.type)
    }

    @Test
    fun newWordAcrossTypesIsRefused() {
        val session = session(
            mail("a", type = MailType.Important),
            mail("b", type = MailType.Notice),
        ).selecting("a", "b")

        val outcome = reduce(session, MailboxIntent.AddTag("课程"), 3L)
        assertEquals(AddTagResult.MixedTypes, outcome.tagResult)
        assertEquals(emptyList<String>(), outcome.session.mail("a").tagNames)
        assertNull(outcome.session.catalog.find("课程"))
        assertEquals(setOf("a", "b"), outcome.session.selectedIds)
        assertEquals(Notice.TagMixedTypes(3L), outcome.session.notice)
    }

    @Test
    fun tagLimitAndMissingTypeDoNotWrite() {
        val full = session(mail("a", tagNames = listOf("一", "二", "三"))).selecting("a")
        val limited = reduce(full, MailboxIntent.AddTag("四"), 1L)
        assertEquals(AddTagResult.AtLimit, limited.tagResult)
        assertEquals(listOf("一", "二", "三"), limited.session.mail("a").tagNames)
        assertEquals(Notice.TagLimit(1L), limited.session.notice)

        val untyped = session(mail("a", type = null)).selecting("a")
        val needsType = reduce(untyped, MailboxIntent.AddTag("课程"), 2L)
        assertEquals(AddTagResult.NeedsType, needsType.tagResult)
        assertEquals(emptyList<String>(), needsType.session.mail("a").tagNames)
        assertEquals(Notice.TagNeedsType(2L), needsType.session.notice)

        val blank = reduce(full, MailboxIntent.AddTag("  "), 3L)
        assertEquals(AddTagResult.Blank, blank.tagResult)
        assertEquals(full, blank.session)
    }

    @Test
    fun deleteClearsArchiveAndUndoKeepsLaterMail() {
        val session = session(mail("a", archived = true)).selecting("a")
        val deleted = reduce(session, MailboxIntent.DeleteSelected, 4L).session
        val moved = deleted.mail("a")
        assertEquals(RemoteFolder.Trash, moved.remoteFolder)
        assertEquals(false, moved.archived)
        assertEquals(Notice.Deleted(4L), deleted.notice)

        val arrived = deleted.copy(mails = deleted.mails + mail("new"))
        val restored = reduce(arrived, MailboxIntent.Undo, 5L).session
        assertEquals(RemoteFolder.Inbox, restored.mail("a").remoteFolder)
        assertEquals(true, restored.mail("a").archived)
        assertEquals(RemoteFolder.Inbox, restored.mail("new").remoteFolder)
        assertNull(restored.undo)
        assertNull(restored.notice)
    }

    @Test
    fun archiveKeepsRemoteFolderAndUndoRestoresTheFlag() {
        val session = session(mail("a")).selecting("a")
        val archived = reduce(session, MailboxIntent.ArchiveSelected, 8L).session
        assertEquals(RemoteFolder.Inbox, archived.mail("a").remoteFolder)
        assertEquals(true, archived.mail("a").archived)
        assertEquals(Notice.Archived(8L), archived.notice)
        assertEquals(emptySet<String>(), archived.copy(section = Section.Inbox).mailsInSection().ids())
        assertEquals(setOf("a"), archived.copy(section = Section.Archive).mailsInSection().ids())

        val restored = reduce(archived, MailboxIntent.Undo, 9L).session
        assertEquals(false, restored.mail("a").archived)
        assertEquals(setOf("a"), restored.mailsInSection().ids())
    }
}

private fun MailboxSession.selecting(vararg ids: String): MailboxSession =
    copy(multiSelect = true, selectedIds = ids.toSet())

private fun MailboxSession.mail(id: String): Mail = mails.first { it.id == id }

private fun List<Mail>.ids(): Set<String> = map { it.id }.toSet()

private fun session(
    vararg mails: Mail,
    catalog: TagCatalog = TagCatalog(emptyList()),
): MailboxSession = MailboxSession(
    accounts = emptyList(),
    mails = mails.toList(),
    catalog = catalog,
)

private fun mail(
    id: String,
    type: MailType? = MailType.Important,
    tagNames: List<String> = emptyList(),
    remoteFolder: RemoteFolder = RemoteFolder.Inbox,
    archived: Boolean = false,
    accountId: String = "a1",
): Mail = Mail(
    id = id,
    accountId = accountId,
    remoteFolder = remoteFolder,
    archived = archived,
    fromName = "发件人",
    subject = id,
    preview = "",
    body = "占位正文",
    timeMillis = 0L,
    unread = true,
    type = type,
    tagNames = tagNames,
)
