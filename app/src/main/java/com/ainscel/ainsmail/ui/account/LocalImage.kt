package com.ainscel.ainsmail.ui.account

import android.content.Context
import android.net.Uri
import java.io.ByteArrayOutputStream

private const val MAX_IMAGE_BYTES = 20 * 1024 * 1024

/** 在 IO 线程读取用户选中的图片。超过上限就放弃，避免把大图留在内存里。 */
internal fun Context.readImage(uri: Uri): ByteArray? {
    val stream = contentResolver.openInputStream(uri) ?: return null
    return stream.use { input ->
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            if (total > MAX_IMAGE_BYTES) return null
            output.write(buffer, 0, count)
        }
        output.toByteArray()
    }
}
