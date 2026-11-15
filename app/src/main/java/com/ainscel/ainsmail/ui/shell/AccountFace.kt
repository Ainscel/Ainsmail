package com.ainscel.ainsmail.ui.shell

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import com.ainscel.ainsmail.security.resolveInside
import com.ainscel.ainsmail.ui.mailbox.AccountUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun AccountFace(
    account: AccountUi,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bitmap = rememberAvatar(account.avatarFile)
    CircleButton(
        label = account.name,
        selected = selected,
        onClick = onClick,
        container = avatarColor(account.hue),
        content = Color.White,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = account.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(account.label, color = Color.White, fontSize = 14.sp)
        }
    }
}

@Composable
private fun rememberAvatar(relative: String?): ImageBitmap? {
    val context = LocalContext.current
    var image by remember(relative) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(relative) {
        image = null
        if (relative == null) return@LaunchedEffect
        image = withContext(Dispatchers.IO) {
            val file = context.filesDir.resolveInside(relative) ?: return@withContext null
            if (!file.isFile) null else BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
        }
    }
    return image
}
