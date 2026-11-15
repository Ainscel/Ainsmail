package com.ainscel.ainsmail.ui.list

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ainscel.ainsmail.R
import com.ainscel.ainsmail.model.MailType
import com.ainscel.ainsmail.ui.mailbox.title
import com.ainscel.ainsmail.ui.shell.MailCapsuleHeight

@Composable
internal fun TypeCapsule(
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
                            label = { Text(stringResource(type.title())) },
                            modifier = Modifier.padding(end = 6.dp),
                        )
                    }
                }
            }
        }
    }
}
