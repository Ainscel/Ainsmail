package com.ainscel.ainsmail.data.imap

import jakarta.mail.FetchProfile
import jakarta.mail.Flags
import jakarta.mail.Folder
import jakarta.mail.Message
import jakarta.mail.Store
import jakarta.mail.UIDFolder
import jakarta.mail.internet.InternetAddress
import jakarta.mail.search.AndTerm
import jakarta.mail.search.ComparisonTerm
import jakarta.mail.search.ReceivedDateTerm
import com.ainscel.ainsmail.data.mail.nextUid
import com.ainscel.ainsmail.data.mail.normalizeMessageId
import java.util.Date

/** IMAP 信封。不含账号、板块和本机标记，落库时再套上。 */
data class RemoteHeader(
    val uid: Long,
    val uidValidity: Long,
    val fromName: String,
    val fromAddress: String,
    val toCsv: String,
    val ccCsv: String,
    val subject: String,
    val timeMillis: Long,
    val unread: Boolean,
    /** 列表还没显示附件。首拉不取正文结构，这里保持 false。 */
    val hasAttachment: Boolean,
    /** 尖括号已去掉。没有这封信的 Message-ID 时是空字符串。 */
    val messageId: String = "",
)

data class FetchedHeaders(
    val uidValidity: Long,
    val headers: List<RemoteHeader>,
)

/**
 * 按收到日期拉信头。只取信封 / 标志 / UID，不下载正文。
 * [limit] 非空时按 UID 从新到旧截断，只再取这几封的信封。
 */
fun fetchHeadersSince(
    store: Store,
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
        val uidFolder = mailFolder as? UIDFolder
            ?: return FetchedHeaders(uidValidity = 0L, headers = emptyList())
        val uidValidity = uidFolder.uidValidity
        val sinceTerm = ReceivedDateTerm(ComparisonTerm.GE, Date(sinceEpochMs))
        val term = if (untilEpochMs == null) {
            sinceTerm
        } else {
            AndTerm(sinceTerm, ReceivedDateTerm(ComparisonTerm.LE, Date(untilEpochMs)))
        }
        val found = mailFolder.search(term)
        if (found.isEmpty()) return FetchedHeaders(uidValidity = uidValidity, headers = emptyList())
        val chosen = if (limit != null && found.size > limit) {
            newestByUid(mailFolder, uidFolder, found, limit)
        } else {
            found
        }
        if (chosen.isEmpty()) return FetchedHeaders(uidValidity = uidValidity, headers = emptyList())
        return readHeaders(mailFolder, uidFolder, uidValidity, chosen)
    } finally {
        if (mailFolder.isOpen) mailFolder.close(false)
    }
}

/**
 * 只取比 [highestUid] 更新的信头。没有新 UID 时不拉信封。
 * 文件夹需已打开。调用方决定何时关闭，方便 IDLE 复用这条连接。
 */
fun readHeadersAfterUid(folder: Folder, highestUid: Long): FetchedHeaders {
    val uidFolder = folder as? UIDFolder ?: return FetchedHeaders(uidValidity = 0L, headers = emptyList())
    val uidValidity = uidFolder.uidValidity
    if (folder.messageCount == 0) return FetchedHeaders(uidValidity = uidValidity, headers = emptyList())
    val messages = uidFolder.getMessagesByUID(nextUid(highestUid), UIDFolder.LASTUID)
    if (messages.isEmpty()) return FetchedHeaders(uidValidity = uidValidity, headers = emptyList())
    return readHeaders(folder, uidFolder, uidValidity, messages)
}

/** 按 UID 打开文件夹，取新信头，然后关掉。不改服务器标志。 */
fun fetchHeadersAfterUid(store: Store, folderPath: String, highestUid: Long): FetchedHeaders {
    val mailFolder = store.getFolder(folderPath)
    if (!mailFolder.exists()) return FetchedHeaders(uidValidity = 0L, headers = emptyList())
    mailFolder.open(Folder.READ_ONLY)
    return try {
        readHeadersAfterUid(mailFolder, highestUid)
    } finally {
        if (mailFolder.isOpen) mailFolder.close(false)
    }
}

/** 把这些 UID 标成 \\Seen。文件夹已打开时直接写，不再重开。 */
fun markSeen(folder: Folder, uids: List<Long>) {
    if (uids.isEmpty()) return
    val uidFolder = folder as? UIDFolder ?: return
    val openedHere = !folder.isOpen
    if (openedHere) folder.open(Folder.READ_WRITE)
    try {
        if (folder.mode == Folder.READ_ONLY) return
        val messages = uids.mapNotNull { uid -> uidFolder.getMessageByUID(uid) }.toTypedArray()
        if (messages.isEmpty()) return
        folder.setFlags(messages, Flags(Flags.Flag.SEEN), true)
    } finally {
        if (openedHere && folder.isOpen) folder.close(false)
    }
}

/** 另开一条已读回写。不移动、不删除。 */
fun storeSeen(store: Store, folderPath: String, uids: List<Long>) {
    if (uids.isEmpty()) return
    val folder = store.getFolder(folderPath)
    if (!folder.exists()) return
    folder.open(Folder.READ_WRITE)
    try {
        markSeen(folder, uids)
    } finally {
        if (folder.isOpen) folder.close(false)
    }
}

/** 先只取 UID，留下最新的 [limit] 封，避免把整个窗口的信封都下载下来。 */
private fun newestByUid(
    mailFolder: Folder,
    uidFolder: UIDFolder,
    found: Array<Message>,
    limit: Int,
): Array<Message> {
    mailFolder.fetch(found, FetchProfile().apply { add(UIDFolder.FetchProfileItem.UID) })
    return found.mapNotNull { message ->
        val uid = uidFolder.getUID(message)
        if (uid <= 0L) null else uid to message
    }.sortedByDescending { it.first }
        .take(limit)
        .map { it.second }
        .toTypedArray()
}

private fun readHeaders(
    folder: Folder,
    uidFolder: UIDFolder,
    uidValidity: Long,
    messages: Array<Message>,
): FetchedHeaders {
    folder.fetch(messages, envelopeProfile())
    val headers = messages.mapNotNull { message ->
        val uid = uidFolder.getUID(message)
        if (uid <= 0L) return@mapNotNull null
        toHeader(uid, uidValidity, message)
    }
    return FetchedHeaders(uidValidity = uidValidity, headers = headers)
}

private fun envelopeProfile(): FetchProfile = FetchProfile().apply {
    add(FetchProfile.Item.ENVELOPE)
    add(FetchProfile.Item.FLAGS)
    add(UIDFolder.FetchProfileItem.UID)
    add("Message-ID")
}

private fun toHeader(uid: Long, uidValidity: Long, message: Message): RemoteHeader {
    val from = firstAddress(message.from)
    return RemoteHeader(
        uid = uid,
        uidValidity = uidValidity,
        fromName = from?.personal?.trim().orEmpty(),
        fromAddress = from?.address.orEmpty(),
        toCsv = addressCsv(message.getRecipients(Message.RecipientType.TO)),
        ccCsv = addressCsv(message.getRecipients(Message.RecipientType.CC)),
        subject = message.subject.orEmpty(),
        timeMillis = messageTime(message),
        unread = !message.isSet(Flags.Flag.SEEN),
        hasAttachment = false,
        messageId = normalizeMessageId(message.getHeader("Message-ID")?.firstOrNull()),
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
