package com.ainscel.ainsmail.ui.account

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.ainscel.ainsmail.R

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
