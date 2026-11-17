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
import androidx.compose.runtime.Stable
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
import com.ainscel.ainsmail.ui.mailbox.MailboxList
import com.ainscel.ainsmail.ui.mailbox.MailboxPlace
import com.ainscel.ainsmail.ui.mailbox.title
import com.ainscel.ainsmail.ui.shell.MailChromeInset

/**
 * 列表入口共用这一份回调。行组件按邮件 id 调用它，不再各自收一个新 lambda。
 */
@Stable
class MailListActions(
    val onIntent: (MailboxIntent) -> AddTagResult?,
    val onOpenMail: (String) -> Unit,
    val onCompose: () -> Unit,
    val onOpenOverlay: () -> Unit,
)

private const val RowReading = "reading"
private const val RowSelecting = "selecting"

@Composable
fun MailListPane(
    place: MailboxPlace,
    list: MailboxList,
    openMailId: String?,
    actions: MailListActions,
    modifier: Modifier = Modifier,
) {
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    var filterOpen by rememberSaveable { mutableStateOf(false) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var typeDialog by rememberSaveable { mutableStateOf(false) }
    var tagDialog by rememberSaveable { mutableStateOf(false) }
    var draftTag by rememberSaveable { mutableStateOf("") }
    var capsuleExpanded by rememberSaveable { mutableStateOf(false) }

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            if (list.multiSelect) {
                MultiSelectBar(
                    count = list.selectedCount,
                    enabled = list.selectedCount > 0,
                    onDelete = { actions.onIntent(MailboxIntent.DeleteSelected) },
                    onChangeType = { typeDialog = true },
                    onAddTag = { tagDialog = true },
                    onArchive = { actions.onIntent(MailboxIntent.ArchiveSelected) },
                    onCancel = { actions.onIntent(MailboxIntent.ExitMultiSelect) },
                )
            } else {
                ListTopBar(
                    title = stringResource(place.section.title()),
                    onTitleClick = actions.onOpenOverlay,
                    menuOpen = menuOpen,
                    onMenuOpen = { menuOpen = true },
                    onMenuDismiss = { menuOpen = false },
                    onFilter = { filterOpen = true },
                    onMarkRead = { actions.onIntent(MailboxIntent.MarkVisibleRead) },
                    onMultiSelect = {
                        menuOpen = false
                        actions.onIntent(MailboxIntent.EnterMultiSelect)
                    },
                    onSearch = {
                        menuOpen = false
                        searchOpen = true
                    },
                    onCompose = {
                        menuOpen = false
                        actions.onCompose()
                    },
                )
            }
            MailRows(
                place = place,
                list = list,
                openMailId = openMailId,
                actions = actions,
            )
        }
        if (place.section == Section.Inbox) {
            TypeCapsule(
                expanded = capsuleExpanded,
                selected = list.typeFilter,
                onExpand = { capsuleExpanded = true },
                onCollapse = {
                    actions.onIntent(MailboxIntent.SetTypeFilter(null))
                    capsuleExpanded = false
                },
                onSelect = { actions.onIntent(MailboxIntent.SetTypeFilter(it)) },
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
            actions.onIntent(MailboxIntent.SetType(it))
            typeDialog = false
        },
        onConfirmTag = {
            if (actions.onIntent(MailboxIntent.AddTag(draftTag)) == AddTagResult.Added) {
                draftTag = ""
                tagDialog = false
            }
        },
    )
    if (filterOpen) {
        FilterDialog(
            readFilter = list.readFilter,
            selectedTags = list.selectedTagNames,
            groups = list.tagGroups,
            onReadFilter = { actions.onIntent(MailboxIntent.SetReadFilter(it)) },
            onToggleTag = { actions.onIntent(MailboxIntent.ToggleTagFilter(it)) },
            onDismiss = { filterOpen = false },
        )
    }
    if (searchOpen) {
        SearchPendingDialog(onDismiss = { searchOpen = false })
    }
}

/**
 * 行列表单独一个组合。对话框草稿和菜单开关不进这里，打字不会把可见行重新组合一遍。
 */
@Composable
private fun MailRows(
    place: MailboxPlace,
    list: MailboxList,
    openMailId: String?,
    actions: MailListActions,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(place.scope, place.section) {
        listState.scrollToItem(0)
    }
    if (list.rows.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.list_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                bottom = if (place.section == Section.Inbox) 88.dp else 16.dp,
            ),
        ) {
            items(
                items = list.rows,
                key = { it.id },
                contentType = { if (list.multiSelect) RowSelecting else RowReading },
            ) { row ->
                MailRow(
                    row = row,
                    selected = row.id == openMailId,
                    multiSelect = list.multiSelect,
                    checked = row.id in list.selectedIds,
                    actions = actions,
                )
            }
        }
    }
}
