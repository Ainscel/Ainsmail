package com.ainscel.ainsmail.ui.mailbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SnapshotHoldTest {
    @Test
    fun pauseKeepsOnlyTheLatestSnapshot() {
        val first = nextSnapshot(paused = true, held = null, incoming = listOf("a"))
        assertNull(first.apply)
        assertEquals(listOf("a"), first.held)

        val second = nextSnapshot(paused = true, held = first.held, incoming = listOf("b"))
        assertNull(second.apply)
        assertEquals(listOf("b"), second.held)

        val released = nextSnapshot(paused = false, held = second.held, incoming = null)
        assertNull(released.held)
        assertEquals(listOf("b"), released.apply)
    }

    @Test
    fun idleApplyDropsTheHeldCopy() {
        val step = nextSnapshot(paused = false, held = listOf("old"), incoming = listOf("new"))
        assertNull(step.held)
        assertEquals(listOf("new"), step.apply)
    }
}
