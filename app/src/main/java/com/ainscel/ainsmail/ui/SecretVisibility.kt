package com.ainscel.ainsmail.ui

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.ainscel.ainsmail.R

/** 授权码和密钥共用的显示/隐藏。 */
@Composable
internal fun SecretVisibilityToggle(revealed: Boolean, onToggle: () -> Unit) {
    TextButton(onClick = onToggle) {
        Text(stringResource(if (revealed) R.string.settings_hide else R.string.settings_show))
    }
}

internal fun secretVisual(revealed: Boolean): VisualTransformation =
    if (revealed) VisualTransformation.None else PasswordVisualTransformation()
