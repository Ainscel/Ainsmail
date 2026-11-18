package com.ainscel.ainsmail.data.mail

import androidx.room.Entity
import androidx.room.Index
import com.ainscel.ainsmail.model.Mail
import com.ainscel.ainsmail.model.MailType
import com.ainscel.ainsmail.model.RemoteFolder

/**
 * 邮件头与本机标记。正文不进库。
 * [typeName] / [tagNamesCsv] 预留给分类任务，本阶段保持空。
 */
@Entity(
    tableName = "mail_headers",
    primaryKeys = ["id"],
    indices = [
        Index(value = ["accountId", "remoteFolder", "uid"], unique = true),
        Index(value = ["timeMillis"]),
    ],
)
data class MailHeaderEntity(
    val id: String,
    val accountId: String,
    val remoteFolder: String,
    val uid: Long,
    val uidValidity: Long,
    val fromName: String,
    val fromAddress: String,
    val toCsv: String,
    val ccCsv: String,
    val subject: String,
    val timeMillis: Long,
    val unread: Boolean,
    val hasAttachment: Boolean,
    val archived: Boolean,
    /** 本机删除。remoteFolder 始终是服务器夹。 */
    val locallyDeleted: Boolean = false,
    val typeName: String? = null,
    val tagNamesCsv: String = "",
    /** 信封里的 Message-ID。UIDVALIDITY 变了以后用来把本机标记套回新 UID。 */
    val messageId: String = "",
    /** 本机已读还没写成服务器的 \\Seen。同步冲突时不能覆盖。 */
    val seenPending: Boolean = false,
)

@Entity(
    tableName = "folder_sync_cursors",
    primaryKeys = ["accountId", "remoteFolder"],
)
data class FolderSyncCursorEntity(
    val accountId: String,
    val remoteFolder: String,
    val folderPath: String,
    val uidValidity: Long,
    val highestUid: Long,
    /** 该账号该夹的 7 天首拉是否完成。 */
    val recentSyncDone: Boolean,
    /** 该账号该夹的历史补齐是否完成（或因全局 50 封上限提前停）。 */
    val historySyncDone: Boolean,
)

/** 夹名对不上枚举时返回空，避免一行坏数据停掉整表订阅。 */
fun MailHeaderEntity.toMail(): Mail? {
    val folder = runCatching { RemoteFolder.valueOf(remoteFolder) }.getOrNull() ?: return null
    return Mail(
        id = id,
        accountId = accountId,
        remoteFolder = folder,
        archived = archived,
        locallyDeleted = locallyDeleted,
        fromName = fromName.ifBlank { fromAddress.substringBefore('@').ifBlank { fromAddress } },
        subject = subject,
        preview = "",
        body = "",
        timeMillis = timeMillis,
        unread = unread,
        type = typeName?.let { runCatching { MailType.valueOf(it) }.getOrNull() },
        tagNames = if (tagNamesCsv.isBlank()) {
            emptyList()
        } else {
            tagNamesCsv.split('\u001f').filter { it.isNotBlank() }
        },
    )
}
