package com.ainscel.mymail.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ainscel.mymail.R
import com.ainscel.mymail.model.Mail
import com.ainscel.mymail.model.formatMailTime
import com.ainscel.mymail.ui.shell.LocalDetailHandlesBack
import com.ainscel.mymail.ui.shell.horizontalDragOrScroll

@Composable
fun MailDetailPane(
    mail: Mail?,
    accountName: String,
    onClose: () -> Unit,
    onOriginalLayout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val handlesBack = LocalDetailHandlesBack.current
    Column(
        modifier
            .fillMaxSize()
            .horizontalDragOrScroll(
                excludeStartEdge = handlesBack,
                excludeEndEdge = true,
                onDelta = {},
                onFinish = { onClose() },
            )
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        if (mail == null) {
            Text(
                text = stringResource(R.string.detail_missing),
                style = MaterialTheme.typography.bodyLarge,
            )
            return@Column
        }
        Text(text = mail.subject, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            text = mail.fromName,
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = "$accountName · ${formatMailTime(mail.timeMillis)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Text(text = mail.body, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onOriginalLayout,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.detail_original))
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
