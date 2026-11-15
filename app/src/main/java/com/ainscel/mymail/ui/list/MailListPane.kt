package com.ainscel.mymail.ui.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ainscel.mymail.R
import com.ainscel.mymail.model.Folder
import com.ainscel.mymail.model.MailType
import com.ainscel.mymail.model.ReadFilter
import com.ainscel.mymail.model.TagPool
import com.ainscel.mymail.ui.mailbox.MailboxViewModel
import com.ainscel.mymail.ui.shell.MailCapsuleHeight
import com.ainscel.mymail.ui.shell.MailChromeInset
import com.ainscel.mymail.ui.shell.OverlayController
import com.ainscel.mymail.ui.shell.horizontalDragOrScroll

@Composable
fun MailListPane(
    mailbox: MailboxViewModel,
    openMailId: String?,
    overlay: OverlayController,
    overlayWidthPx: Float,
    onOpenMail: (String) -> Unit,
    onCompose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mails = mailbox.visibleMails()
    val listState = rememberLazyListState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    var filterOpen by rememberSaveable { mutableStateOf(false) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var typeDialog by rememberSaveable { mutableStateOf(false) }
    var tagDialog by rememberSaveable { mutableStateOf(false) }
    var draftTag by rememberSaveable { mutableStateOf("") }
    val deletedMessage = stringResource(R.string.list_deleted)
    val archivedMessage = stringResource(R.string.list_archived)
    val tagLimitMessage = stringResource(R.string.list_tag_limit)
    val tagNeedsTypeMessage = stringResource(R.string.list_tag_needs_type)

    LaunchedEffect(mailbox.accountId, mailbox.folder) {
        listState.scrollToItem(0)
    }

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            if (mailbox.multiSelect) {
                MultiSelectBar(
                    count = mailbox.selectedIds.size,
                    enabled = mailbox.selectedIds.isNotEmpty(),
                    onDelete = { mailbox.deleteSelected(deletedMessage) },
                    onChangeType = { typeDialog = true },
                    onAddTag = { tagDialog = true },
                    onArchive = { mailbox.archiveSelected(archivedMessage) },
                    onCancel = mailbox::exitMultiSelect,
                )
            } else {
                ListTopBar(
                    title = stringResource(mailbox.folder.label),
                    onTitleClick = { overlay.open(scope) },
                    menuOpen = menuOpen,
                    onMenuOpen = { menuOpen = true },
                    onMenuDismiss = { menuOpen = false },
                    onFilter = { filterOpen = true },
                    onMarkRead = mailbox::markVisibleRead,
                    onMultiSelect = {
                        menuOpen = false
                        mailbox.enterMultiSelect()
                    },
                    onSearch = {
                        menuOpen = false
                        searchOpen = true
                    },
                    onCompose = {
                        menuOpen = false
                        onCompose()
                    },
                )
            }
            if (mails.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.list_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .horizontalDragOrScroll(
                            excludeStartEdge = true,
                            onDelta = { overlay.dragBy(it, overlayWidthPx) },
                            onFinish = { overlay.release(scope) },
                        ),
                    contentPadding = PaddingValues(
                        bottom = if (mailbox.folder == Folder.Inbox) 88.dp else 16.dp,
                    ),
                ) {
                    items(mails, key = { it.id }) { mail ->
                        MailRow(
                            mail = mail,
                            accountName = if (mailbox.accountId == null) {
                                mailbox.account(mail.accountId)?.name
                            } else {
                                null
                            },
                            selected = mail.id == openMailId,
                            multiSelect = mailbox.multiSelect,
                            checked = mail.id in mailbox.selectedIds,
                            showClassifying = mailbox.folder == Folder.Inbox &&
                                mailbox.typeFilter == null &&
                                mail.type == null,
                            onClick = {
                                if (mailbox.multiSelect) {
                                    mailbox.toggleSelected(mail.id)
                                } else {
                                    onOpenMail(mail.id)
                                }
                            },
                            onChecked = { mailbox.toggleSelected(mail.id) },
                        )
                    }
                }
            }
        }
        if (mailbox.folder == Folder.Inbox) {
            TypeCapsule(
                expanded = mailbox.capsuleExpanded,
                selected = mailbox.typeFilter,
                onExpand = { mailbox.capsuleExpanded = true },
                onCollapse = {
                    mailbox.typeFilter = null
                    mailbox.capsuleExpanded = false
                },
                onSelect = { mailbox.typeFilter = it },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(MailChromeInset)
                    .then(
                        if (mailbox.capsuleExpanded) {
                            Modifier.fillMaxWidth()
                        } else {
                            Modifier
                        },
                    ),
            )
        }
    }

    MultiSelectActions(
        showType = typeDialog,
        showTag = tagDialog,
        draftTag = draftTag,
        onDraftTag = { draftTag = it },
        onDismissType = { typeDialog = false },
        onDismissTag = { tagDialog = false },
        onPickType = {
            mailbox.setType(it)
            typeDialog = false
        },
        onConfirmTag = {
            when (mailbox.addTag(draftTag)) {
                MailboxViewModel.AddTagResult.AtLimit -> mailbox.message(tagLimitMessage)
                MailboxViewModel.AddTagResult.NeedsType -> mailbox.message(tagNeedsTypeMessage)
                MailboxViewModel.AddTagResult.Added -> {
                    draftTag = ""
                    tagDialog = false
                }
                MailboxViewModel.AddTagResult.Blank -> Unit
            }
        },
    )

    if (filterOpen) {
        FilterDialog(
            readFilter = mailbox.readFilter,
            selectedTags = mailbox.selectedTags,
            tags = mailbox.mails.flatMap { it.tags }.distinctBy { it.name to it.pool to it.type },
            onReadFilter = { mailbox.readFilter = it },
            onToggleTag = mailbox::toggleTag,
            onDismiss = { filterOpen = false },
        )
    }
    if (searchOpen) {
        AlertDialog(
            onDismissRequest = { searchOpen = false },
            confirmButton = {
                TextButton(onClick = { searchOpen = false }) {
                    Text(stringResource(R.string.search_ok))
                }
            },
            title = { Text(stringResource(R.string.search_title)) },
            text = { Text(stringResource(R.string.search_disabled)) },
        )
    }

}

