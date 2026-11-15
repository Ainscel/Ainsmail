package com.ainscel.ainsmail.security

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

const val GOOGLE_TOKEN = "https://oauth2.googleapis.com/token"
const val GOOGLE_USERINFO = "https://openidconnect.googleapis.com/v1/userinfo"
const val GMAIL_MAIL_SCOPE = "https://mail.google.com/"

private val authJson = Json { ignoreUnknownKeys = true }

/**
 * 用户填写的 Android OAuth 客户端。
 * [secret] 可选：同项目若另有 Web 客户端，换刷新令牌失败时再带上。不进 [toString]。
 */
class GoogleClient(
    val id: String,
    val secret: String = "",
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

/** Android 客户端对应的反向 DNS 回调。AuthorizationClient 由 Play 服务处理，一般不用手写。 */
fun androidRedirectUri(clientId: String): String? {
    val prefix = clientId.trim().removeSuffix(".apps.googleusercontent.com")
    if (prefix.isEmpty() || prefix == clientId.trim()) return null
    return "com.googleusercontent.apps.$prefix:/oauth2redirect"
}

/** Play 授权返回的 server auth code 换令牌。公开客户端可不带密钥。 */
fun serverAuthTokenForm(clientId: String, clientSecret: String, code: String): String {
    val pairs = mutableListOf(
        "code" to code,
        "client_id" to clientId,
        "grant_type" to "authorization_code",
    )
    if (clientSecret.isNotBlank()) {
        pairs.add("client_secret" to clientSecret)
    }
    return form(*pairs.toTypedArray())
}

fun parseIssuedTokens(raw: String): IssuedTokens? {
    val obj = authJson.parseToJsonElement(raw) as? JsonObject ?: return null
    val access = obj.string("access_token") ?: return null
    val refresh = obj.string("refresh_token") ?: return null
    if (access.isBlank() || refresh.isBlank()) return null
    return IssuedTokens(access, refresh)
}

/**
 * 令牌响应诊断摘要。只暴露错误码与字段是否存在，不写任何令牌原文。
 */
fun diagnoseTokenResponse(raw: String): String {
    val obj = runCatching { authJson.parseToJsonElement(raw) as? JsonObject }.getOrNull()
        ?: return "token_body_not_json length=${raw.length.coerceAtMost(MAX_DIAG_LEN)}"
    val error = obj.string("error")
    val description = obj.string("error_description")
    if (error != null || description != null) {
        return buildString {
            append("oauth_error")
            if (error != null) append(" error=").append(error)
            if (description != null) append(" description=").append(description.take(MAX_DIAG_LEN))
        }
    }
    val hasAccess = !obj.string("access_token").isNullOrBlank()
    val hasRefresh = !obj.string("refresh_token").isNullOrBlank()
    return "token_fields hasAccess=$hasAccess hasRefresh=$hasRefresh"
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

private fun JsonObject.string(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull

private const val MAX_DIAG_LEN = 240
