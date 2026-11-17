package com.ainscel.ainsmail.ui.list

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
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

/**
 * 单行文字按固定宽度测量，放进尺寸稳定的绘制层，对外仍报告父级槽宽。
 * [background] 是字形背后的不透明底色：淡出是盖在槽位右缘的纯色渐变，不再做离屏混合。
 * 拖动时槽宽每帧都在变，文字层不跟着变，字形不用重录。
 */
fun Modifier.zeroReflowFadeText(
    background: Color,
    rightInset: Dp = 0.dp,
    fadeWidth: Dp = MailTextFadeWidth,
): Modifier = this
    .then(FadeSlotElement(background, rightInset, fadeWidth))
    // 只缓存字形。槽宽变化时不要换成 Offscreen，否则会按新尺寸重做离屏缓冲。
    .graphicsLayer(compositingStrategy = CompositingStrategy.Auto)

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
 * 对外尺寸是当前槽。传给内层的约束锁在 [TextMeasureWidth]，拖动时不变，文字绘制层可以跳过测量。
 * 绘制时先裁到槽内，再在右缘盖上底色渐变。
 */
private class FadeSlotElement(
    val background: Color,
    val rightInset: Dp,
    val fadeWidth: Dp,
) : ModifierNodeElement<FadeSlotNode>() {
    override fun create(): FadeSlotNode = FadeSlotNode(background, rightInset, fadeWidth)

    override fun update(node: FadeSlotNode) {
        node.background = background
        node.rightInset = rightInset
        node.fadeWidth = fadeWidth
        node.invalidateDraw()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        val o = other as? FadeSlotElement ?: return false
        return background == o.background && rightInset == o.rightInset && fadeWidth == o.fadeWidth
    }

    override fun hashCode(): Int {
        var result = background.hashCode()
        result = 31 * result + rightInset.hashCode()
        result = 31 * result + fadeWidth.hashCode()
        return result
    }
}

private class FadeSlotNode(
    var background: Color,
    var rightInset: Dp,
    var fadeWidth: Dp,
) : Modifier.Node(), LayoutModifierNode, DrawModifierNode {

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

    override fun ContentDrawScope.draw() {
        val inset = rightInset.toPx()
        val fade = fadeWidth.toPx()
        // clipRect 的接收者不是 ContentDrawScope，文字要显式画到当前这层画布上，裁切才生效。
        clipRect {
            this@draw.drawContent()
            drawFadeScrim(background, inset, fade)
        }
    }
}

/** 右缘先淡出，再把 [rightInsetPx] 那段用底色盖住。短文字碰不到右缘时，渐变盖在空白底色上。 */
private fun DrawScope.drawFadeScrim(background: Color, rightInsetPx: Float, fadePx: Float) {
    val width = size.width
    if (width <= 0f || size.height <= 0f) return
    val visibleEnd = (width - rightInsetPx).coerceIn(0f, width)
    val fade = fadePx.coerceAtMost(visibleEnd)
    if (fade > 0f) {
        val fadeStart = visibleEnd - fade
        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(background.copy(alpha = 0f), background),
                startX = fadeStart,
                endX = visibleEnd,
            ),
            topLeft = Offset(fadeStart, 0f),
            size = Size(fade, size.height),
        )
    }
    if (visibleEnd < width) {
        drawRect(
            color = background,
            topLeft = Offset(visibleEnd, 0f),
            size = Size(width - visibleEnd, size.height),
        )
    }
}
