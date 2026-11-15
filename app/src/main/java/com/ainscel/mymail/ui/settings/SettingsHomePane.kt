package com.ainscel.mymail.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ainscel.mymail.R

/** 设置栏目列表。窄屏时整页显示，宽屏时占分界线左侧。 */
@Composable
fun SettingsHomePane(
    selected: SettingsSection?,
    onBack: () -> Unit,
    onOpen: (SettingsSection) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        TextButton(
            onClick = onBack,
            modifier = Modifier.padding(start = 8.dp, top = 4.dp),
        ) {
            Text(stringResource(R.string.settings_back))
        }
        Text(
            text = stringResource(R.string.open_settings),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.headlineSmall,
        )
        SettingsSection.entries.forEach { section ->
            val background = if (section == selected) {
                MaterialTheme.colorScheme.surfaceContainerHigh
            } else {
                MaterialTheme.colorScheme.surface
            }
            Text(
                text = stringResource(section.label),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(background)
                    .clickable { onOpen(section) }
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}
