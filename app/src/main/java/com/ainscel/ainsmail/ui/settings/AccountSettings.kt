package com.ainscel.ainsmail.ui.settings

import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ainscel.ainsmail.R
import com.ainscel.ainsmail.model.Account
import com.ainscel.ainsmail.ui.accountErrorText
import com.ainscel.ainsmail.ui.rememberAccountImagePicker

@Composable
internal fun AccountSettings(
    state: AccountSettingsUiState,
    accounts: List<Account>,
    reauthAccountIds: Set<String>,
    onRename: (String, String) -> Unit,
    onPickAvatar: (String, Uri) -> Unit,
    onRemove: (String) -> Unit,
    onRelogin: (Account) -> Unit,
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pendingAvatar by rememberSaveable { mutableStateOf<String?>(null) }
    var renaming by rememberSaveable { mutableStateOf<String?>(null) }
    var removing by rememberSaveable { mutableStateOf<String?>(null) }
    val pickAvatar = rememberAccountImagePicker { uri ->
        val id = pendingAvatar
        pendingAvatar = null
        if (id != null) onPickAvatar(id, uri)
    }
    SettingsSectionFrame(title = title, onBack = onBack, modifier = modifier) {
        AccountSettingsList(
            state = state,
            accounts = accounts,
            reauthAccountIds = reauthAccountIds,
            onRename = { renaming = it },
            onAvatar = {
                pendingAvatar = it
                pickAvatar()
            },
            onRemove = { removing = it },
            onRelogin = onRelogin,
        )
    }
    renaming?.let { id ->
        val account = accounts.find { it.id == id }
        if (account != null) {
            RenameAccountDialog(
                initial = account.name,
                onDismiss = { renaming = null },
                onSave = {
                    onRename(id, it)
                    renaming = null
                },
            )
        }
    }
    removing?.let { id ->
        ConfirmRemoveDialog(
            onDismiss = { removing = null },
            onConfirm = {
                onRemove(id)
                removing = null
            },
        )
    }
}

@Composable
private fun AccountSettingsList(
    state: AccountSettingsUiState,
    accounts: List<Account>,
    reauthAccountIds: Set<String>,
    onRename: (String) -> Unit,
    onAvatar: (String) -> Unit,
    onRemove: (String) -> Unit,
    onRelogin: (Account) -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        when {
            accounts.isNotEmpty() -> accounts.forEach { account ->
                AccountSettingsRow(
                    account = account,
                    needsReauth = account.id in reauthAccountIds,
                    onRename = { onRename(account.id) },
                    onAvatar = { onAvatar(account.id) },
                    onRemove = { onRemove(account.id) },
                    onRelogin = { onRelogin(account) },
                )
            }
            state.unreadable -> Text(
                text = stringResource(R.string.settings_secrets_unreadable),
                color = MaterialTheme.colorScheme.error,
            )
            state.loaded -> Text(stringResource(R.string.settings_account_empty))
        }
        if (state.failure != null) {
            Text(
                text = accountErrorText(state.failure),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

@Composable
private fun AccountSettingsRow(
    account: Account,
    needsReauth: Boolean,
    onRename: () -> Unit,
    onAvatar: () -> Unit,
    onRemove: () -> Unit,
    onRelogin: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Text(account.name, style = MaterialTheme.typography.titleMedium)
        Text(account.address, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (needsReauth) {
            Text(
                text = stringResource(R.string.settings_account_reauth),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 4.dp),
            )
            TextButton(onClick = onRelogin) {
                Text(stringResource(R.string.settings_account_relogin))
            }
        }
        TextButton(onClick = onRename) { Text(stringResource(R.string.settings_account_rename)) }
        TextButton(onClick = onAvatar) { Text(stringResource(R.string.settings_account_avatar)) }
        TextButton(onClick = onRemove) { Text(stringResource(R.string.settings_account_delete)) }
    }
}

@Composable
private fun RenameAccountDialog(
    initial: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onSave(name) }) {
                Text(stringResource(R.string.account_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.list_cancel)) }
        },
        title = { Text(stringResource(R.string.settings_account_rename)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.account_qq_name)) },
                singleLine = true,
            )
        },
    )
}

@Composable
private fun ConfirmRemoveDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.settings_account_delete)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.list_cancel)) }
        },
        title = { Text(stringResource(R.string.settings_account_delete)) },
        text = { Text(stringResource(R.string.settings_account_delete_body)) },
    )
}
