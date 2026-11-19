package com.ainscel.ainsmail.data.mail

import java.io.File

const val BODY_HTML_NAME = "body.html"
const val BODY_MARKDOWN_NAME = "body.md"

/** 邮件 id 含冒号。目录名换成下划线，真正路径以库里记下的为准。 */
fun bodyRelativeDir(accountId: String, folder: String, mailId: String): String {
    val safeId = mailId.replace(':', '_')
    return "mail/$accountId/$folder/$safeId"
}

/**
 * 正文文件。先写到旁边的临时目录，两份都成功再换上，失败时原来的文件还在。
 */
class BodyFiles(private val filesDir: File) {
    fun write(relativeDir: String, html: String, markdown: String) {
        val dest = directory(relativeDir)
        val staging = File(dest.parentFile, dest.name + ".staging")
        val backup = File(dest.parentFile, dest.name + ".backup")
        if (staging.exists()) staging.deleteRecursively()
        if (!staging.mkdirs()) error("mkdir ${staging.name}")
        try {
            File(staging, BODY_HTML_NAME).writeText(html)
            File(staging, BODY_MARKDOWN_NAME).writeText(markdown)
            if (backup.exists()) backup.deleteRecursively()
            if (dest.exists() && !dest.renameTo(backup)) {
                error("backup ${dest.name}")
            }
            if (!staging.renameTo(dest)) {
                if (backup.exists()) backup.renameTo(dest)
                error("replace ${dest.name}")
            }
            if (backup.exists()) backup.deleteRecursively()
        } catch (error: Exception) {
            if (staging.exists()) staging.deleteRecursively()
            throw error
        }
    }

    fun readMarkdown(relativeDir: String): String? {
        val file = File(directory(relativeDir), BODY_MARKDOWN_NAME)
        if (!file.isFile) return null
        return file.readText()
    }

    fun deleteAccount(accountId: String) {
        if (!safeSegment(accountId)) return
        File(filesDir, "mail/$accountId").deleteRecursively()
    }

    fun deleteFolder(accountId: String, folder: String) {
        if (!safeSegment(accountId) || !safeSegment(folder)) return
        File(filesDir, "mail/$accountId/$folder").deleteRecursively()
    }

    private fun directory(relativeDir: String): File {
        require(relativeDir.isNotBlank() && ".." !in relativeDir && !relativeDir.startsWith("/") && !relativeDir.startsWith("\\")) {
            "bad body path"
        }
        return File(filesDir, relativeDir)
    }
}

private fun safeSegment(value: String): Boolean {
    if (value.isBlank()) return false
    return ".." !in value && '/' !in value && '\\' !in value && ':' !in value
}
