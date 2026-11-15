package com.ainscel.ainsmail.ui.shell

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ainscel.ainsmail.R
import com.ainscel.ainsmail.model.Account
import com.ainscel.ainsmail.model.Folder
import com.ainscel.ainsmail.model.avatarColor
import com.ainscel.ainsmail.model.avatarLabel
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@Composable
fun AccountOverlay(
    progress: Float,
    panelWidth: Dp,
    panelWidthPx: Float,
    accounts: List<Account>,
    selectedAccountId: String?,
    currentName: String,
    folder: Folder,
    onSelectAccount: (String?) -> Unit,
    onSelectFolder: (Folder) -> Unit,
    onAdd: () -> Unit,
    onSettings: () -> Unit,
    onClose: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (progress <= 0.001f && progress >= -0.001f) return
    val scrimAlpha = progress.coerceIn(0f, 1f) * 0.38f
    Box(modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = scrimAlpha))
                .clickable(onClick = onClose),
        )
        Row(
            Modifier
                .fillMaxHeight()
                .width(panelWidth)
                .offset {
                    val shown = panelWidthPx * progress.coerceAtLeast(0f)
                    IntOffset((shown - panelWidthPx).roundToInt(), 0)
                }
                .background(MaterialTheme.colorScheme.surface)
                .horizontalDragOrScroll(
                    onDelta = onDrag,
                    onFinish = { onDragFinish() },
                ),
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
            FolderColumn(
                currentName = currentName,
                folder = folder,
                onSelectFolder = onSelectFolder,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun AvatarColumn(
    accounts: List<Account>,
    selectedAccountId: String?,
    onSelectAccount: (String?) -> Unit,
    onAdd: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.background(MaterialTheme.colorScheme.surfaceContainerLow),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.padding(top = 8.dp, bottom = 4.dp)) {
            CircleButton(
                label = stringResource(R.string.account_all),
                selected = selectedAccountId == null,
                onClick = { onSelectAccount(null) },
                container = MaterialTheme.colorScheme.primary,
                content = Color.White,
            ) {
                AllAccountsMark()
            }
        }
        AvatarScroller(
            accounts = accounts,
            selectedAccountId = selectedAccountId,
            onSelectAccount = onSelectAccount,
            onAdd = onAdd,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        )
        Box(Modifier.padding(top = 4.dp, bottom = 8.dp)) {
            CircleButton(
                label = stringResource(R.string.open_settings),
                selected = false,
                onClick = onSettings,
                container = MaterialTheme.colorScheme.surfaceVariant,
                content = MaterialTheme.colorScheme.onSurfaceVariant,
            ) {
                Text("设", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp)
            }
        }
    }
}

@Composable
private fun AvatarScroller(
    accounts: List<Account>,
    selectedAccountId: String?,
    onSelectAccount: (String) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val item = 56.dp
    val gap = 8.dp
    var offset by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    BoxWithConstraints(
        modifier.clipToBounds(),
    ) {
        val itemPx = with(density) { item.toPx() }
        val gapPx = with(density) { gap.toPx() }
        val view = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val avatarSpan = if (accounts.isEmpty()) {
            0f
        } else {
            accounts.size * itemPx + (accounts.size - 1) * gapPx
        }
        val content = avatarSpan + if (accounts.isEmpty()) 0f else gapPx + itemPx
        val maxScroll = (content - view).coerceAtLeast(0f)
        val plusNatural = avatarSpan + (if (accounts.isEmpty()) 0f else gapPx) - offset
        val pinned = plusNatural + itemPx > view
        val plusTop = if (pinned) (view - itemPx).coerceAtLeast(0f) else plusNatural.coerceAtLeast(0f)
        val passingUnderPlus = pinned && accounts.indices.any { index ->
            val top = index * (itemPx + gapPx) - offset
            top + itemPx > plusTop + 1f
        }
        val passingUnderAll = offset > 1f

        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(accounts.size, view) {
                    var job: kotlinx.coroutines.Job? = null
                    detectVerticalDragGestures(
                        onDragStart = { job?.cancel() },
                        onDragEnd = {
                            val target = offset.coerceIn(0f, maxScroll)
                            val start = offset
                            job = scope.launch {
                                Animatable(start).animateTo(
                                    target,
                                    spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMedium),
                                ) {
                                    offset = value
                                }
                            }
                        },
                        onVerticalDrag = { _, drag ->
                            val next = offset - drag
                            offset = when {
                                next < 0f -> next * 0.32f
                                next > maxScroll -> maxScroll + (next - maxScroll) * 0.32f
                                else -> next
                            }
                        },
                    )
                },
        ) {
            accounts.forEachIndexed { index, account ->
                val top = index * (itemPx + gapPx) - offset
                Box(
                    Modifier
                        .fillMaxWidth()
                        .offset { IntOffset(0, top.roundToInt()) }
                        .size(width = 76.dp, height = item),
                    contentAlignment = Alignment.Center,
                ) {
                    CircleButton(
                        label = account.name,
                        selected = account.id == selectedAccountId,
                        onClick = { onSelectAccount(account.id) },
                        container = avatarColor(account.name),
                        content = Color.White,
                    ) {
                        Text(avatarLabel(account.name), color = Color.White, fontSize = 14.sp)
                    }
                }
            }
            if (passingUnderPlus) {
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .offset { IntOffset(0, (plusTop - with(density) { 18.dp.toPx() }).roundToInt()) }
                        .fillMaxWidth()
                        .size(height = 18.dp, width = 76.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, MaterialTheme.colorScheme.surfaceContainerLow),
                            ),
                        ),
                )
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .offset { IntOffset(0, plusTop.roundToInt()) }
                    .size(width = 76.dp, height = item),
                contentAlignment = Alignment.Center,
            ) {
                CircleButton(
                    label = stringResource(R.string.account_add),
                    selected = false,
                    onClick = onAdd,
                    container = MaterialTheme.colorScheme.surface,
                    content = MaterialTheme.colorScheme.onSurface,
                ) {
                    Text("+", color = MaterialTheme.colorScheme.onSurface, fontSize = 22.sp)
                }
            }
        }
        if (passingUnderAll) {
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(16.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(MaterialTheme.colorScheme.surfaceContainerLow, Color.Transparent),
                        ),
                    ),
            )
        }
    }
}

@Composable
private fun CircleButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
    inside: @Composable () -> Unit,
) {
    val ring = if (selected) {
        Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
    } else {
        Modifier.border(1.dp, content.copy(alpha = 0.25f), CircleShape)
    }
    Box(
        modifier
            .size(48.dp)
            .then(ring)
            .padding(3.dp)
            .clip(CircleShape)
            .background(container)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        inside()
    }
}

@Composable
private fun AllAccountsMark() {
    Canvas(Modifier.size(22.dp)) {
        val radius = 2.6.dp.toPx()
        val gap = 5.dp.toPx()
        val center = this.center
        listOf(
            Offset(-gap, -gap),
            Offset(gap, -gap),
            Offset(-gap, gap),
            Offset(gap, gap),
        ).forEach { delta ->
            drawCircle(Color.White, radius, center + delta)
        }
    }
}

@Composable
private fun FolderColumn(
    currentName: String,
    folder: Folder,
    onSelectFolder: (Folder) -> Unit,
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
        Folder.entries.forEach { item ->
            val selected = item == folder
            Text(
                text = stringResource(item.label),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Start,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (selected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
                    )
                    .clickable { onSelectFolder(item) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            )
        }
    }
}
