package com.ainscel.ainsmail.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ainscel.ainsmail.R
import com.ainscel.ainsmail.ui.mailbox.AccountUi
import kotlin.math.roundToInt

@Composable
internal fun AvatarScroller(
    accounts: List<AccountUi>,
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
    val fade = MaterialTheme.colorScheme.surfaceContainerLow
    BoxWithConstraints(modifier.clipToBounds()) {
        val itemPx = with(density) { item.toPx() }
        val gapPx = with(density) { gap.toPx() }
        val fadePx = with(density) { 18.dp.toPx() }
        val view = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val avatarSpan = if (accounts.isEmpty()) {
            0f
        } else {
            accounts.size * itemPx + (accounts.size - 1) * gapPx
        }
        val trailing = if (accounts.isEmpty()) 0f else gapPx + itemPx
        val maxScroll = (avatarSpan + trailing - view).coerceAtLeast(0f)
        Box(
            Modifier
                .fillMaxSize()
                .verticalRubberScroll(
                    scope = scope,
                    viewKey = view,
                    itemCount = accounts.size,
                    offset = { offset },
                    maxOffset = { maxScroll },
                    onOffset = { offset = it },
                ),
        ) {
            accounts.forEachIndexed { index, account ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .offset {
                            val top = index * (itemPx + gapPx) - offset
                            IntOffset(0, top.roundToInt())
                        }
                        .size(width = 76.dp, height = item),
                    contentAlignment = Alignment.Center,
                ) {
                    AccountFace(
                        account = account,
                        selected = account.id == selectedAccountId,
                        onClick = { onSelectAccount(account.id) },
                    )
                }
            }
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .offset {
                        val plus = plusPlacement(offset, avatarSpan, gapPx, itemPx, view, accounts.size)
                        IntOffset(0, (plus.top - fadePx).roundToInt())
                    }
                    .graphicsLayer {
                        alpha = if (
                            plusPlacement(offset, avatarSpan, gapPx, itemPx, view, accounts.size).fade
                        ) {
                            1f
                        } else {
                            0f
                        }
                    }
                    .fillMaxWidth()
                    .size(height = 18.dp, width = 76.dp)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, fade))),
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .offset {
                        val plus = plusPlacement(offset, avatarSpan, gapPx, itemPx, view, accounts.size)
                        IntOffset(0, plus.top.roundToInt())
                    }
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
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(16.dp)
                .graphicsLayer { alpha = if (offset > 1f) 1f else 0f }
                .background(Brush.verticalGradient(listOf(fade, Color.Transparent))),
        )
    }
}

private data class PlusPlacement(val top: Float, val fade: Boolean)

/** 加号没到底就跟在最后一个头像后面；到底就停住，并在有头像钻进去时淡化。 */
private fun plusPlacement(
    offset: Float,
    avatarSpan: Float,
    gapPx: Float,
    itemPx: Float,
    view: Float,
    accountCount: Int,
): PlusPlacement {
    val lead = if (accountCount == 0) 0f else gapPx
    val natural = avatarSpan + lead - offset
    val pinned = natural + itemPx > view
    val top = if (pinned) (view - itemPx).coerceAtLeast(0f) else natural.coerceAtLeast(0f)
    val fade = pinned && (0 until accountCount).any { index ->
        val itemTop = index * (itemPx + gapPx) - offset
        itemTop + itemPx > top + 1f
    }
    return PlusPlacement(top, fade)
}
