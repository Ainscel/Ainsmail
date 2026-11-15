package com.ainscel.ainsmail.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleAuthTest {
    @Test
    fun challengeMatchesThePkceVector() {
        val verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", codeChallenge(verifier))
    }

    @Test
    fun authorizeUrlCarriesLoopbackPkceAndMailScope() {
        val url = authorizeUrl(
            clientId = "client",
            redirectUri = redirectUri(4312),
            state = "state-1",
            verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk",
        )
        assertTrue(url.startsWith(GOOGLE_AUTHORIZE))
        assertTrue(url.contains("redirect_uri=http%3A%2F%2F127.0.0.1%3A4312%2F"))
        assertTrue(url.contains("code_challenge_method=S256"))
        assertTrue(url.contains("access_type=offline"))
        assertTrue(url.contains("prompt=consent"))
        assertTrue(url.contains("https%3A%2F%2Fmail.google.com%2F"))
        assertTrue(url.contains("openid"))
    }

    @Test
    fun redirectRequiresTheExpectedState() {
        val line = "GET /?code=abc&state=state-1 HTTP/1.1"
        assertEquals("abc", (parseAuthRedirect(line, "state-1") as AuthRedirect.Code).value)
        assertEquals(AuthRedirect.Invalid, parseAuthRedirect(line, "other"))
        assertEquals(
            AuthRedirect.Denied,
            parseAuthRedirect("GET /?error=access_denied&state=state-1 HTTP/1.1", "state-1"),
        )
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
