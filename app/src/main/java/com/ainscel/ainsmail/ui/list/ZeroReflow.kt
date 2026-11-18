package com.ainscel.ainsmail.ui.list

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp

/**
 * 文本测量用的固定上限。必须是常量，且宽于任何真实列表栏，
 * 拖分界线时传给 Text 的约束才不会变，才能命中测量缓存。
 */
internal val TextMeasureWidth = 3000.dp

/**
 * 单行文字按固定宽度测量，字形放进尺寸不变的层，对外仍是当前槽宽。
 * 右缘只做硬裁切，不画淡出。用来把淡出渐变从拖动开销里拿掉。
 */
fun Modifier.zeroReflowFadeText(): Modifier = this
    .clipToBounds()
    .then(SlotWidthElement)
    .graphicsLayer()

/**
 * 顶栏左右两槽：测量用固定宽松约束，右侧按钮只在摆放阶段贴右。
 * 用来替代 Row + weight，避免拖分界线时标题和按钮组反复重测。
 */
@Composable
fun ZeroReflowTopBar(
    modifier: Modifier = Modifier,
    leftTitle: @Composable () -> Unit,
    rightActions: @Composable () -> Unit,
) {
    Layout(
        content = {
            leftTitle()
            rightActions()
        },
        modifier = modifier.clipToBounds(),
    ) { measurables, constraints ->
        val actualWidth = constraints.maxWidth
        val fixedLoose = Constraints(minWidth = 0, maxWidth = TextMeasureWidth.roundToPx())
        // 右侧按钮组约束固定，拖动时不重测；左侧标题只改可见槽宽。
        val rightPlaceable = measurables[1].measure(fixedLoose)
        val leftSlot = (actualWidth - rightPlaceable.width).coerceAtLeast(0)
        val leftPlaceable = measurables[0].measure(
            Constraints(minWidth = 0, maxWidth = leftSlot),
        )
        val height = maxOf(leftPlaceable.height, rightPlaceable.height)
            .coerceAtLeast(constraints.minHeight)

        layout(actualWidth, height) {
            leftPlaceable.placeRelative(
                x = 0,
                y = (height - leftPlaceable.height) / 2,
            )
            rightPlaceable.placeRelative(
                x = (actualWidth - rightPlaceable.width).coerceAtLeast(0),
                y = (height - rightPlaceable.height) / 2,
            )
        }
    }
}

/**
 * 对外尺寸是当前槽。传给内层的约束锁在 [TextMeasureWidth]，
 * 拖动时文字层可以跳过测量。
 */
private object SlotWidthElement : ModifierNodeElement<SlotWidthNode>() {
    override fun create(): SlotWidthNode = SlotWidthNode()

    override fun update(node: SlotWidthNode) = Unit

    override fun equals(other: Any?): Boolean = other is SlotWidthElement

    override fun hashCode(): Int = 0x5107_7E57
}

private class SlotWidthNode : Modifier.Node(), LayoutModifierNode {
    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        val fixed = Constraints(minWidth = 0, maxWidth = TextMeasureWidth.roundToPx())
        val placeable = measurable.measure(fixed)
        val slot = if (constraints.hasBoundedWidth) {
            constraints.maxWidth.coerceAtLeast(0)
        } else {
            placeable.width
        }
        return layout(slot, placeable.height) {
            placeable.placeRelative(0, 0)
        }
    }
}
