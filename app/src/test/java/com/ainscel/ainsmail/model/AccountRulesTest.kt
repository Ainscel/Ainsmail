package com.ainscel.ainsmail.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountRulesTest {
    @Test
    fun qqAddressAcceptsQqAndFoxmail() {
        assertEquals("me@qq.com", qqAddress(" Me@QQ.com "))
        assertEquals("me@foxmail.com", qqAddress("me@Foxmail.com"))
        assertNull(qqAddress("me@gmail.com"))
        assertNull(qqAddress("@qq.com"))
        assertNull(qqAddress("me@qq.com@qq.com"))
        assertNull(qqAddress("me @qq.com"))
    }

    @Test
    fun displayNameDefaultsToTheLocalPart() {
        assertEquals("me", defaultDisplayName("me@qq.com"))
        assertEquals("me", defaultDisplayName(" me@qq.com "))
    }

    @Test
    fun gmailNeedsClientIdOnly() {
        assertFalse(gmailClientReady(""))
        assertFalse(gmailClientReady(" "))
        assertTrue(gmailClientReady("id.apps.googleusercontent.com"))
    }
}
