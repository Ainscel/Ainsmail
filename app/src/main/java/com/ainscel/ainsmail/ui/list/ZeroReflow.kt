package com.ainscel.ainsmail.ui.list

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 文本测量用的固定上限。必须是常量，且宽于任何真实列表栏，
 * 拖分界线时传给 Text 的约束才不会变，才能命中测量缓存。
 */
internal val TextMeasureWidth = 3000.dp

/** 右缘透明淡出默认宽度。 */
val MailTextFadeWidth = 36.dp

/** DstIn 只看源的透明度：靠左不透明，靠右全透。 */
private val FadeAlphaColors = listOf(Color.Black, Color.Transparent)

/**
 * 单行文字按固定宽度测量，字形放进尺寸不变的层。
 * 对外仍是当前槽宽。淡出只把右缘一条固定宽度的字变透明，好让背后的颜色透出来。
 * 槽宽变化时重画的是这一条，不是整行字形。
 */
fun Modifier.zeroReflowFadeText(
    rightInset: Dp = 0.dp,
    fadeWidth: Dp = MailTextFadeWidth,
): Modifier = this
    // 槽宽不变时，裁切和淡出带的结果留在这层，滑动不必每帧重做小缓冲。
    // 不要改成 Offscreen：那会把整行缓冲做成槽宽，拖动时又整段重录。
    .graphicsLayer()
    .then(FadeMaskElement(rightInset, fadeWidth))
    .then(SlotWidthElement)
    // 字形层。内侧约束由 [SlotWidthElement] 锁死，拖动不改变这一层的尺寸。
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
 * 只有绘制、没有测量，这样才会挂在槽宽那一层上。
 * 淡出若和测量写在同一个 Node 里，Compose 会把绘制交给内侧文字层，位置就钉在字形末尾。
 */
private class FadeMaskElement(
    val rightInset: Dp,
    val fadeWidth: Dp,
) : ModifierNodeElement<FadeMaskNode>() {
    override fun create(): FadeMaskNode = FadeMaskNode(rightInset, fadeWidth)

    override fun update(node: FadeMaskNode) {
        node.rightInset = rightInset
        node.fadeWidth = fadeWidth
        node.invalidateDraw()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        val o = other as? FadeMaskElement ?: return false
        return rightInset == o.rightInset && fadeWidth == o.fadeWidth
    }

    override fun hashCode(): Int = 31 * rightInset.hashCode() + fadeWidth.hashCode()
}

private class FadeMaskNode(
    var rightInset: Dp,
    var fadeWidth: Dp,
) : Modifier.Node(), DrawModifierNode {

    private val layerPaint = Paint()

    override fun ContentDrawScope.draw() {
        val width = size.width
        val height = size.height
        if (width <= 0f || height <= 0f) return
        val visibleEnd = (width - rightInset.toPx()).coerceIn(0f, width)
        val fade = fadeWidth.toPx().coerceAtMost(visibleEnd)
        val fadeStart = visibleEnd - fade
        // 右间距里的字不画，背后的颜色直接露出来。
        clipRect(right = visibleEnd) {
            if (fadeStart > 0f) {
                clipRect(right = fadeStart) {
                    this@draw.drawContent()
                }
            }
            if (fade > 0f) {
                this@draw.drawFadeStrip(fadeStart, visibleEnd)
            }
        }
    }

    /** 淡出带的宽高不随槽宽变化，变的只是它盖住字形的哪一段。 */
    private fun ContentDrawScope.drawFadeStrip(start: Float, end: Float) {
        val canvas = drawContext.canvas
        canvas.saveLayer(Rect(start, 0f, end, size.height), layerPaint)
        try {
            clipRect(left = start, right = end) {
                this@drawFadeStrip.drawContent()
            }
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = FadeAlphaColors,
                    startX = start,
                    endX = end,
                ),
                topLeft = Offset(start, 0f),
                size = Size(end - start, size.height),
                blendMode = BlendMode.DstIn,
            )
        } finally {
            canvas.restore()
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
