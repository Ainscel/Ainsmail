package com.ainscel.ainsmail.ui.mailbox

import androidx.annotation.StringRes
import com.ainscel.ainsmail.R
import com.ainscel.ainsmail.model.Folder
import com.ainscel.ainsmail.model.MailType
import com.ainscel.ainsmail.model.ReadFilter
import com.ainscel.ainsmail.model.TagPool

@StringRes
fun Folder.title(): Int = when (this) {
    Folder.Inbox -> R.string.folder_inbox
    Folder.Drafts -> R.string.folder_drafts
    Folder.Archive -> R.string.folder_archive
    Folder.Sent -> R.string.folder_sent
    Folder.Trash -> R.string.folder_trash
}

@StringRes
fun MailType.title(): Int = when (this) {
    MailType.Important -> R.string.type_important
    MailType.Verify -> R.string.type_verify
    MailType.Notice -> R.string.type_notice
    MailType.Promo -> R.string.type_promo
    MailType.Spam -> R.string.type_spam
}

@StringRes
fun TagPool.title(): Int = when (this) {
    TagPool.Active -> R.string.pool_active
    TagPool.Stale -> R.string.pool_stale
    TagPool.Pending -> R.string.pool_pending
}

@StringRes
fun ReadFilter.title(): Int = when (this) {
    ReadFilter.Any -> R.string.filter_any
    ReadFilter.Read -> R.string.filter_read
    ReadFilter.Unread -> R.string.filter_unread
}
