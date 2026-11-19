package com.ainscel.ainsmail.ui.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ainscel.ainsmail.R
import com.ainscel.ainsmail.ui.SecretVisibilityToggle
import com.ainscel.ainsmail.ui.secretVisual

/**
 * 密钥可以长按粘贴。
 * Password 键盘类型会关掉文字选区，系统的粘贴菜单不会出现。
 * 隐藏字符仍由 PasswordVisualTransformation 负责。
 */
internal val SecretKeyboard = KeyboardOptions(
    keyboardType = KeyboardType.Ascii,
    autoCorrectEnabled = false,
)

@Composable
internal fun DeepSeekFields(
    state: SecretsUiState,
    onEdit: (SecretsEdit) -> Unit,
) {
    SectionTitle(stringResource(R.string.settings_deepseek_section))
    OutlinedTextField(
        value = state.apiKey,
        onValueChange = { onEdit(SecretsEdit.ApiKey(it)) },
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        label = { Text(stringResource(R.string.settings_deepseek_key)) },
        singleLine = true,
        visualTransformation = secretVisual(state.revealKey),
        keyboardOptions = SecretKeyboard,
        trailingIcon = { SecretVisibilityToggle(revealed = state.revealKey, onToggle = { onEdit(SecretsEdit.ToggleKey) }) },
    )
    OutlinedTextField(
        value = state.model,
        onValueChange = { onEdit(SecretsEdit.Model(it)) },
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        label = { Text(stringResource(R.string.settings_deepseek_model)) },
        supportingText = { Text(stringResource(R.string.settings_deepseek_model_hint)) },
        singleLine = true,
    )
}

@Composable
internal fun GoogleFields(
    state: SecretsUiState,
    onEdit: (SecretsEdit) -> Unit,
) {
    SectionTitle(stringResource(R.string.settings_google_section))
    OutlinedTextField(
        value = state.clientId,
        onValueChange = { onEdit(SecretsEdit.ClientId(it)) },
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        label = { Text(stringResource(R.string.settings_client_id)) },
        supportingText = { Text(stringResource(R.string.settings_client_id_hint)) },
        singleLine = true,
    )
    Text(
        text = stringResource(R.string.settings_google_help),
        modifier = Modifier.padding(top = 12.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
internal fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 8.dp),
    )
}
