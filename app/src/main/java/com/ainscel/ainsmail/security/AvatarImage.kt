package com.ainscel.ainsmail.security

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min

private const val AVATAR_EDGE = 256
private const val MAX_SOURCE_BYTES = 20 * 1024 * 1024

/** 把头像收成 256 像素的方图。解不开就返回 null，调用方改用纯色圆。 */
fun scaleAvatar(bytes: ByteArray): ByteArray? {
    if (bytes.isEmpty() || bytes.size > MAX_SOURCE_BYTES) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    val sample = sampleSize(bounds.outWidth, bounds.outHeight)
    val decoded = BitmapFactory.decodeByteArray(
        bytes,
        0,
        bytes.size,
        BitmapFactory.Options().apply { inSampleSize = sample },
    ) ?: return null
    val oriented = orient(decoded, exifOrientation(bytes))
    val squared = square(oriented, AVATAR_EDGE)
    val output = ByteArrayOutputStream()
    val wrote = squared.compress(Bitmap.CompressFormat.JPEG, 85, output)
    if (squared !== oriented) squared.recycle()
    if (oriented !== decoded) oriented.recycle()
    decoded.recycle()
    if (!wrote) return null
    return output.toByteArray()
}

private fun sampleSize(width: Int, height: Int): Int {
    var sample = 1
    val longest = max(width, height)
    while (longest / sample > AVATAR_EDGE * 4) sample *= 2
    return sample
}

private fun exifOrientation(bytes: ByteArray): Int = try {
    ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(
        ExifInterface.TAG_ORIENTATION,
        ExifInterface.ORIENTATION_NORMAL,
    )
} catch (cancelled: kotlinx.coroutines.CancellationException) {
    throw cancelled
} catch (_: Exception) {
    ExifInterface.ORIENTATION_NORMAL
}

private fun orient(source: Bitmap, orientation: Int): Bitmap {
    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
        else -> return source
    }
    val turned = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    if (turned !== source) source.recycle()
    return turned
}

private fun square(source: Bitmap, edge: Int): Bitmap {
    val side = min(source.width, source.height)
    val x = (source.width - side) / 2
    val y = (source.height - side) / 2
    val cropped = Bitmap.createBitmap(source, x, y, side, side)
    if (side == edge) return cropped
    val scaled = Bitmap.createScaledBitmap(cropped, edge, edge, true)
    if (cropped !== source && cropped !== scaled) cropped.recycle()
    return scaled
}
