package com.ainscel.ainsmail.ui.account

import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.ainscel.ainsmail.R
import com.ainscel.ainsmail.model.defaultDisplayName
import com.ainscel.ainsmail.security.AccountError
import com.ainscel.ainsmail.ui.accountErrorText
import com.ainscel.ainsmail.ui.rememberAccountImagePicker
import com.ainscel.ainsmail.ui.secretVisual
import com.ainscel.ainsmail.ui.SecretVisibilityToggle

@Composable
internal fun QqLoginDialog(
    initialAddress: String,
    initialName: String,
    saving: Boolean,
    failure: AccountError?,
    onSubmit: (address: String, name: String, code: String, avatar: Uri?) -> Unit,
    onDismiss: () -> Unit,
) {
    var address by rememberSaveable(initialAddress, initialName) { mutableStateOf(initialAddress) }
    var name by rememberSaveable(initialAddress, initialName) { mutableStateOf(initialName) }
    var nameTouched by rememberSaveable(initialAddress, initialName) { mutableStateOf(initialName.isNotEmpty()) }
    var code by remember { mutableStateOf("") }
    var reveal by rememberSaveable { mutableStateOf(false) }
    var avatar by rememberSaveable { mutableStateOf<String?>(null) }
    val pickAvatar = rememberAccountImagePicker { uri -> avatar = uri.toString() }
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        confirmButton = {
            TextButton(
                enabled = !saving,
                onClick = {
                    onSubmit(address, name, code, avatar?.let(Uri::parse))
                },
            ) {
                Text(stringResource(R.string.account_save))
            }
        },
        dismissButton = {
            TextButton(enabled = !saving, onClick = onDismiss) {
                Text(stringResource(R.string.list_cancel))
            }
        },
        title = { Text(stringResource(R.string.account_provider_qq)) },
        text = {
            QqLoginFields(
                address = address,
                name = name,
                code = code,
                reveal = reveal,
                avatarPicked = avatar != null,
                failure = failure,
                onAddress = {
                    address = it
                    if (!nameTouched) name = defaultDisplayName(it)
                },
                onName = {
                    nameTouched = true
                    name = it
                },
                onCode = { code = it },
                onToggleCode = { reveal = !reveal },
                onPickAvatar = pickAvatar,
            )
        },
    )
}

@Composable
private fun QqLoginFields(
    address: String,
    name: String,
    code: String,
    reveal: Boolean,
    avatarPicked: Boolean,
    failure: AccountError?,
    onAddress: (String) -> Unit,
    onName: (String) -> Unit,
    onCode: (String) -> Unit,
    onToggleCode: () -> Unit,
    onPickAvatar: () -> Unit,
) {
    Column(Modifier.verticalScroll(rememberScrollState())) {
        Text(stringResource(R.string.account_qq_help))
        OutlinedTextField(
            value = address,
            onValueChange = onAddress,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.account_qq_address)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        )
        OutlinedTextField(
            value = name,
            onValueChange = onName,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.account_qq_name)) },
            singleLine = true,
        )
        OutlinedTextField(
            value = code,
            onValueChange = onCode,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.account_qq_code)) },
            singleLine = true,
            visualTransformation = secretVisual(reveal),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false),
            trailingIcon = { SecretVisibilityToggle(revealed = reveal, onToggle = onToggleCode) },
        )
        TextButton(onClick = onPickAvatar) {
            Text(
                stringResource(
                    if (avatarPicked) R.string.account_avatar_picked else R.string.account_qq_pick_avatar,
                ),
            )
        }
        if (failure != null) {
            Text(accountErrorText(failure))
        }
    }
}
