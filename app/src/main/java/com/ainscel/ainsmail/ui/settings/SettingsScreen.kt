package com.ainscel.ainsmail.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ainscel.ainsmail.R
import com.ainscel.ainsmail.ui.detail.SplitFullscreenButton
import com.ainscel.ainsmail.ui.shell.LocalDetailHandlesBack
import com.ainscel.ainsmail.ui.shell.MailCapsuleHeight
import com.ainscel.ainsmail.ui.shell.MailChromeInset
import com.ainscel.ainsmail.ui.shell.holdWidthUntilSettled

/** 某个设置栏目。窄屏整页，宽屏在分界线右侧。 */
@Composable
fun SettingsSectionPane(
    section: SettingsSection,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = stringResource(section.label)
    when (section) {
        SettingsSection.Secrets -> {
            val context = LocalContext.current
            val model = viewModel<SecretsViewModel>(factory = SecretsViewModel.factory(context))
            SecretsSettings(
                state = model.state,
                onEdit = model::onEdit,
                title = title,
                onBack = onBack,
                modifier = modifier,
            )
        }
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

@Composable
private fun SecretsSettings(
    state: SecretsUiState,
    onEdit: (SecretsEdit) -> Unit,
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val savedMessage = stringResource(R.string.settings_saved)
    val failedMessage = stringResource(R.string.settings_secrets_unreadable)
    LaunchedEffect(state.savedTick) {
        if (state.savedTick == 0L) return@LaunchedEffect
        snackbarHostState.showSnackbar(savedMessage)
    }
    LaunchedEffect(state.errorTick) {
        if (state.errorTick == 0L) return@LaunchedEffect
        snackbarHostState.showSnackbar(failedMessage)
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
                if (state.loadFailed) {
                    Text(
                        text = failedMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                DeepSeekFields(state = state, onEdit = onEdit)
                GoogleFields(state = state, onEdit = onEdit)
                Button(
                    onClick = { onEdit(SecretsEdit.Save) },
                    enabled = state.loaded,
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
