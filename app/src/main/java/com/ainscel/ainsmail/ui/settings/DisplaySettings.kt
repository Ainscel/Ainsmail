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
import com.ainscel.ainsmail.model.WeekDateStyle

/** 显示栏目。偏好只影响列表呈现，不收信。 */
@Composable
fun DisplaySettings(
    badgeStyle: AccountBadgeStyle,
    onBadge: (AccountBadgeStyle) -> Unit,
    weekDate: WeekDateStyle,
    onWeekDate: (WeekDateStyle) -> Unit,
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
            ChoiceGroup(
                title = stringResource(R.string.settings_account_badge),
                hint = stringResource(R.string.settings_account_badge_hint),
                options = AccountBadgeStyle.entries,
                selected = badgeStyle,
                labelOf = { stringResource(it.label) },
                onSelect = onBadge,
            )
            ChoiceGroup(
                title = stringResource(R.string.settings_week_date),
                hint = stringResource(R.string.settings_week_date_hint),
                options = WeekDateStyle.entries,
                selected = weekDate,
                labelOf = { stringResource(it.label) },
                onSelect = onWeekDate,
                modifier = Modifier.padding(top = 20.dp),
            )
        }
    }
}

@Composable
private fun <T> ChoiceGroup(
    title: String,
    hint: String,
    options: List<T>,
    selected: T,
    labelOf: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        Text(
            text = hint,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        options.forEach { option ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(option) }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = selected == option,
                    onClick = { onSelect(option) },
                )
                Text(
                    text = labelOf(option),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

private val AccountBadgeStyle.label: Int
    get() = when (this) {
        AccountBadgeStyle.EveryRow -> R.string.settings_account_badge_every
        AccountBadgeStyle.FirstOfRun -> R.string.settings_account_badge_first
        AccountBadgeStyle.SameSender -> R.string.settings_account_badge_sender
    }

private val WeekDateStyle.label: Int
    get() = when (this) {
        WeekDateStyle.Date -> R.string.settings_week_date_calendar
        WeekDateStyle.Relative -> R.string.settings_week_date_relative
    }
