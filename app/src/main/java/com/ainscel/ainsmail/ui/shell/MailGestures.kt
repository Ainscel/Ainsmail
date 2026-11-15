package com.ainscel.ainsmail.ui.shell

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 横向位移明显大于纵向时才接手，之前不消费事件，列表还能上下滑。
 * 屏幕左右边缘的一段留给系统返回。
 */
fun Modifier.horizontalDragOrScroll(
    enabled: Boolean = true,
    excludeStartEdge: Boolean = false,
    excludeEndEdge: Boolean = false,
    onDelta: (Float) -> Unit,
    onFinish: (Float) -> Unit,
): Modifier = pointerInput(enabled, excludeStartEdge, excludeEndEdge) {
    if (!enabled) return@pointerInput
    val startInset = if (excludeStartEdge) 24.dp.toPx() else 0f
    val endInset = if (excludeEndEdge) 24.dp.toPx() else 0f
    val slop = viewConfiguration.touchSlop
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val width = size.width.toFloat()
        if (down.position.x <= startInset || down.position.x >= width - endInset) {
            return@awaitEachGesture
        }
        var totalX = 0f
        var totalY = 0f
        var locked = false
        while (true) {
            val event = awaitPointerEvent(
                pass = if (locked) PointerEventPass.Initial else PointerEventPass.Main,
            )
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) {
                if (locked) onFinish(totalX)
                break
            }
            val delta = change.position - change.previousPosition
            totalX += delta.x
            totalY += delta.y
            if (!locked) {
                if (abs(totalY) > slop && abs(totalY) >= abs(totalX)) return@awaitEachGesture
                if (abs(totalX) > slop && abs(totalX) > abs(totalY)) {
                    locked = true
                    change.consume()
                    onDelta(totalX)
                }
            } else {
                change.consume()
                onDelta(delta.x)
            }
        }
    }
}

/**
 * 头像列的纵向拖动。位移只在布局阶段读，滚动时不重组圆钮。
 * 拉出内容范围时变沉，松手弹回。
 */
internal fun Modifier.verticalRubberScroll(
    scope: CoroutineScope,
    viewKey: Any,
    itemCount: Int,
    offset: () -> Float,
    maxOffset: () -> Float,
    onOffset: (Float) -> Unit,
): Modifier = pointerInput(viewKey, itemCount) {
    var job: Job? = null
    detectVerticalDragGestures(
        onDragStart = { job?.cancel() },
        onDragEnd = {
            val limit = maxOffset()
            val target = offset().coerceIn(0f, limit)
            val start = offset()
            job = scope.launch {
                Animatable(start).animateTo(
                    target,
                    spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMedium),
                ) {
                    onOffset(value)
                }
            }
        },
        onVerticalDrag = { _, drag ->
            val limit = maxOffset()
            val next = offset() - drag
            onOffset(
                when {
                    next < 0f -> next * 0.32f
                    next > limit -> limit + (next - limit) * 0.32f
                    else -> next
                },
            )
        },
    )
}
