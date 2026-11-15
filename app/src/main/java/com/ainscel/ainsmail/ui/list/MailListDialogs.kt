package com.ainscel.ainsmail.ui.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ainscel.ainsmail.R
import com.ainscel.ainsmail.model.MailType
import com.ainscel.ainsmail.model.ReadFilter
import com.ainscel.ainsmail.model.TagPool
import com.ainscel.ainsmail.ui.mailbox.TagGroupUi
import com.ainscel.ainsmail.ui.mailbox.title

@Composable
internal fun FilterDialog(
    readFilter: ReadFilter,
    selectedTags: Set<String>,
    groups: List<TagGroupUi>,
    onReadFilter: (ReadFilter) -> Unit,
    onToggleTag: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.filter_done)) }
        },
        title = { Text(stringResource(R.string.filter_title)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.filter_read_state), style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ReadFilter.entries.forEach { filter ->
                        FilterChip(
                            selected = readFilter == filter,
                            onClick = { onReadFilter(filter) },
                            label = { Text(stringResource(filter.title())) },
                        )
                    }
                }
                Text(stringResource(R.string.filter_tags), style = MaterialTheme.typography.titleSmall)
                if (groups.isEmpty()) {
                    Text(stringResource(R.string.filter_no_tags))
                }
                TagPool.entries.forEach { pool ->
                    val inPool = groups.filter { it.pool == pool }
                    if (inPool.isEmpty()) return@forEach
                    Text(stringResource(pool.title()), style = MaterialTheme.typography.labelLarge)
                    inPool.forEach { group ->
                        Text(stringResource(group.type.title()), style = MaterialTheme.typography.labelMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            group.names.forEach { name ->
                                FilterChip(
                                    selected = name in selectedTags,
                                    onClick = { onToggleTag(name) },
                                    label = { Text(name) },
                                )
                            }
                        }
                    }
                }
            }
        },
    )
}

@Composable
internal fun MultiSelectActions(
    showType: Boolean,
    showTag: Boolean,
    draftTag: String,
    onDraftTag: (String) -> Unit,
    onDismissType: () -> Unit,
    onDismissTag: () -> Unit,
    onPickType: (MailType) -> Unit,
    onConfirmTag: () -> Unit,
) {
    if (showType) {
        AlertDialog(
            onDismissRequest = onDismissType,
            confirmButton = {
                TextButton(onClick = onDismissType) { Text(stringResource(R.string.list_cancel)) }
            },
            title = { Text(stringResource(R.string.list_change_type)) },
            text = {
                Column {
                    MailType.entries.forEach { type ->
                        TextButton(onClick = { onPickType(type) }) {
                            Text(stringResource(type.title()))
                        }
                    }
                }
            },
        )
    }
    if (showTag) {
        AlertDialog(
            onDismissRequest = onDismissTag,
            confirmButton = {
                TextButton(onClick = onConfirmTag) { Text(stringResource(R.string.list_tag_add)) }
            },
            dismissButton = {
                TextButton(onClick = onDismissTag) { Text(stringResource(R.string.list_cancel)) }
            },
            title = { Text(stringResource(R.string.list_add_tag)) },
            text = {
                OutlinedTextField(
                    value = draftTag,
                    onValueChange = onDraftTag,
                    label = { Text(stringResource(R.string.list_tag_hint)) },
                    singleLine = true,
                )
            },
        )
    }
}

@Composable
internal fun SearchPendingDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.search_ok))
            }
        },
        title = { Text(stringResource(R.string.search_title)) },
        text = { Text(stringResource(R.string.search_disabled)) },
    )
}
