package com.ainscel.ainsmail.model

/** 第一版只有 QQ（含 Foxmail）和 Gmail。以后加服务商时在这里加一项。 */
enum class Provider {
    Qq,
    Gmail,
}

enum class Folder {
    Inbox,
    Drafts,
    Archive,
    Sent,
    Trash,
}

enum class MailType {
    Important,
    Verify,
    Notice,
    Promo,
    Spam,
}

enum class TagPool {
    Active,
    Stale,
    Pending,
}

enum class ReadFilter {
    Any,
    Read,
    Unread,
}

data class Account(
    val id: String,
    val name: String,
    val address: String,
    val provider: Provider,
)

/**
 * 标签词属于某一个类型下的一个池。
 * 模型可以跨类型沿用这个词，所以它的类型可以和所在邮件的类型不同。
 * 用户改邮件分类时，不改这里。
 */
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
