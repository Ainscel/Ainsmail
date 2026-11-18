package com.ainscel.ainsmail.security

/**
 * 账号写入与登录失败的共享原因。
 * UI 只做文案映射；设置与登录共用，避免 settings 依赖 ui.account。
 */
enum class AccountError {
    NotConfigured,
    TimedOut,
    Denied,
    Rejected,
    Duplicate,
    InvalidAddress,
    BlankCode,
    BlankName,
    Unreadable,
    BadImage,
}

/** 成功写入没有对应错误。调用方用 null 走成功分支。 */
fun AccountWrite.toAccountError(): AccountError? = when (this) {
    is AccountWrite.Saved -> null
    AccountWrite.Duplicate -> AccountError.Duplicate
    AccountWrite.InvalidAddress -> AccountError.InvalidAddress
    AccountWrite.BlankCode -> AccountError.BlankCode
    AccountWrite.BlankName -> AccountError.BlankName
    AccountWrite.Unreadable -> AccountError.Unreadable
    AccountWrite.BadImage -> AccountError.BadImage
    AccountWrite.Rejected -> AccountError.Rejected
}

fun GoogleSignInFailure.toAccountError(): AccountError = when (this) {
    GoogleSignInFailure.TimedOut -> AccountError.TimedOut
    GoogleSignInFailure.Denied -> AccountError.Denied
    GoogleSignInFailure.Rejected -> AccountError.Rejected
}
