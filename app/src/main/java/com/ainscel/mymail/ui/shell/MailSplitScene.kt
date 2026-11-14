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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
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
private val ComfortableList = 280.dp
private val ComfortableDetail = 360.dp

/**
 * 窗口放得下 280dp 列表和 360dp 正文时并排。
 * 默认列表约占 34%。拖进舒适范围会记住比例，拖过之后只跟着走一段，松手弹回。
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
        val minList = with(density) { ComfortableList.toPx() }
        val minDetail = with(density) { ComfortableDetail.toPx() }
        val minFraction = (minList / total).coerceAtMost(0.9f)
        val maxFraction = ((total - minDetail) / total).coerceAtLeast(minFraction)
        val base = preferredFraction.floatValue.coerceIn(minFraction, maxFraction) * total
        val visual = (base + extra.floatValue).coerceIn(total * 0.12f, total * 0.88f)
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.width(with(density) { visual.toDp() }).fillMaxHeight()) {
                slots.list()
            }
            Box(
                Modifier
                    .width(16.dp)
                    .fillMaxHeight()
                    .pointerInput(total, minFraction, maxFraction) {
                        var widthPx = base
                        detectHorizontalDragGestures(
                            onDragStart = {
                                settle?.cancel()
                                val shown = preferredFraction.floatValue
                                    .coerceIn(minFraction, maxFraction) * total + extra.floatValue
                                widthPx = shown
                            },
                            onDragEnd = { settle = springExtraBack(scope, extra) },
                            onDragCancel = { settle = springExtraBack(scope, extra) },
                            onHorizontalDrag = { _, delta ->
                                widthPx += delta
                                val minPx = minFraction * total
                                val maxPx = maxFraction * total
                                if (minPx < maxPx - 1f && widthPx in minPx..maxPx) {
                                    preferredFraction.floatValue = widthPx / total
                                    extra.floatValue = 0f
                                } else {
                                    val edge = if (minPx >= maxPx) minPx else widthPx.coerceIn(minPx, maxPx)
                                    extra.floatValue = (widthPx - edge) * 0.35f
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
            Box(Modifier.weight(1f).fillMaxHeight()) {
                slots.detail()
            }
        }
    }
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
