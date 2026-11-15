package com.ainscel.ainsmail.ui.shell

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

internal const val MinListFraction = 0.35f
internal const val MaxListFraction = 0.60f

internal val SplitSpring = spring<Float>(
    dampingRatio = 0.86f,
    stiffness = Spring.StiffnessMediumLow,
)

internal class SplitDragState(
    val preferred: MutableFloatState,
) {
    val extra = mutableFloatStateOf(0f)
    val fullscreen = mutableFloatStateOf(0f)

    /** 只在跨过一半时变化，全屏按钮靠它换图标，弹簧本身不重组两栏。 */
    var fullscreenActive by mutableStateOf(false)
        private set

    var locked by mutableStateOf(false)
        private set
    var resizing by mutableStateOf(false)
        private set
    var detailOpen: Boolean = false
    var motion: Job? = null
    private var motionToken = 0
    private var fingerDown = false

    fun writeLocked(value: Boolean) {
        locked = value
        publishActive()
    }

    fun writeFullscreen(value: Float) {
        fullscreen.floatValue = value.coerceIn(0f, 1f)
        publishActive()
    }

    private fun publishActive() {
        val next = locked || fullscreen.floatValue > 0.5f
        if (next != fullscreenActive) fullscreenActive = next
    }

    /** 手指按下分界线。右栏沿用按下前的宽度，直到松开。 */
    fun holdResize() {
        fingerDown = true
        motionToken++
        motion?.cancel()
        motion = null
        resizing = true
    }

    /** 手指离开屏幕。不取消回弹，只是立刻允许右栏重排。 */
    fun releaseResize() {
        fingerDown = false
        resizing = false
    }

    /** 右栏关掉时停掉动画，并结束冻结。 */
    fun settle() {
        fingerDown = false
        motionToken++
        motion?.cancel()
        motion = null
        resizing = false
    }

    fun resetFullscreen() {
        settle()
        writeLocked(false)
        writeFullscreen(0f)
        extra.floatValue = 0f
    }

    private fun launchMotion(
        scope: CoroutineScope,
        pinWidth: Boolean,
        block: suspend () -> Unit,
    ) {
        motionToken++
        val token = motionToken
        motion?.cancel()
        motion = null
        // 只有点全屏按钮这种没有按住分界线的动画才自己冻结宽度。
        // 手指拖动时的回弹不冻结，松开就已经重排。
        if (pinWidth && !fingerDown) resizing = true
        motion = scope.launch {
            try {
                block()
            } finally {
                if (token == motionToken && pinWidth && !fingerDown) {
                    motion = null
                    resizing = false
                } else if (token == motionToken) {
                    motion = null
                }
            }
        }
    }

    fun basePx(total: Float): Float {
        return preferred.floatValue.coerceIn(MinListFraction, MaxListFraction) * total
    }

    fun listWidthPx(total: Float): Float {
        return basePx(total) * (1f - fullscreen.floatValue.coerceIn(0f, 1f))
    }

    fun dividerPx(total: Float): Float {
        val progress = fullscreen.floatValue.coerceIn(0f, 1f)
        return ((basePx(total) + extra.floatValue) * (1f - progress)).coerceIn(0f, total)
    }

    /** 0 是正常分栏，1 是正文铺满、分界线贴在屏幕左缘。 */
    fun animateTo(scope: CoroutineScope, target: Float, pinWidth: Boolean = false) {
        val start = fullscreen.floatValue
        val clamped = target.coerceIn(0f, 1f)
        launchMotion(scope, pinWidth) {
            Animatable(start).animateTo(clamped, SplitSpring) {
                writeFullscreen(value)
            }
            writeFullscreen(clamped)
            if (clamped >= 1f) {
                extra.floatValue = 0f
                writeLocked(true)
            } else {
                // 从越界位置收回时，先跟着进度回到分界，再把阻力位移弹回限度内。
                val extraStart = extra.floatValue
                if (abs(extraStart) < 0.5f) {
                    extra.floatValue = 0f
                } else {
                    Animatable(extraStart).animateTo(0f, SplitSpring) {
                        extra.floatValue = value
                    }
                    extra.floatValue = 0f
                }
            }
        }
    }

    fun springExtraHome(scope: CoroutineScope) {
        val start = extra.floatValue
        if (abs(start) < 0.5f) {
            extra.floatValue = 0f
            return
        }
        launchMotion(scope, pinWidth = false) {
            Animatable(start).animateTo(0f, SplitSpring) {
                extra.floatValue = value
            }
            extra.floatValue = 0f
        }
    }
}
