package com.ainscel.ainsmail.ui

/** 一次性提示。tick 留在状态里，界面重建时用它挡住第二次弹出。 */
internal class ShownTick {
    private var shown: Long = 0

    fun claim(tick: Long): Boolean {
        if (tick == 0L || tick == shown) return false
        shown = tick
        return true
    }
}
