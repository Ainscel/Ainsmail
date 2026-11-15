package com.ainscel.ainsmail.ui.shell

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** 宽屏分栏里，正文不需要自己再画返回。窄屏时这个值是 true。 */
val LocalDetailHandlesBack = staticCompositionLocalOf { true }

/**
 * 宽屏才提供。窄屏是 null，正文不画全屏按钮。
 * active 为 true 时正文已经铺满，或正在收向左边缘。
 */
val LocalDetailFullscreen = staticCompositionLocalOf<DetailFullscreenControls?> { null }

/**
 * 宽屏分界线是否还在动。右栏用它在布局阶段决定要不要沿用上一次的测量宽度。
 * 窄屏没有分栏，这个值是 null。
 */
val LocalSplitResize = staticCompositionLocalOf<SplitResize?> { null }

class SplitResize(val isResizing: () -> Boolean)

class DetailFullscreenControls(
    val active: Boolean,
    val toggle: () -> Unit,
)

private val WideThreshold = 640.dp
private const val MIN_LIST_FRACTION = 0.35f
private const val MAX_LIST_FRACTION = 0.60f
private val HandleWidth = 20.dp
private val EdgeHandleWidth = 36.dp

/** 手指进入屏幕左缘这一带，才取消阻尼并把右栏收成全屏。 */
private val FullscreenFingerEdge = 32.dp

private val SplitSpring = spring<Float>(
    dampingRatio = 0.86f,
    stiffness = Spring.StiffnessMediumLow,
)

fun isWideWindow(width: androidx.compose.ui.unit.Dp): Boolean = width >= WideThreshold

/**
 * 宽屏左右分栏。不认具体页面：左边放什么、右边放什么由调用方决定。
 * 比例全局一份。右侧有内容时，手指向左滑到屏幕左缘会把右栏收成全屏。
 */
@Composable
fun SplitLayout(
    preferredFraction: MutableFloatState,
    rightOpen: Boolean,
    content: SplitSlots.() -> Unit,
) {
    val slots = SplitSlots().apply(content)
    val state = remember(preferredFraction) { SplitDragState(preferredFraction) }
    val resize = remember(state) { SplitResize { state.resizing } }
    val scope = rememberCoroutineScope()
    SideEffect { state.detailOpen = rightOpen }
    LaunchedEffect(rightOpen) {
        if (!rightOpen && (state.locked || state.fullscreen.floatValue > 0f)) {
            state.settle()
            state.locked = false
            state.fullscreen.floatValue = 0f
            state.extra.floatValue = 0f
        }
    }
    val controls = DetailFullscreenControls(
        active = state.locked || state.fullscreen.floatValue > 0.5f,
        toggle = {
            val activeNow = state.locked || state.fullscreen.floatValue > 0.5f
            if (activeNow) {
                state.locked = false
                if (state.fullscreen.floatValue > 0.98f) state.extra.floatValue = 0f
                state.animateTo(scope, 0f, pinWidth = true)
            } else if (state.detailOpen) {
                state.locked = true
                state.animateTo(scope, 1f, pinWidth = true)
            }
        },
    )
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val total = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        // 左栏宽度在 layout 里读。分界线位置只交给下面的触摸条，避免拖动时重组左右两栏。
        val handlePx = with(density) { HandleWidth.toPx() }
        val edgeHandlePx = with(density) { EdgeHandleWidth.toPx() }
        val edgePx = with(density) { FullscreenFingerEdge.toPx() }
        val rubberRange = with(density) { 180.dp.toPx() }
        CompositionLocalProvider(
            LocalDetailFullscreen provides controls,
            LocalSplitResize provides resize,
        ) {
            Box(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxSize()) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .clipToBounds()
                            .layout { measurable, rowConstraints ->
                                val width = state.listWidthPx(total)
                                    .roundToInt()
                                    .coerceIn(0, rowConstraints.maxWidth)
                                val placeable = measurable.measure(
                                    rowConstraints.copy(minWidth = width, maxWidth = width),
                                )
                                layout(width, placeable.height) {
                                    placeable.placeRelative(0, 0)
                                }
                            },
                    ) {
                        slots.list()
                    }
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clipToBounds(),
                    ) {
                        slots.detail()
                    }
                }
                SplitDivider(
                    state = state,
                    scope = scope,
                    total = total,
                    rubberRange = rubberRange,
                    edgePx = edgePx,
                    handlePx = handlePx,
                    edgeHandlePx = edgeHandlePx,
                    modifier = Modifier.zIndex(1f),
                )
            }
        }
    }
}

/**
 * 分界线触摸条。比例只在这个小组件里读，拖动时不会把左右两栏重新组合。
 * 位置用 offset，触摸区域跟着线走。
 */
