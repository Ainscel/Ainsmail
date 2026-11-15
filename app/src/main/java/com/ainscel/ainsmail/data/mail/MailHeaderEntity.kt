package com.ainscel.ainsmail.data.mail

import androidx.room.Entity
import androidx.room.Index
import com.ainscel.ainsmail.data.imap.RemoteHeader
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
    val typeName: String? = null,
    val tagNamesCsv: String = "",
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

fun mailId(accountId: String, folder: RemoteFolder, uid: Long): String =
    "$accountId:${folder.name}:$uid"

fun RemoteHeader.toEntity(accountId: String, folder: RemoteFolder): MailHeaderEntity = MailHeaderEntity(
    id = mailId(accountId, folder, uid),
    accountId = accountId,
    remoteFolder = folder.name,
    uid = uid,
    uidValidity = uidValidity,
    fromName = fromName,
    fromAddress = fromAddress,
    toCsv = toCsv,
    ccCsv = ccCsv,
    subject = subject,
    timeMillis = timeMillis,
    unread = unread,
    hasAttachment = hasAttachment,
    archived = false,
)

fun MailHeaderEntity.toMail(): Mail = Mail(
    id = id,
    accountId = accountId,
    remoteFolder = RemoteFolder.valueOf(remoteFolder),
    archived = archived,
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
