package com.ainscel.ainsmail.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ainscel.ainsmail.R
import com.ainscel.ainsmail.ui.mailbox.MailDetailUi
import com.ainscel.ainsmail.ui.shell.LocalDetailFullscreen
import com.ainscel.ainsmail.ui.shell.LocalDetailHandlesBack
import com.ainscel.ainsmail.ui.shell.MailCapsuleHeight
import com.ainscel.ainsmail.ui.shell.MailChromeInset
import com.ainscel.ainsmail.ui.shell.MailRoundButton
import com.ainscel.ainsmail.ui.shell.SplitFullscreenButton
import com.ainscel.ainsmail.ui.shell.holdWidthUntilSettled
import com.ainscel.ainsmail.ui.shell.horizontalDragOrScroll

private val DetailActionGap = 8.dp

@Composable
fun MailDetailPane(
    detail: MailDetailUi?,
    onClose: () -> Unit,
    onOriginalLayout: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val handlesBack = LocalDetailHandlesBack.current
    val fullscreen = LocalDetailFullscreen.current
    Box(
        modifier
            .fillMaxSize()
            .horizontalDragOrScroll(
                excludeStartEdge = handlesBack,
                excludeEndEdge = true,
                onDelta = {},
                onFinish = { onClose() },
            ),
    ) {
        if (detail == null) {
            Box(
                Modifier
                    .holdWidthUntilSettled()
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            ) {
                Text(
                    text = stringResource(R.string.detail_missing),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            return@Box
        }
        Column(
            Modifier
                .holdWidthUntilSettled()
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Column(Modifier.padding(end = if (fullscreen != null) MailCapsuleHeight else 0.dp)) {
                Text(text = detail.subject, style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = detail.fromName,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = detail.metaLine,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(text = detail.body, style = MaterialTheme.typography.bodyLarge)
            // 给右下角三颗圆形按钮留出空档，正文滚到底时不会被挡住。
            Spacer(
                Modifier.height(MailCapsuleHeight * 3 + DetailActionGap * 2 + MailChromeInset + 8.dp),
            )
        }
        SplitFullscreenButton(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = MailChromeInset, end = MailChromeInset),
        )
        // 从下往上是编辑、删除、按原版式查看。最底下那颗和列表分类胶囊处于同一高度。
        Column(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = MailChromeInset, bottom = MailChromeInset),
            verticalArrangement = Arrangement.spacedBy(DetailActionGap),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            MailRoundButton(
                icon = MailIcons.OpenInNew,
                description = stringResource(R.string.detail_original),
                onClick = onOriginalLayout,
            )
            MailRoundButton(
                icon = Icons.Filled.Delete,
                description = stringResource(R.string.list_delete),
                onClick = onDelete,
            )
            MailRoundButton(
                icon = Icons.Filled.Edit,
                description = stringResource(R.string.detail_edit),
                onClick = onEdit,
            )
        }
    }
}

@Composable
fun ComposePlaceholder(
    replySubject: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        androidx.compose.material3.TextButton(onClick = onBack) {
            Text(stringResource(R.string.settings_back))
        }
        Text(text = stringResource(R.string.compose_title), style = MaterialTheme.typography.headlineSmall)
        Text(
            text = stringResource(R.string.compose_pending),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (replySubject != null) {
            Text(text = stringResource(R.string.compose_reply, replySubject))
        }
    }
}