@Composable
private fun SplitDivider(
    state: SplitDragState,
    scope: CoroutineScope,
    total: Float,
    rubberRange: Float,
    edgePx: Float,
    handlePx: Float,
    edgeHandlePx: Float,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val dividerX = state.dividerPx(total)
    val handle = placeHandle(dividerX, handlePx, edgeHandlePx)
    Box(
        modifier
            .offset { IntOffset(handle.left.roundToInt(), 0) }
            .width(with(density) { handle.width.toDp() })
            .fillMaxHeight()
            .pointerInput(total, rubberRange, edgePx, handlePx, edgeHandlePx) {
                            var dragX = 0f
                            var fingerX = 0f
                            var committed = false
                            var restoring = false
                            var travel = 0f
                            val minPx = MIN_LIST_FRACTION * total
                            val maxPx = MAX_LIST_FRACTION * total
                            val slop = viewConfiguration.touchSlop
                            fun finish() {
                                // 手指已离开。回弹可以接着走，右栏立刻按当前宽度重排。
                                state.releaseResize()
                                if (committed) {
                                    if (state.motion == null && state.fullscreen.floatValue < 1f) {
                                        state.animateTo(scope, 1f)
                                    }
                                    return
                                }
                                if (restoring && travel < slop) {
                                    state.locked = true
                                    if (state.fullscreen.floatValue < 1f) state.animateTo(scope, 1f)
                                    return
                                }
                                if (restoring && state.fullscreen.floatValue > 0.01f && dragX < minPx) {
                                    state.locked = false
                                    state.extra.floatValue = 0f
                                    state.animateTo(scope, 0f)
                                    return
                                }
                                state.springExtraHome(scope)
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
                                    // 从右侧跨进屏幕左缘，并且右栏开着内容，才收成全屏。
                                    val crossedIntoEdge = state.detailOpen &&
                                        dragX < minPx &&
                                        fingerX <= edgePx &&
                                        previousFinger > edgePx
                                    if (crossedIntoEdge) {
                                        committed = true
                                        restoring = false
                                        state.locked = true
                                        state.animateTo(scope, 1f)
                                        return@detectHorizontalDragGestures
                                    }
                                    if (restoring && dragX < minPx) {
                                        val base = state.basePx(total).coerceAtLeast(1f)
                                        val shown = dragX.coerceAtLeast(0f)
                                        state.extra.floatValue = 0f
                                        state.fullscreen.floatValue = (1f - shown / base).coerceIn(0f, 1f)
                                        state.locked = state.fullscreen.floatValue > 0.98f
                                        return@detectHorizontalDragGestures
                                    }
                                    if (restoring) {
                                        restoring = false
                                        state.locked = false
                                        state.fullscreen.floatValue = 0f
                                        state.extra.floatValue = 0f
                                    }
                                    when {
                                        dragX < minPx -> {
                                            state.preferred.floatValue = MIN_LIST_FRACTION
                                            state.extra.floatValue = resistPastEdge(dragX - minPx, rubberRange)
                                        }
                                        dragX > maxPx -> {
                                            state.preferred.floatValue = MAX_LIST_FRACTION
                                            state.extra.floatValue = resistPastEdge(dragX - maxPx, rubberRange)
                                        }
                                        else -> {
                                            state.preferred.floatValue = dragX / total
                                            state.extra.floatValue = 0f
                                        }
                                    }
                                },
                            )
                        },
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                Modifier
                    .offset { IntOffset((dividerX - handle.left).roundToInt(), 0) }
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )
        }
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

private class SplitDragState(
    val preferred: MutableFloatState,
) {
    val extra = mutableFloatStateOf(0f)
    val fullscreen = mutableFloatStateOf(0f)
    var locked by mutableStateOf(false)
    var resizing by mutableStateOf(false)
    var detailOpen: Boolean = false
    var motion: Job? = null
    private var motionToken = 0
    private var fingerDown = false

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
        return preferred.floatValue.coerceIn(MIN_LIST_FRACTION, MAX_LIST_FRACTION) * total
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
        launchMotion(scope, pinWidth) {
            Animatable(start).animateTo(target.coerceIn(0f, 1f), SplitSpring) {
                fullscreen.floatValue = value.coerceIn(0f, 1f)
            }
            fullscreen.floatValue = target.coerceIn(0f, 1f)
            if (target >= 1f) {
                extra.floatValue = 0f
                locked = true
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

class SplitSlots {
    var list: @Composable () -> Unit = {}
    var detail: @Composable () -> Unit = {}

    fun list(content: @Composable () -> Unit) {
        list = content
    }

    fun detail(content: @Composable () -> Unit) {
        detail = content
    }
}
