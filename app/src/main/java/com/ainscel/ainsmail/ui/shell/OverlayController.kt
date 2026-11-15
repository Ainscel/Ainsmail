package com.ainscel.ainsmail.ui.shell

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** 左侧遮罩的开合进度。0 是关上，1 是打开。按钮和返回键驱动。 */
class OverlayController {
    var progress by mutableFloatStateOf(0f)
        private set

    /** 跨过一条很浅的开合线才变化，返回键不要跟着每一帧进度重组。 */
    var obstructsBack by mutableStateOf(false)
        private set

    private var job: Job? = null

    fun open(scope: CoroutineScope) {
        animate(scope, 1f)
    }

    fun close(scope: CoroutineScope) {
        animate(scope, 0f)
    }

    private fun moveTo(value: Float) {
        progress = value
        val obstructs = value > 0.02f
        if (obstructs != obstructsBack) obstructsBack = obstructs
    }

    private fun animate(scope: CoroutineScope, target: Float) {
        job?.cancel()
        val start = progress
        job = scope.launch {
            Animatable(start).animateTo(
                target,
                spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMedium),
            ) {
                moveTo(value)
            }
        }
    }
}
