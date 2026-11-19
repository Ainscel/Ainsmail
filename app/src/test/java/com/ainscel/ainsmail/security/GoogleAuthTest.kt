package com.ainscel.ainsmail.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GoogleAuthTest {
    @Test
    fun profileParsesEmailAndHttpsPicture() {
        val profile = parseGoogleProfile(
            """{"email":"Me@Gmail.com","name":"Ainscel","picture":"https://lh3.googleusercontent.com/a"}""",
        )
        assertEquals("me@gmail.com", profile?.email)
        assertEquals("Ainscel", profile?.name)
        assertEquals("https://lh3.googleusercontent.com/a", profile?.pictureUrl)
        assertNull(parseGoogleProfile("""{"email":"me@gmail.com","picture":"http://evil"}""")?.pictureUrl)
        assertNull(parseGoogleProfile("""{"email":"not-an-email"}"""))
    }
}
