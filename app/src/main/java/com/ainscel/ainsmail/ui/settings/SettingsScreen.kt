package com.ainscel.ainsmail.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.ainscel.ainsmail.R
import com.ainscel.ainsmail.security.SecretStore
import com.ainscel.ainsmail.ui.detail.SplitFullscreenButton
import com.ainscel.ainsmail.ui.shell.LocalDetailHandlesBack
import com.ainscel.ainsmail.ui.shell.MailCapsuleHeight
import com.ainscel.ainsmail.ui.shell.MailChromeInset
import com.ainscel.ainsmail.ui.shell.holdWidthUntilSettled
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 某个设置栏目。窄屏整页，宽屏在分界线右侧。 */
@Composable
fun SettingsSectionPane(
    section: SettingsSection,
    store: SecretStore,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = stringResource(section.label)
    when (section) {
        SettingsSection.Secrets -> SecretsSettings(store = store, title = title, onBack = onBack, modifier = modifier)
        SettingsSection.Notifications,
        SettingsSection.Privacy,
        -> EmptySettingsSection(title = title, onBack = onBack, modifier = modifier)
    }
}

@Composable
private fun EmptySettingsSection(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsSectionFrame(title = title, onBack = onBack, modifier = modifier) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.settings_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 密钥可以长按粘贴。
 * Password 键盘类型会关掉文字选区，系统的粘贴菜单不会出现。
 * 隐藏字符仍由 PasswordVisualTransformation 负责。
 */
private val SecretKeyboard = KeyboardOptions(
    keyboardType = KeyboardType.Ascii,
    autoCorrectEnabled = false,
)

@Composable
private fun SecretsSettings(
    store: SecretStore,
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val savedMessage = stringResource(R.string.settings_saved)
    var loaded by rememberSaveable { mutableStateOf(false) }
    var apiKey by rememberSaveable { mutableStateOf("") }
    var model by rememberSaveable { mutableStateOf(SecretStore.DEFAULT_MODEL) }
    var clientId by rememberSaveable { mutableStateOf("") }
    var clientSecret by rememberSaveable { mutableStateOf("") }
    var revealKey by rememberSaveable { mutableStateOf(false) }
    var revealClientSecret by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(store) {
        if (loaded) return@LaunchedEffect
        val secrets = withContext(Dispatchers.IO) { store.read() }
        apiKey = secrets.deepSeekApiKey
        model = secrets.deepSeekModel
        clientId = secrets.googleClientId
        clientSecret = secrets.googleClientSecret
        loaded = true
    }

    SettingsSectionFrame(title = title, onBack = onBack, modifier = modifier) {
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                SectionTitle(stringResource(R.string.settings_deepseek_section))
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    label = { Text(stringResource(R.string.settings_deepseek_key)) },
                    singleLine = true,
                    visualTransformation = if (revealKey) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    keyboardOptions = SecretKeyboard,
                    trailingIcon = {
                        TextButton(onClick = { revealKey = !revealKey }) {
                            Text(
                                stringResource(
                                    if (revealKey) R.string.settings_hide else R.string.settings_show,
                                ),
                            )
                        }
                    },
                )
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    label = { Text(stringResource(R.string.settings_deepseek_model)) },
                    supportingText = { Text(stringResource(R.string.settings_deepseek_model_hint)) },
                    singleLine = true,
                )
                SectionTitle(stringResource(R.string.settings_google_section))
                OutlinedTextField(
                    value = clientId,
                    onValueChange = { clientId = it },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    label = { Text(stringResource(R.string.settings_client_id)) },
                    supportingText = { Text(stringResource(R.string.settings_client_id_hint)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = clientSecret,
                    onValueChange = { clientSecret = it },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    label = { Text(stringResource(R.string.settings_client_secret)) },
                    supportingText = { Text(stringResource(R.string.settings_client_secret_hint)) },
                    singleLine = true,
                    visualTransformation = if (revealClientSecret) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    keyboardOptions = SecretKeyboard,
                    trailingIcon = {
                        TextButton(onClick = { revealClientSecret = !revealClientSecret }) {
                            Text(
                                stringResource(
                                    if (revealClientSecret) R.string.settings_hide else R.string.settings_show,
                                ),
                            )
                        }
                    },
                )
                Text(
                    text = stringResource(R.string.settings_google_help),
                    modifier = Modifier.padding(top = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = {
                        scope.launch {
                            val saved = withContext(Dispatchers.IO) {
                                store.write(
                                    SecretStore.Secrets(
                                        deepSeekApiKey = apiKey,
                                        deepSeekModel = model,
                                        googleClientId = clientId,
                                        googleClientSecret = clientSecret,
                                    ),
                                )
                                store.read()
                            }
                            apiKey = saved.deepSeekApiKey
                            model = saved.deepSeekModel
                            clientId = saved.googleClientId
                            clientSecret = saved.googleClientSecret
                            snackbarHostState.showSnackbar(savedMessage)
                        }
                    },
                    enabled = loaded,
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                ) {
                    Text(stringResource(R.string.settings_save))
                }
            }
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
private fun SettingsSectionFrame(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val handlesBack = LocalDetailHandlesBack.current
    Box(modifier.fillMaxSize()) {
        Column(
            Modifier
                .holdWidthUntilSettled()
                .fillMaxSize(),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(end = if (handlesBack) 0.dp else MailCapsuleHeight + MailChromeInset),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (handlesBack) {
                    TextButton(onClick = onBack) {
                        Text(stringResource(R.string.settings_back))
                    }
                }
                Text(
                    text = title,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
            Box(Modifier.weight(1f)) {
                content()
            }
        }
        if (!handlesBack) {
            SplitFullscreenButton(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = MailChromeInset, end = MailChromeInset),
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 8.dp),
    )
}
