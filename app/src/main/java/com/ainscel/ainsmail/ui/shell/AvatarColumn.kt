package com.ainscel.ainsmail.ui.shell

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import com.ainscel.ainsmail.R
import com.ainscel.ainsmail.ui.mailbox.AccountUi

/** 侧栏账号圆的尺寸。列表角标按这个比例缩小，字和圆的关系保持一致。 */
internal val AccountAvatarSize = 48.dp
internal val AccountAvatarInset = 3.dp
internal val AccountAvatarFont = 14.sp

internal fun scaledAvatarFont(diameter: Dp): TextUnit =
    (AccountAvatarFont.value * diameter.value / AccountAvatarSize.value).sp

internal fun scaledAvatarInset(diameter: Dp): Dp =
    AccountAvatarInset * (diameter.value / AccountAvatarSize.value)

@Composable
internal fun AvatarColumn(
    accounts: List<AccountUi>,
    selectedAccountId: String?,
    onSelectAccount: (String?) -> Unit,
    onAdd: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.background(MaterialTheme.colorScheme.surfaceContainerLow),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.padding(top = 8.dp, bottom = 4.dp)) {
            CircleButton(
                label = stringResource(R.string.account_all),
                selected = selectedAccountId == null,
                onClick = { onSelectAccount(null) },
                container = MaterialTheme.colorScheme.primary,
                content = Color.White,
            ) {
                AllAccountsMark()
            }
        }
        AvatarScroller(
            accounts = accounts,
            selectedAccountId = selectedAccountId,
            onSelectAccount = onSelectAccount,
            onAdd = onAdd,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        )
        Box(Modifier.padding(top = 4.dp, bottom = 8.dp)) {
            CircleButton(
                label = stringResource(R.string.open_settings),
                selected = false,
                onClick = onSettings,
                container = MaterialTheme.colorScheme.surfaceVariant,
                content = MaterialTheme.colorScheme.onSurfaceVariant,
            ) {
                Text("设", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp)
            }
        }
    }
}

@Composable
internal fun CircleButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
    inside: @Composable () -> Unit,
) {
    val ring = if (selected) {
        Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
    } else {
        Modifier.border(1.dp, content.copy(alpha = 0.25f), CircleShape)
    }
    Box(
        modifier
            .size(AccountAvatarSize)
            .then(ring)
            .padding(AccountAvatarInset)
            .clip(CircleShape)
            .background(container)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        inside()
    }
}

@Composable
private fun AllAccountsMark() {
    Canvas(Modifier.size(22.dp)) {
        val radius = 2.6.dp.toPx()
        val gap = 5.dp.toPx()
        val center = this.center
        listOf(
            Offset(-gap, -gap),
            Offset(gap, -gap),
            Offset(-gap, gap),
            Offset(gap, gap),
        ).forEach { delta ->
            drawCircle(Color.White, radius, center + delta)
        }
    }
}
