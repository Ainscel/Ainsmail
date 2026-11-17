package com.ainscel.ainsmail.model

/** 第一版只有 QQ（含 Foxmail）和 Gmail。以后加服务商时在这里加一项。 */
enum class Provider {
    Qq,
    Gmail,
}

/** 服务器上的文件夹。存档不是其中一种，它是本机标记。 */
enum class RemoteFolder {
    Inbox,
    Drafts,
    Sent,
    Trash,
}

/** 左侧遮罩里的板块。和服务器文件夹不是同一件事。 */
enum class Section {
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

/** 全部账号，或某一个账号。自建聚合等有了数据再加第三支。 */
sealed interface MailboxScope {
    data object All : MailboxScope
    data class Account(val id: String) : MailboxScope
}

data class Account(
    val id: String,
    val name: String,
    val address: String,
    val provider: Provider,
    /** 相对 files 目录的头像文件。没有就用显示名画纯色圆。 */
    val avatarFile: String? = null,
)

/**
 * 列表和正文暂时共用这一份。
 * [body] 只是内存里的占位正文，列表投影不准读它。清洗后的 HTML 和 Markdown 以后落文件。
 */
data class Mail(
    val id: String,
    val accountId: String,
    val remoteFolder: RemoteFolder,
    val archived: Boolean,
    /** 本机删除。不改 [remoteFolder]，服务器夹要等真正写回再动。 */
    val locallyDeleted: Boolean = false,
    val fromName: String,
    val subject: String,
    val preview: String,
    val body: String,
    val timeMillis: Long,
    val unread: Boolean,
    val type: MailType?,
    val tagNames: List<String>,
)

fun Mail.inScope(scope: MailboxScope): Boolean = when (scope) {
    MailboxScope.All -> true
    is MailboxScope.Account -> accountId == scope.id
}

/** 存档板块只看收件箱里被本机归档的信。别的文件夹不因为这个标记改归属。 */
fun Mail.inSection(section: Section): Boolean = when (section) {
    Section.Inbox -> remoteFolder == RemoteFolder.Inbox && !archived && !locallyDeleted
    Section.Drafts -> remoteFolder == RemoteFolder.Drafts && !locallyDeleted
    Section.Archive -> remoteFolder == RemoteFolder.Inbox && archived && !locallyDeleted
    Section.Sent -> remoteFolder == RemoteFolder.Sent && !locallyDeleted
    Section.Trash -> locallyDeleted || remoteFolder == RemoteFolder.Trash
}
