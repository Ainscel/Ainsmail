package com.ainscel.ainsmail.ui.shell

import androidx.compose.runtime.mutableFloatStateOf
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.coroutines.CoroutineContext

class SplitDragStateTest {
    @Test
    fun rubberReleaseUnfreezes() {
        val state = SplitDragState(mutableFloatStateOf(0.4f))
        state.holdResize()
        state.onFingerUp(CoroutineScope(HoldingDispatcher()), null)
        assertFalse(state.resizing)
    }

    @Test
    fun fullscreenSpringStaysFrozenAfterFingerUp() {
        val state = SplitDragState(mutableFloatStateOf(0.4f))
        val scope = CoroutineScope(HoldingDispatcher())
        state.holdResize()
        state.animateTo(scope, 1f)
        state.onFingerUp(scope, 1f)
        assertTrue(state.resizing)
        state.settle()
        assertFalse(state.resizing)
    }
}

/** 不跑协程体，避免单测里没有帧时钟就把弹簧跑起来。 */
private class HoldingDispatcher : CoroutineDispatcher() {
    override fun dispatch(context: CoroutineContext, block: Runnable) = Unit
}
