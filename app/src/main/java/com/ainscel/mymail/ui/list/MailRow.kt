package com.ainscel.mymail.ui.list

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ainscel.mymail.R
import com.ainscel.mymail.model.Mail
import com.ainscel.mymail.model.MailTag
import com.ainscel.mymail.model.TagPool
import com.ainscel.mymail.model.avatarColor
import com.ainscel.mymail.model.avatarLabel
import com.ainscel.mymail.model.formatMailTime

/** 标题比预览更早收束的右间距。 */
private val SubjectFadeInset = 40.dp

/** 发件人与右侧「标签+时间」之间的间隙。 */
private val MetaGap = 8.dp

/** 正文三行之间的行距。 */
private val LineGap = 2.dp

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
    // 外框宽度严格等于当前分界线宽度，以后横滑快捷操作从边缘立刻露出。
    Box(
        modifier
            .fillMaxWidth()
            .clipToBounds()
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
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
            MailRowTextBlock(
                mail = mail,
                nameWeight = nameWeight,
                showClassifying = showClassifying,
                modifier = Modifier
                    .padding(start = 10.dp)
                    .weight(1f),
            )
        }
    }
}

/**
 * 三行正文：子项用与 actualWidth 无关的固定约束测量；
 * 仅在摆放阶段把标签/时间贴到当前右缘。
 */
@Composable
private fun MailRowTextBlock(
    mail: Mail,
    nameWeight: FontWeight,
    showClassifying: Boolean,
    modifier: Modifier = Modifier,
) {
    Layout(
        content = {
            // [0] 发件人
            Text(
                text = mail.fromName,
                modifier = Modifier.zeroReflowFadeText(fadeWidth = 24.dp),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = nameWeight,
            )
            // [1] 标签 + 时间（或「分类中」）
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (showClassifying && mail.tags.isEmpty()) {
                    Text(
                        text = stringResource(R.string.list_classifying),
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
                    Spacer(Modifier.width(MetaGap))
                }
                Text(
                    text = formatMailTime(mail.timeMillis),
                    maxLines = 1,
                    softWrap = false,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // [2] 标题：比预览更早淡出
            Text(
                text = mail.subject,
                modifier = Modifier.zeroReflowFadeText(
                    rightInset = SubjectFadeInset,
                    fadeWidth = MailTextFadeWidth,
                ),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = nameWeight,
            )
            // [3] 预览
            Text(
                text = mail.preview,
                modifier = Modifier.zeroReflowFadeText(fadeWidth = 28.dp),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // [4] 未读圆点（与发件人同一行靠左）
            Box(
                Modifier
                    .padding(end = 6.dp)
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(
                        if (mail.unread) MaterialTheme.colorScheme.primary else Color.Transparent,
                    ),
            )
        },
        modifier = modifier.clipToBounds(),
    ) { measurables, constraints ->
        val actualWidth = constraints.maxWidth
        val fixedLoose = Constraints(minWidth = 0, maxWidth = TextMeasureWidth.roundToPx())
        val gap = MetaGap.roundToPx()
        val lineGap = LineGap.roundToPx()

        // 右侧元数据与未读点用固定约束测量，拖动时命中缓存。
        val metaPlaceable = measurables[1].measure(fixedLoose)
        val dotPlaceable = measurables[4].measure(fixedLoose)

        // 发件人可见槽 = 当前行宽 − 元数据 − 间隙 − 圆点；槽宽会变，但内部 Text 仍按常量测。
        val senderSlot = (actualWidth - metaPlaceable.width - gap - dotPlaceable.width)
            .coerceAtLeast(0)
        val senderPlaceable = measurables[0].measure(
            Constraints(minWidth = 0, maxWidth = senderSlot.coerceAtLeast(0)),
        )
        // 标题/预览的布局宽度跟 actualWidth 走，内部 Paragraph 仍锁在常量宽度上。
        val subjectPlaceable = measurables[2].measure(
            Constraints(minWidth = 0, maxWidth = actualWidth.coerceAtLeast(0)),
        )
        val previewPlaceable = measurables[3].measure(
            Constraints(minWidth = 0, maxWidth = actualWidth.coerceAtLeast(0)),
        )

        val line1Height = maxOf(senderPlaceable.height, metaPlaceable.height, dotPlaceable.height)
        val line2Y = line1Height + lineGap
        val line3Y = line2Y + subjectPlaceable.height + lineGap
        val height = line3Y + previewPlaceable.height

        layout(actualWidth, height) {
            val dotY = (line1Height - dotPlaceable.height) / 2
            dotPlaceable.placeRelative(0, dotY)
            senderPlaceable.placeRelative(
                x = dotPlaceable.width,
                y = (line1Height - senderPlaceable.height) / 2,
            )
            metaPlaceable.placeRelative(
                x = (actualWidth - metaPlaceable.width).coerceAtLeast(0),
                y = (line1Height - metaPlaceable.height) / 2,
            )
            subjectPlaceable.placeRelative(0, line2Y)
            previewPlaceable.placeRelative(0, line3Y)
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
