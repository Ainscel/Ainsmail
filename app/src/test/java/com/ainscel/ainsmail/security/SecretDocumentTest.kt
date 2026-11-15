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
        )
        val original = SecretDocument(
            deepSeekApiKey = "old-key",
            deepSeekModel = "deepseek-flash",
            googleClientId = "old-id",
            accounts = listOf(record),
        )
        val updated = parseSecretDocument(original.encode()).replacingSecrets(
            SecretStore.Secrets(
                deepSeekApiKey = "new-key",
                deepSeekModel = "deepseek-chat",
                googleClientId = "new-id",
            ),
        )
        val again = parseSecretDocument(updated.encode())

        assertEquals("new-key", again.deepSeekApiKey)
        assertEquals("new-id", again.googleClientId)
        assertEquals("SECRET-CODE", again.accounts.single().authCode)
        assertFalse(again.accounts.single().toString().contains("SECRET-CODE"))
        assertFalse(again.encode().contains("googleClientSecret"))
        assertFalse(again.encode().contains("refreshToken"))
    }

    @Test
    fun replacingAccountsKeepsClientId() {
        val original = SecretDocument(googleClientId = "id")
        val updated = parseSecretDocument(original.encode()).replacingAccounts(
            listOf(
                AccountRecord(
                    id = "g",
                    provider = AccountRecord.PROVIDER_GMAIL,
                    address = "me@gmail.com",
                    displayName = "Me",
                    accessToken = "SECRET-ACCESS",
                    accessTokenExpiresAtEpochMs = 1_700_000_000_000L,
                ),
            ),
        )
        val again = parseSecretDocument(updated.encode())

        assertEquals("SECRET-ACCESS", again.accounts.single().accessToken)
        assertEquals(1_700_000_000_000L, again.accounts.single().accessTokenExpiresAtEpochMs)
        assertEquals("id", again.googleClientId)
        assertNull(again.accounts.single().profile()?.avatarFile)
        assertFalse(again.accounts.single().toString().contains("SECRET-ACCESS"))
    }

    @Test
    fun olderSchemasStillReadAndDropObsoleteFields() {
        val raw = """
            {"version":2,"deepSeekApiKey":"k","deepSeekModel":"m","googleClientId":"id","googleClientSecret":"sec","accounts":[{"id":"g","provider":"gmail","address":"me@gmail.com","displayName":"Me","refreshToken":"OLD-REFRESH"}]}
        """.trimIndent()
        val document = parseSecretDocument(raw)

        assertEquals("k", document.deepSeekApiKey)
        assertEquals("m", document.deepSeekModel)
        assertEquals("id", document.googleClientId)
        assertEquals(1, document.accounts.size)
        assertEquals("", document.accounts.single().accessToken)
        assertEquals(SECRET_SCHEMA_VERSION, parseSecretDocument(document.encode()).version)
        assertTrue(document.encode().contains("accessToken"))
        assertFalse(document.encode().contains("refreshToken"))
        assertFalse(document.encode().contains("googleClientSecret"))
    }
}
