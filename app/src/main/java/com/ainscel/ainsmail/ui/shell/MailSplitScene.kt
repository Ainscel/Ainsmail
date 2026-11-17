package com.ainscel.ainsmail.ui.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt

/** 宽屏分栏里，正文不需要自己再画返回。窄屏时这个值是 true。 */
val LocalDetailHandlesBack = staticCompositionLocalOf { true }

/**
 * 宽屏才提供。窄屏是 null，正文不画全屏按钮。
 * [DetailFullscreenControls.isActive] 只在跨过一半时变化。
 */
val LocalDetailFullscreen = staticCompositionLocalOf<DetailFullscreenControls?> { null }

/**
 * 宽屏分界线是否还在动。右栏用它在布局阶段决定要不要沿用上一次的测量宽度。
 * 窄屏没有分栏，这个值是 null。
 */
val LocalSplitResize = staticCompositionLocalOf<SplitResize?> { null }

class SplitResize(val isResizing: () -> Boolean)

class DetailFullscreenControls(
    val isActive: () -> Boolean,
    val toggle: () -> Unit,
)

private val WideThreshold = 640.dp
private val HandleWidth = 20.dp
private val EdgeHandleWidth = 36.dp

/** 手指进入屏幕左缘这一带，才取消阻尼并把右栏收成全屏。 */
private val FullscreenFingerEdge = 32.dp

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
            state.resetFullscreen()
        }
    }
    val controls = remember(state, scope) {
        DetailFullscreenControls(
            isActive = { state.fullscreenActive },
            toggle = {
                if (state.fullscreenActive) {
                    state.writeLocked(false)
                    if (state.fullscreen.floatValue > 0.98f) state.extra.floatValue = 0f
                    state.animateTo(scope, 0f)
                } else if (state.detailOpen) {
                    state.writeLocked(true)
                    state.animateTo(scope, 1f)
                }
            },
        )
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val total = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        // 左栏宽度在 layout 里读。分界线位置只交给触摸条，拖动时不重组左右两栏。
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
