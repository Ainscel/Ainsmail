package com.ainscel.ainsmail.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.ainscel.ainsmail.R
import com.ainscel.ainsmail.security.AccountError

/** 登录和设置共用的失败文案。 */
@Composable
internal fun accountErrorText(error: AccountError): String = when (error) {
    AccountError.NotConfigured -> stringResource(R.string.account_gmail_need_client)
    AccountError.TimedOut -> stringResource(R.string.account_gmail_timeout)
    AccountError.Denied -> stringResource(R.string.account_gmail_denied)
    AccountError.Rejected -> stringResource(R.string.account_gmail_rejected)
    AccountError.Duplicate -> stringResource(R.string.account_duplicate)
    AccountError.InvalidAddress -> stringResource(R.string.account_invalid_address)
    AccountError.BlankCode -> stringResource(R.string.account_blank_code)
    AccountError.BlankName -> stringResource(R.string.account_blank_name)
    AccountError.Unreadable -> stringResource(R.string.settings_secrets_unreadable)
    AccountError.BadImage -> stringResource(R.string.account_bad_image)
}
