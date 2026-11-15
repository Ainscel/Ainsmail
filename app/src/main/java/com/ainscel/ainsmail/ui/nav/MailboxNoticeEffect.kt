package com.ainscel.ainsmail.ui.nav

import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.stringResource
import com.ainscel.ainsmail.R
import com.ainscel.ainsmail.ui.mailbox.Notice

/** 本期还没有行为的入口。只在界面上说明，不进邮箱归约。 */
enum class UnavailableKind {
    Login,
    Group,
    OriginalLayout,
    Edit,
}

data class UnavailableHint(
    val kind: UnavailableKind,
    val id: Long,
)

@Composable
internal fun MailboxNoticeEffect(
    notice: Notice?,
    hostState: SnackbarHostState,
    onUndo: () -> Unit,
    onConsumed: (Long) -> Unit,
) {
    val message = notice?.text()
    val undoLabel = stringResource(R.string.undo)
    LaunchedEffect(notice?.id) {
        val current = notice ?: return@LaunchedEffect
        val text = message ?: return@LaunchedEffect
        val result = hostState.showSnackbar(
            message = text,
            actionLabel = if (current.canUndo) undoLabel else null,
        )
        if (result == SnackbarResult.ActionPerformed) onUndo()
        onConsumed(current.id)
    }
}

@Composable
private fun Notice.text(): String = when (this) {
    is Notice.Deleted -> stringResource(R.string.list_deleted)
    is Notice.Archived -> stringResource(R.string.list_archived)
    is Notice.TagLimit -> stringResource(R.string.list_tag_limit)
    is Notice.TagNeedsType -> stringResource(R.string.list_tag_needs_type)
    is Notice.TagMixedTypes -> stringResource(R.string.list_tag_mixed_types)
}

@Composable
internal fun UnavailableHintEffect(
    hint: UnavailableHint?,
    hostState: SnackbarHostState,
    onConsumed: () -> Unit,
) {
    val message = hint?.text()
    LaunchedEffect(hint?.id) {
        val text = message ?: return@LaunchedEffect
        hostState.showSnackbar(text)
        onConsumed()
    }
}

@Composable
private fun UnavailableHint.text(): String = when (kind) {
    UnavailableKind.Login -> stringResource(R.string.account_add_login_pending)
    UnavailableKind.Group -> stringResource(R.string.account_add_group_pending)
    UnavailableKind.OriginalLayout -> stringResource(R.string.detail_original_pending)
    UnavailableKind.Edit -> stringResource(R.string.detail_edit_pending)
}