@Composable
private fun ListTopBar(
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
                    .zeroReflowFadeText(fadeWidth = 28.dp),
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
private fun MultiSelectBar(
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

@Composable
private fun TypeCapsule(
    expanded: Boolean,
    selected: MailType?,
    onExpand: () -> Unit,
    onCollapse: () -> Unit,
    onSelect: (MailType) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.height(MailCapsuleHeight),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 2.dp,
    ) {
        if (!expanded) {
            TextButton(onClick = onExpand) {
                Text(stringResource(R.string.capsule_classify))
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onCollapse) {
                    Text(stringResource(R.string.capsule_classify))
                }
                Row(Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
                    MailType.entries.forEach { type ->
                        FilterChip(
                            selected = selected == type,
                            onClick = { onSelect(type) },
                            label = { Text(stringResource(type.label)) },
                            modifier = Modifier.padding(end = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterDialog(
    readFilter: ReadFilter,
    selectedTags: List<String>,
    tags: List<com.ainscel.mymail.model.MailTag>,
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
                            label = { Text(stringResource(filter.label)) },
                        )
                    }
                }
                Text(stringResource(R.string.filter_tags), style = MaterialTheme.typography.titleSmall)
                if (tags.isEmpty()) {
                    Text(stringResource(R.string.filter_no_tags))
                }
                TagPool.entries.forEach { pool ->
                    val inPool = tags.filter { it.pool == pool }
                    if (inPool.isEmpty()) return@forEach
                    Text(stringResource(pool.label), style = MaterialTheme.typography.labelLarge)
                    MailType.entries.forEach { type ->
                        val named = inPool.filter { it.type == type }.map { it.name }.distinct()
                        if (named.isEmpty()) return@forEach
                        Text(stringResource(type.label), style = MaterialTheme.typography.labelMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            named.forEach { name ->
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
private fun MultiSelectActions(
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
                            Text(stringResource(type.label))
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
