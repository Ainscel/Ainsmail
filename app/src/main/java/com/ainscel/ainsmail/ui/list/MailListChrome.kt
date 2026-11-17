package com.ainscel.ainsmail.ui.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ainscel.ainsmail.R

@Composable
internal fun ListTopBar(
    title: String,
    onTitleClick: () -> Unit,
    menuOpen: Boolean,
    onMenuOpen: () -> Unit,
    onMenuDismiss: () -> Unit,
    onFilter: () -> Unit,
    onMarkRead: () -> Unit,
    onMultiSelect: () -> Unit,
    onSearch: () -> Unit,
    onCompose: () -> Unit,
) {
    // 标题与右侧三按钮用固定约束测量，拖分界线时只改按钮 X，不再整行重测。
    ZeroReflowTopBar(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 4.dp, top = 4.dp),
        leftTitle = {
            Text(
                text = title,
                modifier = Modifier
                    .padding(start = 8.dp, end = 4.dp)
                    .clickable(onClick = onTitleClick)
                    .zeroReflowFadeText(
                        // 顶栏没有自己的底色，淡出盖住的是 Scaffold 的 surface。
                        background = MaterialTheme.colorScheme.surface,
                        fadeWidth = 28.dp,
                    ),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
                style = MaterialTheme.typography.headlineSmall,
            )
        },
        rightActions = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onFilter) { BarAction(stringResource(R.string.list_filter)) }
                TextButton(onClick = onMarkRead) { BarAction(stringResource(R.string.list_mark_read)) }
                Box {
                    TextButton(onClick = onMenuOpen) { BarAction(stringResource(R.string.list_more)) }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = onMenuDismiss) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.list_multi_select)) },
                            onClick = onMultiSelect,
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.list_search)) },
                            onClick = onSearch,
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.list_compose)) },
                            onClick = onCompose,
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun BarAction(label: String) {
    Text(text = label, maxLines = 1, softWrap = false)
}

@Composable
internal fun MultiSelectBar(
    count: Int,
    enabled: Boolean,
    onDelete: () -> Unit,
    onChangeType: () -> Unit,
    onAddTag: () -> Unit,
    onArchive: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
        Text(
            text = stringResource(R.string.list_selected, count),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 8.dp, bottom = 4.dp),
        )
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            TextButton(onClick = onDelete, enabled = enabled) {
                Text(stringResource(R.string.list_delete))
            }
            TextButton(onClick = onChangeType, enabled = enabled) {
                Text(stringResource(R.string.list_change_type))
            }
            TextButton(onClick = onAddTag, enabled = enabled) {
                Text(stringResource(R.string.list_add_tag))
            }
            TextButton(onClick = onArchive, enabled = enabled) {
                Text(stringResource(R.string.list_archive))
            }
            TextButton(onClick = onCancel) { Text(stringResource(R.string.list_cancel)) }
        }
    }
}
