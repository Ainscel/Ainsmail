package com.ainscel.ainsmail.ui.list

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ainscel.ainsmail.R
import com.ainscel.ainsmail.model.Section
import com.ainscel.ainsmail.ui.mailbox.AddTagResult
import com.ainscel.ainsmail.ui.mailbox.MailboxIntent
import com.ainscel.ainsmail.ui.mailbox.MailboxUiState
import com.ainscel.ainsmail.ui.mailbox.title
import com.ainscel.ainsmail.ui.shell.MailChromeInset
import com.ainscel.ainsmail.ui.shell.horizontalDragOrScroll

private const val RowReading = "reading"
private const val RowSelecting = "selecting"

@Composable
fun MailListPane(
    state: MailboxUiState,
    openMailId: String?,
    onIntent: (MailboxIntent) -> AddTagResult?,
    onOpenMail: (String) -> Unit,
    onCompose: () -> Unit,
    onOpenOverlay: () -> Unit,
    onOverlayDrag: (Float) -> Unit,
    onOverlayRelease: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    var filterOpen by rememberSaveable { mutableStateOf(false) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var typeDialog by rememberSaveable { mutableStateOf(false) }
    var tagDialog by rememberSaveable { mutableStateOf(false) }
    var draftTag by rememberSaveable { mutableStateOf("") }
    var capsuleExpanded by rememberSaveable { mutableStateOf(false) }
    fun perform(intent: MailboxIntent) {
        onIntent(intent)
    }
    LaunchedEffect(state.scope, state.section) {
        listState.scrollToItem(0)
    }

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            if (state.multiSelect) {
                MultiSelectBar(
                    count = state.selectedCount,
                    enabled = state.selectedCount > 0,
                    onDelete = { perform(MailboxIntent.DeleteSelected) },
                    onChangeType = { typeDialog = true },
                    onAddTag = { tagDialog = true },
                    onArchive = { perform(MailboxIntent.ArchiveSelected) },
                    onCancel = { perform(MailboxIntent.ExitMultiSelect) },
                )
            } else {
                ListTopBar(
                    title = stringResource(state.section.title()),
                    onTitleClick = onOpenOverlay,
                    menuOpen = menuOpen,
                    onMenuOpen = { menuOpen = true },
                    onMenuDismiss = { menuOpen = false },
                    onFilter = { filterOpen = true },
                    onMarkRead = { perform(MailboxIntent.MarkVisibleRead) },
                    onMultiSelect = {
                        menuOpen = false
                        perform(MailboxIntent.EnterMultiSelect)
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
            if (state.rows.isEmpty()) {
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
                            onDelta = onOverlayDrag,
                            onFinish = { onOverlayRelease() },
                        ),
                    contentPadding = PaddingValues(
                        bottom = if (state.section == Section.Inbox) 88.dp else 16.dp,
                    ),
                ) {
                    items(
                        items = state.rows,
                        key = { it.id },
                        contentType = { if (state.multiSelect) RowSelecting else RowReading },
                    ) { row ->
                        MailRow(
                            row = row,
                            selected = row.id == openMailId,
                            multiSelect = state.multiSelect,
                            onClick = {
                                if (state.multiSelect) {
                                    perform(MailboxIntent.ToggleSelected(row.id))
                                } else {
                                    onOpenMail(row.id)
                                }
                            },
                            onChecked = { perform(MailboxIntent.ToggleSelected(row.id)) },
                        )
                    }
                }
            }
        }
        if (state.section == Section.Inbox) {
            TypeCapsule(
                expanded = capsuleExpanded,
                selected = state.typeFilter,
                onExpand = { capsuleExpanded = true },
                onCollapse = {
                    perform(MailboxIntent.SetTypeFilter(null))
                    capsuleExpanded = false
                },
                onSelect = { perform(MailboxIntent.SetTypeFilter(it)) },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(MailChromeInset)
                    .then(
                        if (capsuleExpanded) Modifier.fillMaxWidth() else Modifier,
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
            perform(MailboxIntent.SetType(it))
            typeDialog = false
        },
        onConfirmTag = {
            if (onIntent(MailboxIntent.AddTag(draftTag)) == AddTagResult.Added) {
                draftTag = ""
                tagDialog = false
            }
        },
    )
    if (filterOpen) {
        FilterDialog(
            readFilter = state.readFilter,
            selectedTags = state.selectedTagNames,
            groups = state.tagGroups,
            onReadFilter = { perform(MailboxIntent.SetReadFilter(it)) },
            onToggleTag = { perform(MailboxIntent.ToggleTagFilter(it)) },
            onDismiss = { filterOpen = false },
        )
    }
    if (searchOpen) {
        SearchPendingDialog(onDismiss = { searchOpen = false })
    }
}
