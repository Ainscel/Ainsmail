package com.ainscel.mymail.ui.shell

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** 左侧遮罩的开合进度。0 是关上，1 是打开，超出这段时是拉过头的阻力。 */
class OverlayController {
    var progress by mutableFloatStateOf(0f)
    private var job: Job? = null

    fun dragBy(dx: Float, widthPx: Float) {
        if (widthPx <= 0f) return
        job?.cancel()
        progress = rubber(progress + dx / widthPx)
    }

    fun release(scope: CoroutineScope) {
        animate(scope, if (progress > 0.5f) 1f else 0f)
    }

    fun open(scope: CoroutineScope) {
        animate(scope, 1f)
    }

    fun close(scope: CoroutineScope) {
        animate(scope, 0f)
    }

    private fun animate(scope: CoroutineScope, target: Float) {
        job?.cancel()
        val start = progress
        job = scope.launch {
            Animatable(start).animateTo(
                target,
                spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMedium),
            ) {
                progress = value
            }
        }
    }

    private fun rubber(value: Float): Float = when {
        value < 0f -> value * 0.28f
        value > 1f -> 1f + (value - 1f) * 0.28f
        else -> value
    }
}
