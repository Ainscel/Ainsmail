package com.ainscel.ainsmail.ui.mailbox

import com.ainscel.ainsmail.model.Mail
import com.ainscel.ainsmail.model.MailType
import com.ainscel.ainsmail.model.RemoteFolder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalMailOverlayTest {
    @Test
    fun oldSnapshotDoesNotCoverReadArchiveOrLocalDelete() {
        val pending = linkedMapOf(
            "a" to mail(id = "a", unread = false, archived = true, locallyDeleted = true),
        )
        val merged = mergePending(pending, listOf(mail(id = "a", unread = true, subject = "新主题")))
        val row = merged.single()
        assertFalse(row.unread)
        assertTrue(row.archived)
        assertTrue(row.locallyDeleted)
        assertEquals("a", row.subject)
        assertEquals(setOf("a"), pending.keys)
    }

    @Test
    fun dropsPendingOnceDatabaseCatchesUp() {
        val pending = linkedMapOf("a" to mail(id = "a", unread = false, subject = "旧"))
        val merged = mergePending(pending, listOf(mail(id = "a", unread = false, subject = "新主题")))
        assertEquals("新主题", merged.single().subject)
        assertTrue(pending.isEmpty())
    }

    @Test
    fun dropsPendingMissingFromDatabase() {
        val pending = linkedMapOf("gone" to mail(id = "gone", locallyDeleted = true))
        val merged = mergePending(pending, listOf(mail(id = "a")))
        assertEquals(listOf("a"), merged.map { it.id })
        assertTrue(pending.isEmpty())
    }

    @Test
    fun emptyPendingReturnsTheDatabaseList() {
        val fromDb = listOf(mail(id = "a"))
        assertSame(fromDb, mergePending(linkedMapOf(), fromDb))
    }

    @Test
    fun typeAndTagEditsAreNotLocalWrites() {
        assertFalse(MailboxIntent.SetType(MailType.Notice).writesLocalMail())
        assertFalse(MailboxIntent.AddTag("课程").writesLocalMail())
        assertTrue(MailboxIntent.DeleteMail("a").writesLocalMail())
        assertTrue(MailboxIntent.ArchiveSelected.writesLocalMail())
    }
}

private fun mail(
    id: String,
    unread: Boolean = true,
    archived: Boolean = false,
    locallyDeleted: Boolean = false,
    subject: String = id,
): Mail = Mail(
    id = id,
    accountId = "a1",
    remoteFolder = RemoteFolder.Inbox,
    archived = archived,
    locallyDeleted = locallyDeleted,
    fromName = "发件人",
    subject = subject,
    preview = "",
    body = "",
    timeMillis = 0L,
    unread = unread,
    type = null,
    tagNames = emptyList(),
)
