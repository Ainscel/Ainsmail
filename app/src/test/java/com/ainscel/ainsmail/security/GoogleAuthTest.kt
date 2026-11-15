package com.ainscel.ainsmail.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleAuthTest {
    @Test
    fun androidRedirectUriUsesReversedClientId() {
        assertEquals(
            "com.googleusercontent.apps.123-abc:/oauth2redirect",
            androidRedirectUri("123-abc.apps.googleusercontent.com"),
        )
        assertNull(androidRedirectUri("not-a-google-client"))
    }

    @Test
    fun serverAuthFormOmitsBlankSecret() {
        val withoutSecret = serverAuthTokenForm("client", "", "code-1")
        assertTrue(withoutSecret.contains("client_id=client"))
        assertTrue(withoutSecret.contains("code=code-1"))
        assertFalse(withoutSecret.contains("client_secret"))

        val withSecret = serverAuthTokenForm("client", "sec", "code-1")
        assertTrue(withSecret.contains("client_secret=sec"))
    }

    @Test
    fun tokensAndProfileDoNotLeakThroughToString() {
        val tokens = parseIssuedTokens("""{"access_token":"ACCESS","refresh_token":"REFRESH"}""")
        assertEquals("REFRESH", tokens?.refreshToken)
        assertEquals("IssuedTokens", tokens.toString())
        assertFalse(tokens.toString().contains("REFRESH"))

        val profile = parseGoogleProfile(
            """{"email":"Me@Gmail.com","name":"Ainscel","picture":"https://lh3.googleusercontent.com/a"}""",
        )
        assertEquals("me@gmail.com", profile?.email)
        assertEquals("Ainscel", profile?.name)
        assertEquals("https://lh3.googleusercontent.com/a", profile?.pictureUrl)
        assertNull(parseGoogleProfile("""{"email":"me@gmail.com","picture":"http://evil"}""")?.pictureUrl)
    }
}
