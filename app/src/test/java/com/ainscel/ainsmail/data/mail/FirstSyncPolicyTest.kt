package com.ainscel.ainsmail.data.mail

import com.ainscel.ainsmail.model.RemoteFolder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FirstSyncPolicyTest {
    @Test
    fun belowCap_allowsHistory() {
        assertTrue(shouldFillHistory(totalCount = 20))
        assertEquals(30, historyFillBudget(totalCount = 20))
    }

    @Test
    fun atCap_skipsHistory() {
        assertFalse(shouldFillHistory(totalCount = 50))
        assertFalse(shouldFillHistory(totalCount = 80))
        assertEquals(0, historyFillBudget(totalCount = 50))
    }
}

class MailHeaderMappingTest {
    @Test
    fun mailIdKeepsServerFolderBetweenColons() {
        val id = mailId("11111111-1111-1111-1111-111111111111", RemoteFolder.Inbox, 5L)
        assertEquals("Inbox", serverFolderInMailId(id))
    }

    @Test
    fun malformedIdHasNoServerFolder() {
        assertNull(serverFolderInMailId("no-colons"))
        assertNull(serverFolderInMailId("a:b"))
    }

    @Test
    fun knownFolderKeepsEnvelope() {
        val mail = header(remoteFolder = "Inbox", subject = "你好").toMail()
        assertEquals(RemoteFolder.Inbox, mail?.remoteFolder)
        assertEquals("你好", mail?.subject)
    }

    @Test
    fun unknownFolderIsSkipped() {
        assertNull(header(remoteFolder = "Nope").toMail())
    }
}

private fun header(remoteFolder: String, subject: String = "主题"): MailHeaderEntity = MailHeaderEntity(
    id = "a:$remoteFolder:1",
    accountId = "a",
    remoteFolder = remoteFolder,
    uid = 1L,
    uidValidity = 1L,
    fromName = "发件人",
    fromAddress = "a@b.c",
    toCsv = "",
    ccCsv = "",
    subject = subject,
    timeMillis = 0L,
    unread = true,
    hasAttachment = false,
    archived = false,
)
