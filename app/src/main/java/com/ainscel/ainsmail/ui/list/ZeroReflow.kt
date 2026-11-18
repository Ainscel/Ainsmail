package com.ainscel.ainsmail.ui.list

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
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
 * 单行文字按固定宽度测量；布局宽度仍跟父级可见槽走。
 * 变窄时只改摆放宽度并在绘制阶段裁切淡出，不触发 Paragraph 重算。
 */
fun Modifier.zeroReflowFadeText(
    rightInset: Dp = 0.dp,
    fadeWidth: Dp = MailTextFadeWidth,
): Modifier = this
    // layout 必须最靠近文字。它对外报告可见槽宽度，对内仍按常量测量。
    // 淡出画在这一层外面，size.width 才是槽宽；若顺序反了，淡出会画在全文末尾的屏幕外。
    .clipToBounds()
    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    .drawWithContent {
        val visibleEnd = (size.width - rightInset.toPx()).coerceIn(0f, size.width)
        val fade = fadeWidth.toPx().coerceAtMost(visibleEnd)
        val fadeStart = visibleEnd - fade
        drawContext.canvas.save()
        drawContext.canvas.clipRect(0f, 0f, visibleEnd, size.height)
        drawContent()
        drawContext.canvas.restore()
        if (fade <= 0f) return@drawWithContent
        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(Color.Black, Color.Transparent),
                startX = fadeStart,
                endX = visibleEnd,
            ),
            topLeft = Offset(fadeStart, 0f),
            size = Size(fade, size.height),
            blendMode = BlendMode.DstIn,
        )
    }
    .layout { measurable, constraints ->
        val fixedWidth = TextMeasureWidth.roundToPx()
        val placeable = measurable.measure(
            Constraints(minWidth = 0, maxWidth = fixedWidth),
        )
        val width = when {
            constraints.hasBoundedWidth -> constraints.maxWidth.coerceAtLeast(0)
            else -> placeable.width
        }
        layout(width, placeable.height) {
            placeable.placeRelative(0, 0)
        }
    }

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
