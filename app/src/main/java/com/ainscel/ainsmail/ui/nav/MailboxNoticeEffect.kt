package com.ainscel.ainsmail.ui.nav

import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.stringResource
import com.ainscel.ainsmail.R
import com.ainscel.ainsmail.ui.mailbox.NoticeUi

@Composable
internal fun MailboxNoticeEffect(
    notice: NoticeUi?,
    hostState: SnackbarHostState,
        onUndo: () -> Unit,
        onConsumed: (Long) -> Unit,
) {
    val undoLabel = stringResource(R.string.undo)
    LaunchedEffect(notice?.id) {
        val current = notice ?: return@LaunchedEffect
        val result = hostState.showSnackbar(
            message = current.text,
            actionLabel = if (current.canUndo) undoLabel else null,
        )
        if (result == SnackbarResult.ActionPerformed) onUndo()
        onConsumed(current.id)
    }
}
