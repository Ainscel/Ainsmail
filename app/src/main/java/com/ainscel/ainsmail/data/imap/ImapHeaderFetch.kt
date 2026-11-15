package com.ainscel.ainsmail.data.imap

import com.ainscel.ainsmail.data.mail.MailHeaderEntity
import com.ainscel.ainsmail.data.mail.mailId
import com.ainscel.ainsmail.model.RemoteFolder
import jakarta.mail.FetchProfile
import jakarta.mail.Flags
import jakarta.mail.Folder
import jakarta.mail.Message
import jakarta.mail.UIDFolder
import jakarta.mail.internet.InternetAddress
import jakarta.mail.search.AndTerm
import jakarta.mail.search.ComparisonTerm
import jakarta.mail.search.ReceivedDateTerm
import jakarta.mail.search.SentDateTerm
import java.util.Date
import org.eclipse.angus.mail.imap.IMAPFolder

data class FetchedHeaders(
    val uidValidity: Long,
    val headers: List<MailHeaderEntity>,
)

/**
 * 按时间窗口拉信头。只取信封 / 标志 / UID，不下载正文。
 * [limit] 非空时从新到旧截断。
 */
fun fetchHeadersSince(
    store: jakarta.mail.Store,
    accountId: String,
    folder: RemoteFolder,
    folderPath: String,
    sinceEpochMs: Long,
    untilEpochMs: Long? = null,
    limit: Int? = null,
): FetchedHeaders {
    val mailFolder = store.getFolder(folderPath)
    if (!mailFolder.exists()) {
        return FetchedHeaders(uidValidity = 0L, headers = emptyList())
    }
    mailFolder.open(Folder.READ_ONLY)
    try {
        val uidValidity = (mailFolder as? UIDFolder)?.uidValidity ?: 0L
        val since = Date(sinceEpochMs)
        val until = untilEpochMs?.let { Date(it) }
        val receivedSince = ReceivedDateTerm(ComparisonTerm.GE, since)
        val sentSince = SentDateTerm(ComparisonTerm.GE, since)
        val sinceTerm = jakarta.mail.search.OrTerm(receivedSince, sentSince)
        val term = if (until == null) {
            sinceTerm
        } else {
            val receivedUntil = ReceivedDateTerm(ComparisonTerm.LE, until)
            val sentUntil = SentDateTerm(ComparisonTerm.LE, until)
            AndTerm(sinceTerm, jakarta.mail.search.OrTerm(receivedUntil, sentUntil))
        }
        val found = mailFolder.search(term)
        if (found.isNotEmpty()) {
            val profile = FetchProfile().apply {
                add(FetchProfile.Item.ENVELOPE)
                add(FetchProfile.Item.FLAGS)
                add(FetchProfile.Item.CONTENT_INFO)
                add(UIDFolder.FetchProfileItem.UID)
            }
            mailFolder.fetch(found, profile)
        }
        var messages = found.sortedByDescending { messageTime(it) }
        if (limit != null && messages.size > limit) {
            messages = messages.take(limit)
        }
        val uidFolder = mailFolder as? UIDFolder
        val headers = messages.mapNotNull { message ->
            val uid = uidFolder?.getUID(message) ?: return@mapNotNull null
            if (uid <= 0L) return@mapNotNull null
            toEntity(accountId, folder, uid, uidValidity, message)
        }
        return FetchedHeaders(uidValidity = uidValidity, headers = headers)
    } finally {
        if (mailFolder.isOpen) mailFolder.close(false)
    }
}

fun readUidValidity(store: jakarta.mail.Store, folderPath: String): Long {
    val mailFolder = store.getFolder(folderPath)
    if (!mailFolder.exists()) return 0L
    mailFolder.open(Folder.READ_ONLY)
    return try {
        (mailFolder as? UIDFolder)?.uidValidity ?: 0L
    } finally {
        if (mailFolder.isOpen) mailFolder.close(false)
    }
}

private fun toEntity(
    accountId: String,
    folder: RemoteFolder,
    uid: Long,
    uidValidity: Long,
    message: Message,
): MailHeaderEntity {
    val from = firstAddress(message.from)
    return MailHeaderEntity(
        id = mailId(accountId, folder, uid),
        accountId = accountId,
        remoteFolder = folder.name,
        uid = uid,
        uidValidity = uidValidity,
        fromName = from?.personal?.trim().orEmpty().ifBlank {
            from?.address?.substringBefore('@').orEmpty()
        },
        fromAddress = from?.address.orEmpty(),
        toCsv = addressCsv(message.getRecipients(Message.RecipientType.TO)),
        ccCsv = addressCsv(message.getRecipients(Message.RecipientType.CC)),
        subject = message.subject.orEmpty(),
        timeMillis = messageTime(message),
        unread = !message.isSet(Flags.Flag.SEEN),
        hasAttachment = messageHasAttachment(message),
        archived = false,
    )
}

private fun messageTime(message: Message): Long {
    val received = message.receivedDate?.time
    val sent = message.sentDate?.time
    return received ?: sent ?: 0L
}

private fun firstAddress(addresses: Array<jakarta.mail.Address>?): InternetAddress? {
    val first = addresses?.firstOrNull() ?: return null
    return first as? InternetAddress ?: runCatching {
        InternetAddress.parse(first.toString(), false).firstOrNull()
    }.getOrNull()
}

private fun addressCsv(addresses: Array<jakarta.mail.Address>?): String {
    if (addresses.isNullOrEmpty()) return ""
    return addresses.mapNotNull { address ->
        (address as? InternetAddress)?.address ?: address.toString().takeIf { it.isNotBlank() }
    }.joinToString(",")
}

/** 只看 Content-Type，不打开正文流，避免首拉把整封信拉下来。 */
private fun messageHasAttachment(message: Message): Boolean {
    return try {
        val type = message.contentType?.lowercase().orEmpty()
        "multipart/mixed" in type || "multipart/related" in type
    } catch (cancelled: kotlinx.coroutines.CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        false
    }
}

/** 列出 SPECIAL-USE 时用；普通 Folder 没有 attributes 就当没有。 */
internal fun folderSpecialUses(folder: Folder): Array<String> {
    return try {
        (folder as? IMAPFolder)?.attributes ?: emptyArray()
    } catch (cancelled: kotlinx.coroutines.CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        emptyArray()
    }
}
