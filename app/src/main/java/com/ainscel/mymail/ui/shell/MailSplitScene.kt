package com.ainscel.mymail.ui.shell

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
import androidx.compose.runtime.MutableFloatState
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.NavMetadataKey
import androidx.navigation3.runtime.contains
import androidx.navigation3.runtime.metadata
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import com.ainscel.mymail.ui.detail.EmptyDetail
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** 宽屏分栏里，正文不需要自己再画返回。窄屏时这个值是 true。 */
val LocalDetailHandlesBack = staticCompositionLocalOf { true }

private val WideThreshold = 640.dp
private const val MIN_LIST_FRACTION = 0.30f
private const val MAX_LIST_FRACTION = 0.60f

/**
 * 窗口放得下大约 640dp 时并排。
 * 列表默认占 40%。拖动时列表停在 30% 到 60%。
 * 再往外拖，两边内容宽度不动，分界线盖住旁边的内容，松手弹回。
 */
class MailSplitStrategy(
    private val isWide: Boolean,
    private val preferredFraction: MutableFloatState,
) : SceneStrategy<NavKey> {
    override fun SceneStrategyScope<NavKey>.calculateScene(entries: List<NavEntry<NavKey>>): Scene<NavKey>? {
        if (!isWide) return null
        val listEntry = entries.findLast { it.metadata.contains(ListKey) } ?: return null
        val top = entries.last()
        val topIsList = top.metadata.contains(ListKey)
        val topIsDetail = top.metadata.contains(DetailKey)
        if (!topIsList && !topIsDetail) return null
        return MailSplitScene(
            key = listEntry.contentKey,
            previousEntries = entries.dropLast(1),
            listEntry = listEntry,
            detailEntry = top.takeIf { topIsDetail },
            preferredFraction = preferredFraction,
        )
    }

    companion object {
        private object ListKey : NavMetadataKey<Boolean>
        private object DetailKey : NavMetadataKey<Boolean>

        fun listPane() = metadata { put(ListKey, true) }
        fun detailPane() = metadata { put(DetailKey, true) }
    }
}

data class MailSplitScene(
    override val key: Any,
    override val previousEntries: List<NavEntry<NavKey>>,
    private val listEntry: NavEntry<NavKey>,
    private val detailEntry: NavEntry<NavKey>?,
    private val preferredFraction: MutableFloatState,
) : Scene<NavKey> {
    override val entries: List<NavEntry<NavKey>> = listOfNotNull(listEntry, detailEntry)

    override val content: @Composable () -> Unit = {
        MailSplitLayout(preferredFraction = preferredFraction) {
            list { listEntry.Content() }
            detail {
                CompositionLocalProvider(LocalDetailHandlesBack provides false) {
                    if (detailEntry == null) EmptyDetail() else detailEntry.Content()
                }
            }
        }
    }
}

@Composable
fun rememberMailSplitStrategy(widthIsWide: Boolean, preferredFraction: MutableFloatState): MailSplitStrategy {
    return remember(widthIsWide, preferredFraction) {
        MailSplitStrategy(widthIsWide, preferredFraction)
    }
}

fun isWideWindow(width: androidx.compose.ui.unit.Dp): Boolean = width >= WideThreshold

@Composable
private fun MailSplitLayout(
    preferredFraction: MutableFloatState,
    content: SplitSlots.() -> Unit,
) {
    val slots = SplitSlots().apply(content)
    val extra = remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    var settle by remember { mutableStateOf<Job?>(null) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val total = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val fraction = preferredFraction.floatValue.coerceIn(MIN_LIST_FRACTION, MAX_LIST_FRACTION)
        val contentWidth = fraction * total
        val dividerX = (contentWidth + extra.floatValue).coerceIn(0f, total)
        val handleWidth = 20.dp
        val handlePx = with(density) { handleWidth.toPx() }
        val rubberRange = with(density) { 180.dp.toPx() }
        Box(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .width(with(density) { contentWidth.toDp() })
                        .fillMaxHeight()
                        .clipToBounds(),
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
            Box(
                Modifier
                    .zIndex(1f)
                    .offset { IntOffset((dividerX - handlePx / 2f).roundToInt(), 0) }
                    .width(handleWidth)
                    .fillMaxHeight()
                    .pointerInput(total, rubberRange) {
                        var dragX = contentWidth
                        detectHorizontalDragGestures(
                            onDragStart = {
                                settle?.cancel()
                                val shown = preferredFraction.floatValue
                                    .coerceIn(MIN_LIST_FRACTION, MAX_LIST_FRACTION) * total
                                dragX = shown + extra.floatValue
                            },
                            onDragEnd = { settle = springExtraBack(scope, extra) },
                            onDragCancel = { settle = springExtraBack(scope, extra) },
                            onHorizontalDrag = { _, delta ->
                                dragX = (dragX + delta).coerceIn(-rubberRange, total + rubberRange)
                                val minPx = MIN_LIST_FRACTION * total
                                val maxPx = MAX_LIST_FRACTION * total
                                when {
                                    dragX < minPx -> {
                                        preferredFraction.floatValue = MIN_LIST_FRACTION
                                        extra.floatValue = resistPastEdge(dragX - minPx, rubberRange)
                                    }
                                    dragX > maxPx -> {
                                        preferredFraction.floatValue = MAX_LIST_FRACTION
                                        extra.floatValue = resistPastEdge(dragX - maxPx, rubberRange)
                                    }
                                    else -> {
                                        preferredFraction.floatValue = dragX / total
                                        extra.floatValue = 0f
                                    }
                                }
                            },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.outlineVariant),
                )
            }
        }
    }
}

/** 越界越拖越沉，视觉位移趋近一段固定距离，不会把分界线甩出屏幕。 */
private fun resistPastEdge(pastPx: Float, rangePx: Float): Float {
    if (pastPx == 0f || rangePx <= 0f) return 0f
    val sign = if (pastPx < 0f) -1f else 1f
    val distance = abs(pastPx)
    return sign * rangePx * (1f - 1f / (distance / rangePx + 1f))
}

private fun springExtraBack(
    scope: kotlinx.coroutines.CoroutineScope,
    extra: MutableFloatState,
): Job {
    val start = extra.floatValue
    return scope.launch {
        Animatable(start).animateTo(
            0f,
            spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium),
        ) {
            extra.floatValue = value
        }
    }
}

private class SplitSlots {
    var list: @Composable () -> Unit = {}
    var detail: @Composable () -> Unit = {}

    fun list(content: @Composable () -> Unit) {
        list = content
    }

    fun detail(content: @Composable () -> Unit) {
        detail = content
    }
}
