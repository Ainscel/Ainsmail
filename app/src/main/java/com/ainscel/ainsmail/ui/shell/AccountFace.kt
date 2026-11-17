package com.ainscel.ainsmail.ui.shell

import android.content.Context
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
import com.ainscel.ainsmail.security.resolveInside
import com.ainscel.ainsmail.ui.mailbox.AccountUi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async

@Composable
internal fun AccountFace(
    account: AccountUi,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bitmap = rememberAccountAvatar(account.avatarFile)
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
            Text(account.label, color = Color.White, fontSize = AccountAvatarFont)
        }
    }
}

/**
 * 同一条头像路径只解码一次。侧栏和列表角标拿的是同一份图，
 * 滑动列表时不会按行重复读文件。
 */
@Composable
internal fun rememberAccountAvatar(relative: String?): ImageBitmap? {
    var image by remember(relative) { mutableStateOf(relative?.let(AccountAvatarCache::peek)) }
    val context = LocalContext.current.applicationContext
    LaunchedEffect(relative) {
        if (relative == null) {
            image = null
        } else if (image == null) {
            image = AccountAvatarCache.get(context, relative)
        }
    }
    return image
}

private object AccountAvatarCache {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val images = HashMap<String, ImageBitmap>()
    private val jobs = HashMap<String, Deferred<ImageBitmap?>>()

    fun peek(relative: String): ImageBitmap? = synchronized(this) { images[relative] }

    suspend fun get(context: Context, relative: String): ImageBitmap? {
        peek(relative)?.let { return it }
        val deferred = synchronized(this) {
            images[relative]?.let { return it }
            jobs.getOrPut(relative) {
                scope.async {
                    val decoded = decode(context, relative)
                    synchronized(this@AccountAvatarCache) {
                        if (decoded != null) images[relative] = decoded
                        jobs.remove(relative)
                    }
                    decoded
                }
            }
        }
        return deferred.await()
    }

    private fun decode(context: Context, relative: String): ImageBitmap? {
        val file = context.filesDir.resolveInside(relative) ?: return null
        if (!file.isFile) return null
        return try {
            BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }
}
