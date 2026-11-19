package com.ainscel.ainsmail.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderCredentialsTest {
    @Test
    fun gmailAccessExpiredAtBoundary() {
        assertFalse(gmailAccessExpired(expiresAtEpochMs = 100L, nowEpochMs = 99L))
        assertTrue(gmailAccessExpired(expiresAtEpochMs = 100L, nowEpochMs = 100L))
        assertTrue(gmailAccessExpired(expiresAtEpochMs = 100L, nowEpochMs = 101L))
    }

    @Test
    fun accountWriteMapsToSharedError() {
        assertEquals(AccountError.Duplicate, AccountWrite.Duplicate.toAccountError())
        assertEquals(AccountError.BadImage, AccountWrite.BadImage.toAccountError())
        assertEquals(AccountError.Rejected, AccountWrite.Rejected.toAccountError())
        assertNull(AccountWrite.Saved("id").toAccountError())
    }

    @Test
    fun googleFailureMapsToSharedError() {
        assertEquals(AccountError.Denied, GoogleSignInFailure.Denied.toAccountError())
        assertEquals(AccountError.TimedOut, GoogleSignInFailure.TimedOut.toAccountError())
    }

    @Test
    fun credentialsToStringOmitsSecrets() {
        val qq = ProviderCredentials.Qq("a@qq.com", "secret-code")
        val gmail = ProviderCredentials.Gmail("a@gmail.com", "ya29.token", 1L)
        assertFalse(qq.toString().contains("secret-code"))
        assertFalse(gmail.toString().contains("ya29"))
    }
}
