package com.ainscel.ainsmail.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SecretDocumentTest {
    @Test
    fun replacingSecretsKeepsAccounts() {
        val record = AccountRecord(
            id = "1",
            provider = AccountRecord.PROVIDER_QQ,
            address = "me@qq.com",
            displayName = "me",
            authCode = "SECRET-CODE",
            refreshToken = "SECRET-TOKEN",
        )
        val original = SecretDocument(
            deepSeekApiKey = "old-key",
            deepSeekModel = "deepseek-flash",
            googleClientId = "old-id",
            googleClientSecret = "old-secret",
            accounts = listOf(record),
        )
        val updated = parseSecretDocument(original.encode()).replacingSecrets(
            SecretStore.Secrets(
                deepSeekApiKey = "new-key",
                deepSeekModel = "deepseek-chat",
                googleClientId = "new-id",
                googleClientSecret = "new-secret",
            ),
        )
        val again = parseSecretDocument(updated.encode())

        assertEquals("new-key", again.deepSeekApiKey)
        assertEquals("new-id", again.googleClientId)
        assertEquals("new-secret", again.googleClientSecret)
        assertEquals("SECRET-CODE", again.accounts.single().authCode)
        assertEquals("SECRET-TOKEN", again.accounts.single().refreshToken)
        assertFalse(again.accounts.single().toString().contains("SECRET-CODE"))
        assertFalse(again.accounts.single().toString().contains("SECRET-TOKEN"))
    }

    @Test
    fun replacingAccountsKeepsClientSecret() {
        val original = SecretDocument(
            googleClientId = "id",
            googleClientSecret = "SECRET-CLIENT",
        )
        val updated = parseSecretDocument(original.encode()).replacingAccounts(
            listOf(
                AccountRecord(
                    id = "g",
                    provider = AccountRecord.PROVIDER_GMAIL,
                    address = "me@gmail.com",
                    displayName = "Me",
                    refreshToken = "SECRET-REFRESH",
                ),
            ),
        )
        val again = parseSecretDocument(updated.encode())

        assertEquals("SECRET-CLIENT", again.googleClientSecret)
        assertEquals("SECRET-REFRESH", again.accounts.single().refreshToken)
        assertEquals("id", again.googleClientId)
        assertNull(again.accounts.single().profile()?.avatarFile)
    }

    @Test
    fun versionOneFileStillReads() {
        val raw = """
            {"version":1,"deepSeekApiKey":"k","deepSeekModel":"m","googleClientId":"id","googleClientSecret":"sec"}
        """.trimIndent()
        val document = parseSecretDocument(raw)

        assertTrue(document.accounts.isEmpty())
        assertEquals("k", document.deepSeekApiKey)
        assertEquals("m", document.deepSeekModel)
        assertEquals("id", document.googleClientId)
        assertEquals("sec", document.googleClientSecret)
        assertEquals(SECRET_SCHEMA_VERSION, parseSecretDocument(document.encode()).version)
    }
}
