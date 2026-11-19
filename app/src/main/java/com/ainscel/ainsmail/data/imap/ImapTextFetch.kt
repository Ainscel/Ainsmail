package com.ainscel.ainsmail.data.imap

import jakarta.mail.Multipart
import jakarta.mail.Part
import jakarta.mail.Store
import jakarta.mail.UIDFolder
import jakarta.mail.internet.ContentType
import jakarta.mail.internet.MimeUtility
import java.io.InputStream
import java.nio.charset.Charset
import org.eclipse.angus.mail.imap.IMAPMessage

/** 一封信的文本部分。附件不在这里。 */
sealed interface TextBodyFetch {
    data class Found(val html: String?, val plain: String?) : TextBodyFetch
    data object Missing : TextBodyFetch
    data object NeedsReauth : TextBodyFetch
    data class Failed(val reason: String) : TextBodyFetch
}

private const val MAX_TEXT_CHARS = 500_000
private const val MAX_TEXT_BYTES = 1_500_000

/**
 * 只取非附件的 text/html 和 text/plain。
 * 多段结构先看 BODYSTRUCTURE，再按段取文本，不把附件读进文件。
 * 用 BODY.PEEK，避免这次读取把服务器上的未读改掉。
 */
fun fetchTextBody(store: Store, folderPath: String, uid: Long): TextBodyFetch {
    return try {
        val folder = store.getFolder(folderPath)
        if (!folder.exists()) return TextBodyFetch.Missing
        folder.open(jakarta.mail.Folder.READ_ONLY)
        try {
            val uidFolder = folder as? UIDFolder ?: return TextBodyFetch.Missing
            val message = uidFolder.getMessageByUID(uid) ?: return TextBodyFetch.Missing
            (message as? IMAPMessage)?.setPeek(true)
            val texts = CollectedText()
            collectText(message, texts)
            TextBodyFetch.Found(html = texts.html, plain = texts.plain)
        } finally {
            if (folder.isOpen) folder.close(false)
        }
    } catch (error: Exception) {
        val message = error.message.orEmpty()
        if (looksLikeAuthFailure(message)) {
            TextBodyFetch.NeedsReauth
        } else {
            TextBodyFetch.Failed(error.javaClass.simpleName + ": " + message.take(120))
        }
    }
}

private class CollectedText {
    var html: String? = null
    var plain: String? = null
}

private fun collectText(part: Part, texts: CollectedText) {
    if (texts.html != null) return
    if (isAttachment(part)) return
    val type = runCatching { part.contentType }.getOrNull().orEmpty().lowercase()
    when {
        type.startsWith("text/html") -> texts.html = readLimited(part)
        type.startsWith("text/plain") && texts.plain == null -> texts.plain = readLimited(part)
        type.startsWith("multipart/") -> {
            val multipart = part.content as? Multipart ?: return
            for (index in 0 until multipart.count) {
                collectText(multipart.getBodyPart(index), texts)
                if (texts.html != null) return
            }
        }
        type.startsWith("message/rfc822") -> {
            val nested = part.content as? Part ?: return
            collectText(nested, texts)
        }
    }
}

/** 有文件名，或标明是附件的部分，整段跳过。 */
private fun isAttachment(part: Part): Boolean {
    val disposition = runCatching { part.disposition }.getOrNull()?.lowercase().orEmpty()
    if (disposition == Part.ATTACHMENT) return true
    val name = runCatching { part.fileName }.getOrNull()
    return !name.isNullOrBlank()
}

private fun readLimited(part: Part): String {
    val size = runCatching { part.size }.getOrDefault(-1)
    val text = if (size > MAX_TEXT_BYTES) {
        part.inputStream.bufferedReader(partCharset(part)).use { reader ->
            val buffer = CharArray(MAX_TEXT_CHARS)
            val count = reader.read(buffer)
            if (count <= 0) "" else String(buffer, 0, count)
        }
    } else {
        when (val content = part.content) {
            is String -> content
            is InputStream -> content.bufferedReader(partCharset(part)).use { it.readText() }
            else -> ""
        }
    }
    return if (text.length <= MAX_TEXT_CHARS) text else text.substring(0, MAX_TEXT_CHARS)
}

private fun partCharset(part: Part): Charset {
    val name = runCatching {
        val raw = ContentType(part.contentType).getParameter("charset")
        if (raw.isNullOrBlank()) "UTF-8" else MimeUtility.javaCharset(raw)
    }.getOrDefault("UTF-8")
    return runCatching { Charset.forName(name) }.getOrDefault(Charsets.UTF_8)
}
