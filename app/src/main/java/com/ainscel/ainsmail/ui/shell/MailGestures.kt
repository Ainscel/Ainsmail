package com.ainscel.ainsmail.ui.shell

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

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
