package com.ainscel.ainsmail.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope

@Composable
internal fun SplitDivider(
    state: SplitDragState,
    scope: CoroutineScope,
    total: Float,
    rubberRange: Float,
    edgePx: Float,
    handlePx: Float,
    edgeHandlePx: Float,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .offset {
                val handle = placeHandle(state.dividerPx(total), handlePx, edgeHandlePx)
                IntOffset(handle.left.roundToInt(), 0)
            }
            .layout { measurable, constraints ->
                val handle = placeHandle(state.dividerPx(total), handlePx, edgeHandlePx)
                val width = handle.width.roundToInt().coerceAtLeast(1)
                val height = constraints.maxHeight
                val placeable = measurable.measure(Constraints.fixed(width, height))
                layout(width, height) {
                    placeable.place(0, 0)
                }
            }
            .splitDividerDrag(state, scope, total, rubberRange, edgePx, handlePx, edgeHandlePx),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .offset {
                    val dividerX = state.dividerPx(total)
                    val handle = placeHandle(dividerX, handlePx, edgeHandlePx)
                    IntOffset((dividerX - handle.left).roundToInt(), 0)
                }
                .width(1.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.outlineVariant),
        )
    }
}

/** 过左缘收成全屏、越界阻力和松手回弹都在这条手势里，不进页面组合。 */
private fun Modifier.splitDividerDrag(
    state: SplitDragState,
    scope: CoroutineScope,
    total: Float,
    rubberRange: Float,
    edgePx: Float,
    handlePx: Float,
    edgeHandlePx: Float,
): Modifier = pointerInput(total, rubberRange, edgePx, handlePx, edgeHandlePx) {
    var dragX = 0f
    var fingerX = 0f
    var committed = false
    var restoring = false
    var travel = 0f
    val minPx = MinListFraction * total
    val maxPx = MaxListFraction * total
    val slop = viewConfiguration.touchSlop
    fun finish() {
        // 全屏进度还在动时右栏保持冻结。越界回弹只动分界线，松手就按当前栏宽重排。
        val target = when {
            committed || (restoring && travel < slop) -> 1f
            restoring && state.fullscreen.floatValue > 0.01f && dragX < minPx -> 0f
            else -> null
        }
        if (target == 1f && !committed) state.writeLocked(true)
        if (target == 0f) {
            state.writeLocked(false)
            state.extra.floatValue = 0f
        }
        state.onFingerUp(scope, target)
    }
    detectHorizontalDragGestures(
        onDragStart = { offset ->
            state.holdResize()
            committed = false
            travel = 0f
            val shown = state.dividerPx(total)
            val placement = placeHandle(shown, handlePx, edgeHandlePx)
            fingerX = placement.left + offset.x
            dragX = shown
            restoring = state.fullscreen.floatValue > 0.01f
        },
        onDragEnd = { finish() },
        onDragCancel = { finish() },
        onHorizontalDrag = { _, delta ->
            if (committed) return@detectHorizontalDragGestures
            travel += abs(delta)
            val previousFinger = fingerX
            fingerX += delta
            dragX = (dragX + delta).coerceIn(-rubberRange, total + rubberRange)
            val crossedIntoEdge = state.detailOpen &&
                dragX < minPx &&
                fingerX <= edgePx &&
                previousFinger > edgePx
            if (crossedIntoEdge) {
                committed = true
                restoring = false
                state.writeLocked(true)
                state.animateTo(scope, 1f)
                return@detectHorizontalDragGestures
            }
            if (restoring && dragX < minPx) {
                val base = state.basePx(total).coerceAtLeast(1f)
                val shown = dragX.coerceAtLeast(0f)
                state.extra.floatValue = 0f
                state.writeFullscreen((1f - shown / base).coerceIn(0f, 1f))
                state.writeLocked(state.fullscreen.floatValue > 0.98f)
                return@detectHorizontalDragGestures
            }
            if (restoring) {
                restoring = false
                state.writeLocked(false)
                state.writeFullscreen(0f)
                state.extra.floatValue = 0f
            }
            when {
                dragX < minPx -> {
                    state.preferred.floatValue = MinListFraction
                    state.extra.floatValue = resistPastEdge(dragX - minPx, rubberRange)
                }
                dragX > maxPx -> {
                    state.preferred.floatValue = MaxListFraction
                    state.extra.floatValue = resistPastEdge(dragX - maxPx, rubberRange)
                }
                else -> {
                    state.preferred.floatValue = dragX / total
                    state.extra.floatValue = 0f
                }
            }
        },
    )
}

/** 越界越拖越沉，视觉位移趋近一段固定距离，不会把分界线甩出屏幕。 */
private fun resistPastEdge(pastPx: Float, rangePx: Float): Float {
    if (pastPx == 0f || rangePx <= 0f) return 0f
    val sign = if (pastPx < 0f) -1f else 1f
    val distance = abs(pastPx)
    return sign * rangePx * (1f - 1f / (distance / rangePx + 1f))
}

private data class HandlePlacement(val left: Float, val width: Float)

/** 分界线贴左缘时，触摸条留在屏幕内，避免只剩一半在外面。 */
private fun placeHandle(dividerX: Float, normalPx: Float, edgePx: Float): HandlePlacement {
    val pin = dividerX <= normalPx / 2f
    val width = if (pin) edgePx else normalPx
    val left = if (pin) 0f else dividerX - width / 2f
    return HandlePlacement(left, width)
}
