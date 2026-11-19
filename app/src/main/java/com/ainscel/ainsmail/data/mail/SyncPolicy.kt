package com.ainscel.ainsmail.data.mail

import com.ainscel.ainsmail.model.RemoteFolder
import com.ainscel.ainsmail.model.Section

/** 前台没有 IDLE，或进程不在前台时，隔这么久再拉一次。 */
const val SYNC_POLL_MS = 15L * 60L * 1000L

private val vendorLeaves = setOf(
    "social",
    "promotions",
    "updates",
    "forums",
    "广告",
    "广告邮件",
)

/** 打开列表时要拉的服务器夹。存档没有服务器夹。 */
fun syncFolder(section: Section): RemoteFolder? = when (section) {
    Section.Inbox -> RemoteFolder.Inbox
    Section.Drafts -> RemoteFolder.Drafts
    Section.Sent -> RemoteFolder.Sent
    Section.Trash -> RemoteFolder.Trash
    Section.Archive -> null
}

/** IDLE 盯着的夹。正在看存档时仍盯收件箱，新信才能落库。 */
fun idleFolder(section: Section): RemoteFolder = syncFolder(section) ?: RemoteFolder.Inbox

fun nextUid(highestUid: Long): Long = highestUid + 1L

/** 游标还是 0 时不算变化，那是这条夹还没记下代际。 */
fun uidValidityChanged(stored: Long, remote: Long): Boolean =
    stored > 0L && remote > 0L && stored != remote

/**
 * 收件箱的 7 天和历史都完成，且没有别的夹还挂着，才不再走首拉。
 * 没有任何游标时仍要首拉。
 */
fun accountNeedsInitialSync(cursors: List<FolderSyncCursorEntity>): Boolean {
    val inbox = cursors.find { it.remoteFolder == RemoteFolder.Inbox.name }
    if (inbox == null || !inbox.recentSyncDone || !inbox.historySyncDone) return true
    return cursors.any { cursor -> !cursor.recentSyncDone || !cursor.historySyncDone }
}

/** Gmail 分类和 QQ 广告夹。只认夹名本身，不把「垃圾箱」这类正常夹名算进去。 */
fun isVendorCategoryFolder(fullName: String, simpleName: String): Boolean {
    return leafOf(simpleName) in vendorLeaves || leafOf(fullName) in vendorLeaves
}

fun normalizeMessageId(raw: String?): String {
    val value = raw?.trim().orEmpty()
    if (value.isEmpty()) return ""
    return value.removePrefix("<").removeSuffix(">").trim()
}

fun preservationFallback(fromAddress: String, subject: String, timeMillis: Long): String =
    fromAddress.trim().lowercase() + "\u001f" + subject.trim() + "\u001f" + timeMillis

/** UID 失效前从库里抄出来的本机标记。 */
data class PreservedLocal(
    val messageId: String,
    val fallbackKey: String,
    val uid: Long,
    val unread: Boolean,
    val archived: Boolean,
    val locallyDeleted: Boolean,
    val seenPending: Boolean,
    val row: MailHeaderEntity,
)

data class RebuiltHeaders(
    val synced: List<MailHeaderEntity>,
    val kept: List<MailHeaderEntity>,
)

fun MailHeaderEntity.toPreserved(): PreservedLocal = PreservedLocal(
    messageId = normalizeMessageId(messageId),
    fallbackKey = preservationFallback(fromAddress, subject, timeMillis),
    uid = uid,
    unread = unread,
    archived = archived,
    locallyDeleted = locallyDeleted,
    seenPending = seenPending,
    row = this,
)

/**
 * 新窗口里的信套回本机标记。
 * 窗口里没再出现、UID 也没被占用、并且归档或本机删除过的信留在 [RebuiltHeaders.kept]，UID 改成负数。
 */
fun reapplyPreserved(
    incoming: List<MailHeaderEntity>,
    preserved: List<PreservedLocal>,
): RebuiltHeaders {
    val byMessage = HashMap<String, PreservedLocal>()
    val byFallback = HashMap<String, PreservedLocal>()
    for (item in preserved) {
        if (item.messageId.isNotBlank()) byMessage.putIfAbsent(item.messageId, item)
        if (item.fallbackKey.isNotBlank()) byFallback.putIfAbsent(item.fallbackKey, item)
    }
    val used = HashSet<PreservedLocal>()
    val synced = incoming.map { row ->
        val match = row.messageId.takeIf { it.isNotBlank() }?.let(byMessage::get)
            ?: byFallback[preservationFallback(row.fromAddress, row.subject, row.timeMillis)]
        if (match == null || !used.add(match)) {
            row
        } else {
            row.copy(
                unread = match.unread,
                archived = match.archived,
                locallyDeleted = match.locallyDeleted,
                seenPending = match.seenPending,
            )
        }
    }
    val usedUids = HashSet<Long>(synced.size)
    for (row in synced) usedUids.add(row.uid)
    val kept = ArrayList<MailHeaderEntity>()
    for (item in preserved) {
        if (item in used) continue
        if (!item.archived && !item.locallyDeleted) continue
        if (item.uid in usedUids) continue
        kept.add(item.row.detachFromServerUid())
    }
    return RebuiltHeaders(synced = synced, kept = kept)
}

/**
 * 窗口外留下的信不再占用正 UID。
 * 新代际可能重新发出同一个数字，唯一索引会把归档标记套到另一封信上。
 */
fun MailHeaderEntity.detachFromServerUid(): MailHeaderEntity {
    if (uid <= 0L) return this
    val folder = runCatching { RemoteFolder.valueOf(remoteFolder) }.getOrNull() ?: return this
    val localUid = -uid
    return copy(id = mailId(accountId, folder, localUid), uid = localUid)
}

private fun leafOf(value: String): String =
    value.trim().substringAfterLast('/').substringAfterLast('.').lowercase()
