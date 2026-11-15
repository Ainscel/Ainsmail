package com.ainscel.ainsmail.security

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

const val GOOGLE_AUTHORIZE = "https://accounts.google.com/o/oauth2/v2/auth"
const val GOOGLE_TOKEN = "https://oauth2.googleapis.com/token"
const val GOOGLE_USERINFO = "https://openidconnect.googleapis.com/v1/userinfo"
const val GMAIL_SCOPE = "openid email profile https://mail.google.com/"

private val authJson = Json { ignoreUnknownKeys = true }

/** 用户填写的桌面客户端。密钥不进 [toString]。 */
class GoogleClient(
    val id: String,
    val secret: String,
) {
    override fun toString(): String = "GoogleClient"
}

/** 换来的令牌。访问令牌只在这次登录里用，不落盘。[toString] 不带原文。 */
class IssuedTokens(
    val accessToken: String,
    val refreshToken: String,
) {
    override fun toString(): String = "IssuedTokens"
}

data class GoogleProfile(
    val email: String,
    val name: String,
    val pictureUrl: String?,
)

sealed interface AuthRedirect {
    data class Code(val value: String) : AuthRedirect
    data object Denied : AuthRedirect
    data object Invalid : AuthRedirect
}

fun newAuthSecret(): String {
    val bytes = ByteArray(32)
    SecureRandom().nextBytes(bytes)
    return base64Url(bytes)
}

/** PKCE S256。RFC 7636。 */
fun codeChallenge(verifier: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(verifier.toByteArray(StandardCharsets.US_ASCII))
    return base64Url(digest)
}

fun redirectUri(port: Int): String = "http://127.0.0.1:$port/"

fun authorizeUrl(clientId: String, redirectUri: String, state: String, verifier: String): String {
    val query = form(
        "client_id" to clientId,
        "redirect_uri" to redirectUri,
        "response_type" to "code",
        "scope" to GMAIL_SCOPE,
        "code_challenge" to codeChallenge(verifier),
        "code_challenge_method" to "S256",
        "state" to state,
        "access_type" to "offline",
        "prompt" to "consent",
    )
    return "$GOOGLE_AUTHORIZE?$query"
}

fun tokenForm(
    clientId: String,
    clientSecret: String,
    redirectUri: String,
    code: String,
    verifier: String,
): String = form(
    "code" to code,
    "client_id" to clientId,
    "client_secret" to clientSecret,
    "redirect_uri" to redirectUri,
    "grant_type" to "authorization_code",
    "code_verifier" to verifier,
)

/** 从浏览器打到本机的第一行里取出授权码。state 对不上就丢掉。 */
fun parseAuthRedirect(requestLine: String, expectedState: String): AuthRedirect {
    val target = requestLine.trim().split(' ').getOrNull(1) ?: return AuthRedirect.Invalid
    val query = target.substringAfter('?', "")
    if (query.isEmpty()) return AuthRedirect.Invalid
    val params = linkedMapOf<String, String>()
    query.split('&').forEach { part ->
        if (part.isEmpty()) return@forEach
        val key = urlDecode(part.substringBefore('='))
        val value = urlDecode(part.substringAfter('=', ""))
        params[key] = value
    }
    if (params["state"] != expectedState) return AuthRedirect.Invalid
    when (params["error"]) {
        null -> Unit
        "access_denied" -> return AuthRedirect.Denied
        else -> return AuthRedirect.Invalid
    }
    val code = params["code"]
    if (code.isNullOrBlank()) return AuthRedirect.Invalid
    return AuthRedirect.Code(code)
}

fun parseIssuedTokens(raw: String): IssuedTokens? {
    val obj = authJson.parseToJsonElement(raw) as? JsonObject ?: return null
    val access = obj.string("access_token") ?: return null
    val refresh = obj.string("refresh_token") ?: return null
    if (access.isBlank() || refresh.isBlank()) return null
    return IssuedTokens(access, refresh)
}

fun parseGoogleProfile(raw: String): GoogleProfile? {
    val obj = authJson.parseToJsonElement(raw) as? JsonObject ?: return null
    val email = obj.string("email")?.trim()?.lowercase().orEmpty()
    if ('@' !in email || email.any { it.isWhitespace() }) return null
    val name = obj.string("name")?.trim().orEmpty().ifBlank { email.substringBefore('@') }
    val picture = obj.string("picture")?.trim()?.takeIf { it.startsWith("https://") }
    return GoogleProfile(email, name, picture)
}

private fun form(vararg pairs: Pair<String, String>): String =
    pairs.joinToString("&") { (key, value) -> "${urlEncode(key)}=${urlEncode(value)}" }

private fun urlEncode(value: String): String =
    URLEncoder.encode(value, StandardCharsets.UTF_8)

private fun urlDecode(value: String): String =
    URLDecoder.decode(value, StandardCharsets.UTF_8)

private fun base64Url(bytes: ByteArray): String =
    Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

private fun JsonObject.string(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull
