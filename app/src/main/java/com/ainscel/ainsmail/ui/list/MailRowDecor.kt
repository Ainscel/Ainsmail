package com.ainscel.ainsmail.ui.list

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ainscel.ainsmail.model.TagPool
import com.ainscel.ainsmail.ui.mailbox.TagChipUi
import com.ainscel.ainsmail.ui.shell.avatarColor
import com.ainscel.ainsmail.ui.shell.rememberAccountAvatar
import com.ainscel.ainsmail.ui.shell.scaledAvatarFont
import com.ainscel.ainsmail.ui.shell.scaledAvatarInset

/** 角标外径。字号和内边距按侧栏账号圆的同一比例缩小。 */
private val AccountBadgeSize = 16.dp

@Composable
internal fun SenderAvatar(
    label: String,
    hue: Float,
    accountName: String?,
    accountLabel: String,
    accountHue: Float,
    accountAvatar: String?,
) {
    val accountBitmap = rememberAccountAvatar(accountAvatar)
    Box(Modifier.size(40.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .size(40.dp)
                .clip(CircleShape)
                .background(avatarColor(hue)),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, color = Color.White, fontSize = 14.sp)
        }
        if (accountName != null) {
            val font = scaledAvatarFont(AccountBadgeSize)
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(AccountBadgeSize)
                    .border(1.dp, MaterialTheme.colorScheme.surface, CircleShape)
                    .padding(scaledAvatarInset(AccountBadgeSize))
                    .clip(CircleShape)
                    .background(avatarColor(accountHue)),
                contentAlignment = Alignment.Center,
            ) {
                if (accountBitmap != null) {
                    Image(
                        bitmap = accountBitmap,
                        contentDescription = accountName,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Text(
                        text = accountLabel,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                        style = TextStyle(
                            color = Color.White,
                            fontSize = font,
                            lineHeight = font,
                            textAlign = TextAlign.Center,
                            platformStyle = PlatformTextStyle(includeFontPadding = false),
                        ),
                    )
                }
            }
        }
    }
}

@Composable
internal fun TagChip(tag: TagChipUi) {
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
