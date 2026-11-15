package com.ainscel.ainsmail.model

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import com.ainscel.ainsmail.R
import java.time.Instant
import java.time.ZoneId

enum class Folder(@param:StringRes val label: Int) {
    Inbox(R.string.folder_inbox),
    Drafts(R.string.folder_drafts),
    Archive(R.string.folder_archive),
    Sent(R.string.folder_sent),
    Trash(R.string.folder_trash),
}

enum class MailType(@param:StringRes val label: Int) {
    Important(R.string.type_important),
    Verify(R.string.type_verify),
    Notice(R.string.type_notice),
    Promo(R.string.type_promo),
    Spam(R.string.type_spam),
}

enum class TagPool(@param:StringRes val label: Int) {
    Active(R.string.pool_active),
    Stale(R.string.pool_stale),
    Pending(R.string.pool_pending),
}

enum class ReadFilter(@param:StringRes val label: Int) {
    Any(R.string.filter_any),
    Read(R.string.filter_read),
    Unread(R.string.filter_unread),
}

data class Account(
    val id: String,
    val name: String,
    val address: String,
)

data class MailTag(
    val name: String,
    val pool: TagPool,
    val type: MailType,
)

data class Mail(
    val id: String,
    val accountId: String,
    val folder: Folder,
    val fromName: String,
    val subject: String,
    val preview: String,
    val body: String,
    val timeMillis: Long,
    val unread: Boolean,
    val type: MailType?,
    val tags: List<MailTag>,
)

/** 名称哈希映射到稳重的色相，避免每一行都是荧光色。 */
fun avatarColor(name: String): Color {
    val hue = (name.hashCode() and 0x7fffffff) % 360
    return Color.hsl(hue.toFloat(), 0.38f, 0.46f)
}

/** 中文取前两个字，拉丁字母取前两个字母。空白不算。 */
fun avatarLabel(name: String): String {
    val compact = name.filter { !it.isWhitespace() }
    return compact.take(2)
}

fun formatMailTime(epochMillis: Long, nowMillis: Long = System.currentTimeMillis()): String {
    val zone = ZoneId.systemDefault()
    val moment = Instant.ofEpochMilli(epochMillis).atZone(zone)
    val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    return if (moment.toLocalDate() == today) {
        "%02d:%02d".format(moment.hour, moment.minute)
    } else {
        "${moment.monthValue}月${moment.dayOfMonth}日"
    }
}
