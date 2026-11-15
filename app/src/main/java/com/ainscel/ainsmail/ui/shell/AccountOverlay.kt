package com.ainscel.ainsmail.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.ainscel.ainsmail.model.Section
import com.ainscel.ainsmail.ui.mailbox.AccountUi
import com.ainscel.ainsmail.ui.mailbox.title
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun AccountOverlay(
    progress: () -> Float,
    panelWidth: Dp,
    panelWidthPx: Float,
    accounts: List<AccountUi>,
    selectedAccountId: String?,
    currentName: String,
    section: Section,
    onSelectAccount: (String?) -> Unit,
    onSelectSection: (Section) -> Unit,
    onAdd: () -> Unit,
    onSettings: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val visible by remember { derivedStateOf { abs(progress()) > 0.001f } }
    if (!visible) return
    Box(modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    val alpha = progress().coerceIn(0f, 1f) * 0.38f
                    drawRect(Color.Black.copy(alpha = alpha))
                }
                .clickable(onClick = onClose),
        )
        Row(
            Modifier
                .fillMaxHeight()
                .width(panelWidth)
                .offset {
                    val shown = panelWidthPx * progress().coerceAtLeast(0f)
                    IntOffset((shown - panelWidthPx).roundToInt(), 0)
                }
                .background(MaterialTheme.colorScheme.surface),
        ) {
            AvatarColumn(
                accounts = accounts,
                selectedAccountId = selectedAccountId,
                onSelectAccount = onSelectAccount,
                onAdd = onAdd,
                onSettings = onSettings,
                modifier = Modifier
                    .width(76.dp)
                    .fillMaxHeight(),
            )
            Box(
                Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )
            SectionColumn(
                currentName = currentName,
                section = section,
                onSelectSection = onSelectSection,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun SectionColumn(
    currentName: String,
    section: Section,
    onSelectSection: (Section) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.padding(start = 16.dp, end = 12.dp, top = 20.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = currentName,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp, start = 8.dp),
        )
        Section.entries.forEach { item ->
            val selected = item == section
            Text(
                text = stringResource(item.title()),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Start,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (selected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
                    )
                    .clickable { onSelectSection(item) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            )
        }
    }
}
