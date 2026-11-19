package com.ainscel.ainsmail.ui.nav

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.ainscel.ainsmail.R
import com.ainscel.ainsmail.ui.shell.OverlayController
import kotlinx.coroutines.CoroutineScope

@Composable
internal fun OverlayBackHandler(
    overlay: OverlayController,
    scope: CoroutineScope,
) {
    BackHandler(enabled = overlay.obstructsBack) {
        overlay.close(scope)
    }
}

@Composable
internal fun AddAccountDialog(
    onDismiss: () -> Unit,
    onLogin: () -> Unit,
    onGroup: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.list_cancel))
            }
        },
        title = { Text(stringResource(R.string.account_add)) },
        text = {
            Column {
                TextButton(onClick = onLogin) {
                    Text(stringResource(R.string.account_add_login))
                }
                TextButton(onClick = onGroup) {
                    Text(stringResource(R.string.account_add_group))
                }
            }
        },
    )
}
