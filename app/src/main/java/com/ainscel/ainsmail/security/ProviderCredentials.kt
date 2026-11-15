package com.ainscel.ainsmail.security

/**
 * 同步层（IMAP 等）读取的凭证。禁止进入 Compose / ViewModel UI state。
 * [toString] 不带原文，避免进日志。
 */
sealed interface ProviderCredentials {
    val address: String

    data class Qq(
        override val address: String,
        val authCode: String,
    ) : ProviderCredentials {
        override fun toString(): String = "ProviderCredentials.Qq(address=$address)"
    }

    data class Gmail(
        override val address: String,
        val accessToken: String,
        val expiresAtEpochMs: Long,
    ) : ProviderCredentials {
        override fun toString(): String = "ProviderCredentials.Gmail(address=$address)"
    }
}

/** Gmail 访问令牌是否已到或过了过期时间。 */
fun gmailAccessExpired(
    expiresAtEpochMs: Long,
    nowEpochMs: Long = System.currentTimeMillis(),
): Boolean = nowEpochMs >= expiresAtEpochMs
