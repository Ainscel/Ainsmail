package com.ainscel.ainsmail.data.mail

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BodyFilesTest {
    @Test
    fun pathReplacesColonsInTheMailId() {
        assertEquals(
            "mail/account-1/Inbox/account-1_Inbox_9",
            bodyRelativeDir("account-1", "Inbox", "account-1:Inbox:9"),
        )
    }

    @Test
    fun writeThenReplaceKeepsBothFiles() {
        val root = newRoot()
        val files = BodyFiles(root)
        val relative = bodyRelativeDir("account-1", "Inbox", "account-1:Inbox:9")
        files.write(relative, "<p>旧</p>", "旧")
        files.write(relative, "<p>新</p>", "新正文")
        assertEquals("新正文", files.readMarkdown(relative))
        assertEquals("<p>新</p>", File(root, "$relative/$BODY_HTML_NAME").readText())
        assertFalse(File(root, "$relative.staging").exists())
        assertFalse(File(root, "$relative.backup").exists())
        files.deleteAccount("account-1")
        assertNull(files.readMarkdown(relative))
        assertFalse(File(root, "mail/account-1").exists())
    }

    @Test
    fun deleteFolderLeavesTheOtherFolder() {
        val root = newRoot()
        val files = BodyFiles(root)
        val inbox = bodyRelativeDir("account-1", "Inbox", "account-1:Inbox:1")
        val sent = bodyRelativeDir("account-1", "Sent", "account-1:Sent:2")
        files.write(inbox, "<p>收件</p>", "收件")
        files.write(sent, "<p>已发送</p>", "已发送")
        files.deleteFolder("account-1", "Inbox")
        assertNull(files.readMarkdown(inbox))
        assertEquals("已发送", files.readMarkdown(sent))
        assertTrue(File(root, "mail/account-1/Sent").isDirectory)
    }

    private fun newRoot(): File {
        val root = File(System.getProperty("java.io.tmpdir"), "ainsmail-body-" + System.nanoTime())
        assertTrue(root.mkdirs())
        return root
    }
}
