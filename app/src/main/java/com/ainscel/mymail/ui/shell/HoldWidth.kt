package com.ainscel.mymail.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout

/**
 * 分界线还在拖动或回弹时，子内容继续按上一次停稳的宽度测量。
 * 外层尺寸仍跟当前栏宽走，多出来的部分由父级裁掉。分界线停住后按新宽度重排一次。
 * 窄屏没有 [LocalSplitResize]，每次都按当前宽度测量。
 */
@Composable
fun Modifier.holdWidthUntilSettled(): Modifier {
    val resize = LocalSplitResize.current
    val hold = remember { WidthHold() }
    return this.layout { measurable, constraints ->
        if (!constraints.hasBoundedWidth) {
            val placeable = measurable.measure(constraints)
            return@layout layout(placeable.width, placeable.height) {
                placeable.placeRelative(0, 0)
            }
        }
        val live = constraints.maxWidth
        val width = if (resize?.isResizing() == true && hold.settled >= 0) {
            hold.settled
        } else {
            hold.settled = live
            live
        }
        val placeable = measurable.measure(
            constraints.copy(minWidth = width, maxWidth = width),
        )
        val height = if (constraints.hasBoundedHeight) constraints.maxHeight else placeable.height
        layout(live, height) {
            placeable.placeRelative(0, 0)
        }
    }
}

private class WidthHold {
    var settled: Int = -1
}
