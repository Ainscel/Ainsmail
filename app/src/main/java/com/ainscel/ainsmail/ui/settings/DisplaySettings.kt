package com.ainscel.ainsmail.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ainscel.ainsmail.R
import com.ainscel.ainsmail.model.AccountBadgeStyle

/** 显示栏目。目前只有收件账户小圆的重复方式。 */
@Composable
fun DisplaySettings(
    style: AccountBadgeStyle,
    onSelect: (AccountBadgeStyle) -> Unit,
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsSectionFrame(title = title, onBack = onBack, modifier = modifier) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_account_badge),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.settings_account_badge_hint),
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AccountBadgeStyle.entries.forEach { option ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(option) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = style == option,
                        onClick = { onSelect(option) },
                    )
                    Text(
                        text = stringResource(option.label),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}

private val AccountBadgeStyle.label: Int
    get() = when (this) {
        AccountBadgeStyle.EveryRow -> R.string.settings_account_badge_every
        AccountBadgeStyle.FirstOfRun -> R.string.settings_account_badge_first
    }
