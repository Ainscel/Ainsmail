package com.ainscel.ainsmail.ui.account

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.ainscel.ainsmail.R
import com.ainscel.ainsmail.security.AccountError

@Composable
internal fun ProviderLoginDialog(
    gmailReady: Boolean,
    onQq: () -> Unit,
    onGmail: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.list_cancel))
            }
        },
        title = { Text(stringResource(R.string.account_add_login)) },
        text = {
            Column {
                TextButton(onClick = onQq) {
                    Text(stringResource(R.string.account_provider_qq))
                }
                TextButton(onClick = onGmail, enabled = gmailReady) {
                    Text(stringResource(R.string.account_provider_gmail))
                }
                if (!gmailReady) {
                    Text(stringResource(R.string.account_gmail_need_client))
                }
            }
        },
    )
}

@Composable
internal fun GmailWaitDialog(onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        confirmButton = {
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.account_gmail_cancel))
            }
        },
        title = { Text(stringResource(R.string.account_provider_gmail)) },
        text = { Text(stringResource(R.string.account_gmail_waiting)) },
    )
}

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
