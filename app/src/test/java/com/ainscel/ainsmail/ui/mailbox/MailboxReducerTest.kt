package com.ainscel.ainsmail.ui.mailbox

import com.ainscel.ainsmail.model.Mail
import com.ainscel.ainsmail.model.MailType
import com.ainscel.ainsmail.model.MailboxScope
import com.ainscel.ainsmail.model.ReadFilter
import com.ainscel.ainsmail.model.RemoteFolder
import com.ainscel.ainsmail.model.Section
import com.ainscel.ainsmail.model.TagCatalog
import com.ainscel.ainsmail.model.TagPool
import com.ainscel.ainsmail.model.TagWord
import com.ainscel.ainsmail.model.Account
import com.ainscel.ainsmail.model.AccountBadgeStyle
import com.ainscel.ainsmail.model.Provider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
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
    fun filtersKeepSelectionInsideTheVisibleList() {
        val session = session(
            mail("unread", type = MailType.Important, tagNames = listOf("工作")),
            mail("read", type = MailType.Notice, unread = false),
        ).selecting("unread", "read")

        val byRead = reduce(session, MailboxIntent.SetReadFilter(ReadFilter.Unread), 1L).session
        assertEquals(setOf("unread"), byRead.selectedIds)
        assertTrue(byRead.multiSelect)
        assertEquals(1, project(byRead, 0L).list.selectedCount)

        val widened = reduce(byRead, MailboxIntent.SetReadFilter(ReadFilter.Any), 2L).session
        assertEquals(setOf("unread"), widened.selectedIds)

        val byType = reduce(session, MailboxIntent.SetTypeFilter(MailType.Notice), 3L).session
        assertEquals(setOf("read"), byType.selectedIds)
        assertTrue(byType.multiSelect)

        val byTag = reduce(session, MailboxIntent.ToggleTagFilter("工作"), 4L).session
        assertEquals(setOf("unread"), byTag.selectedIds)
        assertTrue(byTag.multiSelect)
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
        assertEquals(RemoteFolder.Inbox, moved.remoteFolder)
        assertEquals(true, moved.locallyDeleted)
        assertEquals(false, moved.archived)
        assertEquals(emptySet<String>(), deleted.copy(section = Section.Inbox).mailsInSection().ids())
        assertEquals(setOf("a"), deleted.copy(section = Section.Trash).mailsInSection().ids())
        assertEquals(Notice.Deleted(4L), deleted.notice)

        val arrived = deleted.copy(mails = deleted.mails + mail("new"))
        val restored = reduce(arrived, MailboxIntent.Undo, 5L).session
        assertEquals(RemoteFolder.Inbox, restored.mail("a").remoteFolder)
        assertEquals(false, restored.mail("a").locallyDeleted)
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

    @Test
    fun readingMailKeepsChromeAndAccountList() {
        val session = MailboxSession(
            accounts = listOf(Account("a1", "甲", "a@qq.com", Provider.Qq)),
            mails = listOf(mail("a")),
            catalog = TagCatalog(emptyList()),
        )
        val first = project(session, now = 0L)
        val read = reduce(session, MailboxIntent.MarkVisibleRead, 1L).session
        val second = project(read, now = 0L, previousAccounts = first.chrome.accounts)

        assertEquals(first.chrome, second.chrome)
        assertSame(first.chrome.accounts, second.chrome.accounts)
        assertEquals(false, second.list.rows.single().unread)
    }

    @Test
    fun realAccountsDropSampleMailAndUnknownScope() {
        val session = MailboxSession(
            accounts = listOf(Account("a1", "甲", "a@qq.com", Provider.Qq)),
            mails = listOf(mail("a")),
            catalog = TagCatalog(emptyList()),
            scope = MailboxScope.Account("a1"),
            multiSelect = true,
            selectedIds = setOf("a"),
        )
        val real = Account("u1", "乙", "b@qq.com", Provider.Qq)
        val next = reduce(session, MailboxIntent.UseRealAccounts(listOf(real)), 1L).session

        assertTrue(next.mails.isEmpty())
        assertEquals(MailboxScope.All, next.scope)
        assertEquals(listOf(real), next.accounts)
        assertTrue(next.selectedIds.isEmpty())
        assertNull(next.undo)
    }

    @Test
    fun replaceAccountsKeepsMailAndDropsMissingScope() {
        val keptMail = mail("a")
        val session = MailboxSession(
            accounts = listOf(Account("a1", "甲", "a@qq.com", Provider.Qq)),
            mails = listOf(keptMail),
            catalog = TagCatalog(emptyList()),
            scope = MailboxScope.Account("a1"),
            multiSelect = true,
            selectedIds = setOf("a"),
        )
        val renamed = Account("a1", "甲乙", "a@qq.com", Provider.Qq)
        val kept = reduce(session, MailboxIntent.ReplaceAccounts(listOf(renamed)), 1L).session
        assertEquals(listOf(keptMail), kept.mails)
        assertEquals(MailboxScope.Account("a1"), kept.scope)
        assertEquals("甲乙", kept.accounts.single().name)
        assertEquals(setOf("a"), kept.selectedIds)

        val removed = reduce(session, MailboxIntent.ReplaceAccounts(emptyList()), 2L).session
        assertEquals(MailboxScope.All, removed.scope)
        assertTrue(removed.selectedIds.isEmpty())
        assertEquals(listOf(keptMail), removed.mails)
    }

    @Test
    fun avatarChangeReplacesAccountList() {
        val session = MailboxSession(
            accounts = listOf(Account("a1", "甲", "a@qq.com", Provider.Qq)),
            mails = listOf(mail("a")),
            catalog = TagCatalog(emptyList()),
        )
        val first = project(session, now = 0L)
        val same = project(
            session.copy(accounts = listOf(Account("a1", "甲", "a@qq.com", Provider.Qq))),
            now = 0L,
            previousAccounts = first.chrome.accounts,
        )
        assertSame(first.chrome.accounts, same.chrome.accounts)

        val photo = project(
            session.copy(
                accounts = listOf(Account("a1", "甲", "a@qq.com", Provider.Qq, "account/a1/avatar-1")),
            ),
            now = 0L,
            previousAccounts = first.chrome.accounts,
        )
        assertNotSame(first.chrome.accounts, photo.chrome.accounts)
        assertEquals("account/a1/avatar-1", photo.chrome.accounts.single().avatarFile)
    }

    @Test
    fun selectionInTheSameMinuteReusesRows() {
        val session = session(mail("a"), mail("b")).selecting("a")
        val now = 1_700_000_000_000L
        val first = project(session, now)
        val toggled = reduce(session, MailboxIntent.ToggleSelected("b"), 1L).session

        assertTrue(rowsReusable(session, toggled, now / 60_000, now + 1_000))
        assertFalse(rowsReusable(session, toggled, now / 60_000, now + 60_000))

        val second = project(
            toggled,
            now + 1_000,
            previousAccounts = first.chrome.accounts,
            reuse = ListReuse(first.list.rows, first.chrome.sectionMailIds, first.list.tagGroups),
        )
        assertSame(first.list.rows, second.list.rows)
        assertSame(first.chrome.sectionMailIds, second.chrome.sectionMailIds)
        assertSame(first.chrome.accounts, second.chrome.accounts)
        assertEquals(setOf("a", "b"), second.list.selectedIds)
    }

    @Test
    fun markVisibleReadLeavesFilteredMailUnread() {
        val session = session(
            mail("shown", unread = true),
            mail("hidden", unread = true, type = MailType.Notice),
        ).copy(typeFilter = MailType.Important)

        val read = reduce(session, MailboxIntent.MarkVisibleRead, 1L).session

        assertFalse(read.mail("shown").unread)
        assertTrue(read.mail("hidden").unread)
    }

    @Test
    fun accountBadgeCollapsesOnlyAConsecutiveRun() {
        val accounts = listOf(
            Account("a1", "甲", "a@qq.com", Provider.Qq, "account/a1/avatar"),
            Account("a2", "乙", "b@qq.com", Provider.Qq),
        )
        val session = MailboxSession(
            accounts = accounts,
            mails = listOf(
                mail("later", accountId = "a2", timeMillis = 5L),
                mail("first", accountId = "a1", timeMillis = 30L),
                mail("second", accountId = "a1", timeMillis = 20L),
                mail("third", accountId = "a2", timeMillis = 10L),
            ),
            catalog = TagCatalog(emptyList()),
        )

        val every = project(session, now = 0L, badgeStyle = AccountBadgeStyle.EveryRow)
        assertEquals(listOf("甲", "甲", "乙", "乙"), every.list.rows.map { it.accountName })

        val collapsed = project(session, now = 0L, badgeStyle = AccountBadgeStyle.FirstOfRun)
        assertEquals(listOf("甲", null, "乙", null), collapsed.list.rows.map { it.accountName })
        assertEquals(
            listOf("account/a1/avatar", null, null, null),
            collapsed.list.rows.map { it.accountAvatar },
        )

        val oneAccount = project(
            session.copy(scope = MailboxScope.Account("a1")),
            now = 0L,
            badgeStyle = AccountBadgeStyle.EveryRow,
        )
        assertTrue(oneAccount.list.rows.all { it.accountName == null })
    }

    @Test
    fun accountBadgeRepeatsWhenSenderOrAccountChanges() {
        val accounts = listOf(
            Account("a1", "甲", "a@qq.com", Provider.Qq),
            Account("a2", "乙", "b@qq.com", Provider.Qq),
        )
        val session = MailboxSession(
            accounts = accounts,
            mails = listOf(
                mail("b-wang", accountId = "a2", fromName = "王五", timeMillis = 10L),
                mail("b-li", accountId = "a2", fromName = "李四", timeMillis = 20L),
                mail("a-li", accountId = "a1", fromName = "李四", timeMillis = 30L),
                mail("a-zhang-old", accountId = "a1", fromName = "张三", timeMillis = 40L),
                mail("a-zhang-new", accountId = "a1", fromName = "张三", timeMillis = 50L),
            ),
            catalog = TagCatalog(emptyList()),
        )

        val bySender = project(session, now = 0L, badgeStyle = AccountBadgeStyle.SameSender)
        assertEquals(listOf("甲", null, "甲", "乙", "乙"), bySender.list.rows.map { it.accountName })

        val byAccount = project(session, now = 0L, badgeStyle = AccountBadgeStyle.FirstOfRun)
        assertEquals(listOf("甲", null, null, "乙", null), byAccount.list.rows.map { it.accountName })
    }

    @Test
    fun replaceMailsKeepsSelectionThatStillExists() {
        val session = session(mail("a"), mail("b")).selecting("a", "gone")
        val next = reduce(
            session,
            MailboxIntent.ReplaceMails(listOf(mail("a"), mail("c"))),
            noticeId = 1L,
        ).session
        assertEquals(setOf("a"), next.selectedIds)
        assertEquals(setOf("a", "c"), next.mails.ids())
        assertTrue(next.multiSelect)
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
    unread: Boolean = true,
    timeMillis: Long = 0L,
    fromName: String = "发件人",
): Mail = Mail(
    id = id,
    accountId = accountId,
    remoteFolder = remoteFolder,
    archived = archived,
    fromName = fromName,
    subject = id,
    preview = "",
    body = "占位正文",
    timeMillis = timeMillis,
    unread = unread,
    type = type,
    tagNames = tagNames,
)
