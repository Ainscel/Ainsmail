package com.ainscel.ainsmail.security

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

const val GOOGLE_USERINFO = "https://openidconnect.googleapis.com/v1/userinfo"
const val GMAIL_MAIL_SCOPE = "https://mail.google.com/"

/** 访问令牌默认有效期。Play 授权结果不带 expires_in，按一小时记，提前五分钟当过期。 */
const val GOOGLE_ACCESS_TOKEN_TTL_MS = 55L * 60L * 1000L

private val authJson = Json { ignoreUnknownKeys = true }

data class GoogleProfile(
    val email: String,
    val name: String,
    val pictureUrl: String?,
)

fun parseGoogleProfile(raw: String): GoogleProfile? {
    val obj = authJson.parseToJsonElement(raw) as? JsonObject ?: return null
    val email = obj.string("email")?.trim()?.lowercase().orEmpty()
    if ('@' !in email || email.any { it.isWhitespace() }) return null
    val name = obj.string("name")?.trim().orEmpty().ifBlank { email.substringBefore('@') }
    val picture = obj.string("picture")?.trim()?.takeIf { it.startsWith("https://") }
    return GoogleProfile(email, name, picture)
}

private fun JsonObject.string(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull
