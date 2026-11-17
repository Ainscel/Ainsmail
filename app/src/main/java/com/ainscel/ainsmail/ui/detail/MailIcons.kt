package com.ainscel.ainsmail.ui.detail

import androidx.compose.ui.graphics.vector.ImageVector
import com.ainscel.ainsmail.ui.shell.pathIcon

/** 核心图标包里没有的这一枚。填充用黑色，按钮上再着色。 */
internal object MailIcons {
    val OpenInNew: ImageVector by lazy {
        pathIcon(
            "OpenInNew",
            "M19,19H5V5h7V3H5c-1.11,0 -2,0.9 -2,2v14c0,1.1 0.89,2 2,2h14c1.1,0 2,-0.9 2,-2v-7h-2v7z" +
                "M14,3v2h3.59l-9.83,9.83 1.41,1.41L19,6.41V10h2V3h-7z",
        )
    }
}
