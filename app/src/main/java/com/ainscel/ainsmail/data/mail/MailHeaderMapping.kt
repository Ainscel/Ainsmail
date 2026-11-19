package com.ainscel.ainsmail.data.mail

import com.ainscel.ainsmail.data.imap.RemoteHeader
import com.ainscel.ainsmail.model.RemoteFolder

fun mailId(accountId: String, folder: RemoteFolder, uid: Long): String =
    "$accountId:${folder.name}:$uid"

/** 账号 id 是 UUID，不含冒号。主键形如 `账号:夹名:UID`。 */
fun serverFolderInMailId(id: String): String? {
    val parts = id.split(':')
    if (parts.size != 3) return null
    return parts[1].takeIf { it.isNotEmpty() }
}

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
    messageId = messageId,
)
