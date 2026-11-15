package com.ainscel.mymail.ui.list

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ainscel.mymail.R
import com.ainscel.mymail.model.Mail
import com.ainscel.mymail.model.MailTag
import com.ainscel.mymail.model.TagPool
import com.ainscel.mymail.model.avatarColor
import com.ainscel.mymail.model.avatarLabel
import com.ainscel.mymail.model.formatMailTime

@Composable
fun MailRow(
    mail: Mail,
    accountName: String?,
    selected: Boolean,
    multiSelect: Boolean,
    checked: Boolean,
    showClassifying: Boolean,
    onClick: () -> Unit,
    onChecked: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val background = if (selected) {
        MaterialTheme.colorScheme.surfaceContainerHigh
    } else {
        MaterialTheme.colorScheme.surface
    }
    val nameWeight = if (mail.unread) FontWeight.SemiBold else FontWeight.Normal
    Row(
        modifier
            .fillMaxWidth()
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        AnimatedVisibility(
            visible = multiSelect,
            enter = fadeIn() + expandHorizontally(),
            exit = fadeOut() + shrinkHorizontally(),
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = onChecked,
                modifier = Modifier.padding(end = 4.dp),
            )
        }
        SenderAvatar(name = mail.fromName, accountName = accountName)
        Column(
            Modifier
                .padding(start = 10.dp)
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .padding(end = 6.dp)
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(
                            if (mail.unread) MaterialTheme.colorScheme.primary else Color.Transparent,
                        ),
                )
                // 姓名过长时右端淡出。标签和时间在这个槽位外面，保持完整。
                Text(
                    text = mail.fromName,
                    modifier = Modifier
                        .weight(1f)
                        .fixedTextNoReflow(),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = nameWeight,
                )
                if (showClassifying && mail.tags.isEmpty()) {
                    Text(
                        text = stringResource(R.string.list_classifying),
                        modifier = Modifier.padding(start = 8.dp),
                        maxLines = 1,
                        softWrap = false,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                mail.tags.asReversed().forEach { tag ->
                    TagChip(tag)
                }
                if (mail.tags.isNotEmpty() || (showClassifying && mail.tags.isEmpty())) {
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = formatMailTime(mail.timeMillis),
                    maxLines = 1,
                    softWrap = false,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // 标题比预览更早收束。淡出位置用绘制裁切，不靠把文本测窄。
            Text(
                text = mail.subject,
                modifier = Modifier
                    .fillMaxWidth()
                    .fixedTextNoReflow(rightInset = 40.dp),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = nameWeight,
            )
            Text(
                text = mail.preview,
                modifier = Modifier
                    .fillMaxWidth()
                    .fixedTextNoReflow(),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SenderAvatar(name: String, accountName: String?) {
    Box(Modifier.size(40.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .size(40.dp)
                .clip(CircleShape)
                .background(avatarColor(name)),
            contentAlignment = Alignment.Center,
        ) {
            Text(avatarLabel(name), color = Color.White, fontSize = 14.sp)
        }
        if (accountName != null) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(16.dp)
                    .border(1.dp, MaterialTheme.colorScheme.surface, CircleShape)
                    .clip(CircleShape)
                    .background(avatarColor(accountName)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = avatarLabel(accountName).take(1),
                    color = Color.White,
                    fontSize = 8.sp,
                )
            }
        }
    }
}

@Composable
private fun TagChip(tag: MailTag) {
    val color = when (tag.pool) {
        TagPool.Active -> Color(0xFF3D6FBF)
        TagPool.Stale -> Color(0xFF8E97A6)
        TagPool.Pending -> Color(0xFF3D6FBF)
    }
    val shape = RoundedCornerShape(4.dp)
    val border = if (tag.pool == TagPool.Pending) {
        Modifier.drawBehind {
            drawRoundRect(
                color = color,
                style = Stroke(
                    width = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(7f, 5f)),
                ),
                cornerRadius = CornerRadius(4.dp.toPx()),
            )
        }
    } else {
        Modifier.border(1.dp, color, shape)
    }
    Text(
        text = tag.name,
        modifier = Modifier
            .padding(start = 4.dp)
            .then(border)
            .background(color.copy(alpha = if (tag.pool == TagPool.Pending) 0f else 0.08f), shape)
            .padding(horizontal = 6.dp, vertical = 1.dp),
        color = color,
        style = MaterialTheme.typography.labelSmall,
        maxLines = 1,
    )
}

/**
 * 文字按固定宽度排版，列表变窄时不再重测。
 * 看得见的宽度仍跟父布局走，多出来的部分裁掉，右端用透明度淡出，不盖底色。
 *
 * 测量宽度必须是常量，而且要比列表栏更宽，拖动分界线时约束才不会变。
 */
private val TextMeasureWidth = 4000.dp
private val TextFadeWidth = 36.dp

private fun Modifier.fixedTextNoReflow(
    rightInset: Dp = 0.dp,
    fadeWidth: Dp = TextFadeWidth,
): Modifier = this
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
            constraints.copy(
                minWidth = 0,
                maxWidth = fixedWidth,
            ),
        )
        val width = when (val incoming = constraints.maxWidth) {
            Constraints.Infinity -> placeable.width
            else -> incoming.coerceAtLeast(0)
        }
        layout(width, placeable.height) {
            placeable.placeRelative(0, 0)
        }
    }
