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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
                Text(
                    text = mail.fromName,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
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
            Text(
                text = mail.subject,
                modifier = Modifier.padding(end = 40.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = nameWeight,
            )
            PreviewLine(text = mail.preview, fadeColor = background)
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

@Composable
private fun PreviewLine(text: String, fadeColor: Color) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .drawWithContent {
                drawContent()
                val fade = 36.dp.toPx()
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color.Transparent, fadeColor),
                        startX = size.width - fade,
                        endX = size.width,
                    ),
                    topLeft = Offset(size.width - fade, 0f),
                    size = Size(fade, size.height),
                )
            },
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
