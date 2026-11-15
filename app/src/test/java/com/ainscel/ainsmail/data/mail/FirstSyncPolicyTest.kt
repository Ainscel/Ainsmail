package com.ainscel.ainsmail.data.mail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FirstSyncPolicyTest {
    @Test
    fun recentBelowCap_allowsHistory() {
        assertTrue(shouldFillHistory(recentCount = 20, totalCount = 20))
        assertEquals(30, historyFillBudget(totalCount = 20))
    }

    @Test
    fun recentAlreadyAtCap_skipsHistory() {
        assertFalse(shouldFillHistory(recentCount = 50, totalCount = 50))
        assertFalse(shouldFillHistory(recentCount = 80, totalCount = 80))
        assertEquals(0, historyFillBudget(totalCount = 50))
    }

    @Test
    fun totalAtCap_skipsEvenIfRecentSmall() {
        // 理论上 7 天很少但库里已有别的数据时也不再补
        assertFalse(shouldFillHistory(recentCount = 10, totalCount = 50))
        assertEquals(0, historyFillBudget(totalCount = 50))
    }
}
