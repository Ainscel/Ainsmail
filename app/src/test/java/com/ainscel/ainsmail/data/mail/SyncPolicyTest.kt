package com.ainscel.ainsmail.data.mail

import com.ainscel.ainsmail.model.RemoteFolder
import com.ainscel.ainsmail.model.Section
import com.ainscel.ainsmail.model.inSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncPolicyTest {
    @Test
    fun uidWindowAndValidity() {
        assertEquals(6L, nextUid(5L))
        assertTrue(uidValidityChanged(10L, 11L))
        assertFalse(uidValidityChanged(0L, 11L))
        assertFalse(uidValidityChanged(10L, 10L))
    }

    @Test
    fun archiveHasNoSyncFolderButIdleWatchesInbox() {
        assertNull(syncFolder(Section.Archive))
        assertEquals(RemoteFolder.Inbox, syncFolder(Section.Inbox))
        assertEquals(RemoteFolder.Sent, syncFolder(Section.Sent))
        assertEquals(RemoteFolder.Inbox, idleFolder(Section.Archive))
        assertEquals(RemoteFolder.Trash, idleFolder(Section.Trash))
    }

    @Test
    fun vendorCategoryFoldersStayOut() {
        assertTrue(isVendorCategoryFolder("[Gmail]/Promotions", "Promotions"))
        assertTrue(isVendorCategoryFolder("[Gmail]/Social", "Social"))
        assertTrue(isVendorCategoryFolder("Updates", "Updates"))
        assertTrue(isVendorCategoryFolder("广告邮件", "广告邮件"))
        assertFalse(isVendorCategoryFolder("INBOX", "INBOX"))
        assertFalse(isVendorCategoryFolder("已删除", "已删除"))
        assertFalse(isVendorCategoryFolder("垃圾箱", "垃圾箱"))
    }

    @Test
    fun initialSyncWaitsUntilInboxWindowIsDone() {
        assertTrue(accountNeedsInitialSync(emptyList()))
        assertTrue(
            accountNeedsInitialSync(
                listOf(cursor(RemoteFolder.Inbox, recent = true, history = false)),
            ),
        )
        assertFalse(
            accountNeedsInitialSync(
                listOf(cursor(RemoteFolder.Inbox, recent = true, history = true)),
            ),
        )
    }

    @Test
    fun rebuiltArchiveStaysOutOfInbox() {
        val incoming = header(uid = 9L, messageId = "m1", unread = true)
        val old = header(
            uid = 3L,
            messageId = "<m1>",
            unread = false,
            archived = true,
            seenPending = true,
        )
        val result = reapplyPreserved(listOf(incoming), listOf(old.toPreserved()))
        val row = result.synced.single()
        val mail = row.toMail()!!

        assertTrue(result.kept.isEmpty())
        assertTrue(row.archived)
        assertFalse(row.unread)
        assertTrue(row.seenPending)
        assertFalse(mail.inSection(Section.Inbox))
        assertTrue(mail.inSection(Section.Archive))
    }

    @Test
    fun archivedMailOutsideTheWindowIsKeptWhenUidIsFree() {
        val incoming = header(uid = 8L, messageId = "new")
        val old = header(uid = 2L, messageId = "old", archived = true)
        val result = reapplyPreserved(listOf(incoming), listOf(old.toPreserved()))

        assertFalse(result.synced.single().archived)
        assertEquals(-2L, result.kept.single().uid)
        assertEquals("a1:Inbox:-2", result.kept.single().id)
        assertTrue(result.kept.single().archived)
        assertTrue(result.kept.single().toMail()!!.inSection(Section.Archive))
    }

    @Test
    fun reusedUidDropsTheOldArchiveInsteadOfColliding() {
        val incoming = header(uid = 2L, messageId = "new", subject = "新")
        val old = header(uid = 2L, messageId = "old", subject = "旧", archived = true)
        val result = reapplyPreserved(listOf(incoming), listOf(old.toPreserved()))

        assertTrue(result.kept.isEmpty())
        assertFalse(result.synced.single().archived)
    }

    private fun cursor(folder: RemoteFolder, recent: Boolean, history: Boolean) = FolderSyncCursorEntity(
        accountId = "a1",
        remoteFolder = folder.name,
        folderPath = folder.name,
        uidValidity = 1L,
        highestUid = 1L,
        recentSyncDone = recent,
        historySyncDone = history,
    )

    private fun header(
        uid: Long,
        messageId: String,
        subject: String = "主题",
        unread: Boolean = true,
        archived: Boolean = false,
        seenPending: Boolean = false,
    ) = MailHeaderEntity(
        id = "a1:Inbox:$uid",
        accountId = "a1",
        remoteFolder = RemoteFolder.Inbox.name,
        uid = uid,
        uidValidity = 4L,
        fromName = "发件人",
        fromAddress = "a@qq.com",
        toCsv = "",
        ccCsv = "",
        subject = subject,
        timeMillis = 20L,
        unread = unread,
        hasAttachment = false,
        archived = archived,
        messageId = messageId,
        seenPending = seenPending,
    )
}
