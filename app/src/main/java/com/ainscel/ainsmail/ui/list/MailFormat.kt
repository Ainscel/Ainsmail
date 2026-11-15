package com.ainscel.ainsmail.ui.list

import androidx.compose.ui.graphics.Color
import java.time.Instant
import java.time.ZoneId

/** 名称哈希映射到稳重的色相，避免每一行都是荧光色。 */
fun avatarHue(name: String): Float = ((name.hashCode() and 0x7fffffff) % 360).toFloat()

fun avatarColor(hue: Float): Color = Color.hsl(hue, 0.38f, 0.46f)

/** 中文取前两个字，拉丁字母取前两个字母。空白不算。 */
fun avatarLabel(name: String): String = name.filter { !it.isWhitespace() }.take(2)

fun formatMailTime(epochMillis: Long, nowMillis: Long): String {
    val zone = ZoneId.systemDefault()
    val moment = Instant.ofEpochMilli(epochMillis).atZone(zone)
    val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    return if (moment.toLocalDate() == today) {
        "%02d:%02d".format(moment.hour, moment.minute)
    } else {
        "${moment.monthValue}月${moment.dayOfMonth}日"
    }
}
