package com.ainscel.ainsmail.ui.shell

import androidx.compose.ui.graphics.Color

/** 色相已经在投影里算好。这里只把它画成稳重的圆。头像列和邮件行共用。 */
fun avatarColor(hue: Float): Color = Color.hsl(hue, 0.38f, 0.46f)
