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
    fun pendingFlagsDoNotCoverEnvelope() {
        val pending = linkedMapOf(
            "a" to LocalFlags(unread = false, archived = true, locallyDeleted = true),
        )
        val merged = mergeSnapshot(
            pending,
            listOf(mail(id = "a", unread = true, subject = "新主题")),
            emptyList(),
        )
        val row = merged.single()
        assertFalse(row.unread)
        assertTrue(row.archived)
        assertTrue(row.locallyDeleted)
        assertEquals("新主题", row.subject)
        assertEquals(setOf("a"), pending.keys)
    }

    @Test
    fun sessionFieldsSurviveSnapshot() {
        val previous = listOf(
            mail(
                id = "a",
                subject = "旧",
                type = MailType.Notice,
                tagNames = listOf("课程"),
                preview = "预览",
                body = "正文",
            ),
        )
        val merged = mergeSnapshot(
            linkedMapOf(),
            listOf(mail(id = "a", subject = "新主题")),
            previous,
        )
        val row = merged.single()
        assertEquals("新主题", row.subject)
        assertEquals(MailType.Notice, row.type)
        assertEquals(listOf("课程"), row.tagNames)
        assertEquals("预览", row.preview)
        assertEquals("正文", row.body)
    }

    @Test
    fun databasePreviewOverridesSessionPreview() {
        val previous = listOf(mail(id = "a", preview = "旧预览", type = MailType.Notice))
        val merged = mergeSnapshot(
            linkedMapOf(),
            listOf(mail(id = "a", preview = "库里的预览", bodyReady = true)),
            previous,
        )
        val row = merged.single()
        assertEquals("库里的预览", row.preview)
        assertEquals(MailType.Notice, row.type)
        assertTrue(row.bodyReady)
    }

    @Test
    fun dropsPendingOnceDatabaseCatchesUpAndKeepsSessionFields() {
        val pending = linkedMapOf(
            "a" to LocalFlags(unread = false, archived = false, locallyDeleted = false),
        )
        val previous = listOf(
            mail(id = "a", unread = false, type = MailType.Verify, tagNames = listOf("登录")),
        )
        val merged = mergeSnapshot(
            pending,
            listOf(mail(id = "a", unread = false, subject = "新主题")),
            previous,
        )
        val row = merged.single()
        assertEquals("新主题", row.subject)
        assertEquals(MailType.Verify, row.type)
        assertEquals(listOf("登录"), row.tagNames)
        assertTrue(pending.isEmpty())
    }

    @Test
    fun dropsPendingMissingFromDatabase() {
        val pending = linkedMapOf(
            "gone" to LocalFlags(unread = true, archived = false, locallyDeleted = true),
        )
        val merged = mergeSnapshot(pending, listOf(mail(id = "a")), emptyList())
        assertEquals(listOf("a"), merged.map { it.id })
        assertTrue(pending.isEmpty())
    }

    @Test
    fun emptyPendingWithoutPreviousReturnsTheDatabaseList() {
        val fromDb = listOf(mail(id = "a"))
        assertSame(fromDb, mergeSnapshot(linkedMapOf(), fromDb, emptyList()))
    }

    @Test
    fun matchingSessionFieldsKeepTheDatabaseList() {
        val fromDb = listOf(mail(id = "a", type = MailType.Notice, tagNames = listOf("课程")))
        val previous = listOf(mail(id = "a", type = MailType.Notice, tagNames = listOf("课程")))
        assertSame(fromDb, mergeSnapshot(linkedMapOf(), fromDb, previous))
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
    type: MailType? = null,
    tagNames: List<String> = emptyList(),
    preview: String = "",
    body: String = "",
    bodyReady: Boolean = false,
): Mail = Mail(
    id = id,
    accountId = "a1",
    remoteFolder = RemoteFolder.Inbox,
    archived = archived,
    locallyDeleted = locallyDeleted,
    fromName = "发件人",
    subject = subject,
    preview = preview,
    body = body,
    timeMillis = 0L,
    unread = unread,
    type = type,
    tagNames = tagNames,
    bodyReady = bodyReady,
)
