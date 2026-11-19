package com.ainscel.ainsmail.ui.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ainscel.ainsmail.R

/** 宽屏右栏右上角的全屏按钮。没有分栏时不显示。 */
@Composable
fun SplitFullscreenButton(modifier: Modifier = Modifier) {
    val fullscreen = LocalDetailFullscreen.current ?: return
    MailRoundButton(
        icon = if (fullscreen.isActive()) FullscreenExit else Fullscreen,
        description = stringResource(
            if (fullscreen.isActive()) R.string.detail_exit_fullscreen else R.string.detail_fullscreen,
        ),
        onClick = fullscreen.toggle,
        modifier = modifier,
    )
}

@Composable
fun MailRoundButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.size(MailCapsuleHeight),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 2.dp,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = description,
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

private val Fullscreen: ImageVector by lazy {
    pathIcon(
        "Fullscreen",
        "M7,14H5v5h5v-2H7v-3zM5,10h2V7h3V5H5v5zM17,17h-3v2h5v-5h-2v3zM14,5v2h3v3h2V5h-5z",
    )
}

private val FullscreenExit: ImageVector by lazy {
    pathIcon(
        "FullscreenExit",
        "M5,16h3v3h2v-5H5v2zM8,8H5v2h5V5H8v3zM14,19h2v-3h3v-2h-5v5zM16,8V5h-2v5h5V8h-3z",
    )
}
