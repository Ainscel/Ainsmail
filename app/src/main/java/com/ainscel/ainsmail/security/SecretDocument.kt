package com.ainscel.ainsmail.security

import com.ainscel.ainsmail.model.Account
import com.ainscel.ainsmail.model.Provider
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

const val SECRET_SCHEMA_VERSION = 2

private val documentJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
}

/**
 * 加密文件里的整份内容。
 * [SecretStore.Secrets] 仍只放全局配置。账号凭证在 [accounts]，不进那一组字段。
 */
@Serializable
data class SecretDocument(
    val version: Int = SECRET_SCHEMA_VERSION,
    val deepSeekApiKey: String = "",
    val deepSeekModel: String = SecretStore.DEFAULT_MODEL,
    val googleClientId: String = "",
    val googleClientSecret: String = "",
    val accounts: List<AccountRecord> = emptyList(),
) {
    fun toSecrets(): SecretStore.Secrets = SecretStore.Secrets(
        deepSeekApiKey = deepSeekApiKey,
        deepSeekModel = deepSeekModel,
        googleClientId = googleClientId,
        googleClientSecret = googleClientSecret,
    ).normalized()

    /** 只换全局配置，账号段原样留下。 */
    fun replacingSecrets(secrets: SecretStore.Secrets): SecretDocument {
        val next = secrets.normalized()
        return copy(
            version = SECRET_SCHEMA_VERSION,
            deepSeekApiKey = next.deepSeekApiKey,
            deepSeekModel = next.deepSeekModel,
            googleClientId = next.googleClientId,
            googleClientSecret = next.googleClientSecret,
        )
    }

    /** 只换账号段，全局配置原样留下。 */
    fun replacingAccounts(records: List<AccountRecord>): SecretDocument =
        copy(version = SECRET_SCHEMA_VERSION, accounts = records)

    fun normalized(): SecretDocument = copy(
        version = SECRET_SCHEMA_VERSION,
        deepSeekApiKey = deepSeekApiKey.trim(),
        deepSeekModel = deepSeekModel.trim().ifBlank { SecretStore.DEFAULT_MODEL },
        googleClientId = googleClientId.trim(),
        googleClientSecret = googleClientSecret.trim(),
        accounts = accounts.map { it.normalized() },
    )

    fun encode(): String = documentJson.encodeToString(normalized())
}

/**
 * 一个账号的凭证。界面只拿 [profile]，授权码和刷新令牌不出这个类型。
 * [toString] 不带凭证，避免进日志。
 */
@Serializable
data class AccountRecord(
    val id: String,
    val provider: String,
    val address: String,
    val displayName: String,
    val avatarFile: String? = null,
    val authCode: String = "",
    val refreshToken: String = "",
) {
    fun normalized(): AccountRecord = copy(
        address = address.trim(),
        displayName = displayName.trim(),
        authCode = authCode.trim(),
        refreshToken = refreshToken.trim(),
    )

    fun profile(): Account? {
        val known = when (provider) {
            PROVIDER_QQ -> Provider.Qq
            PROVIDER_GMAIL -> Provider.Gmail
            else -> return null
        }
        if (id.isBlank() || address.isBlank()) return null
        return Account(
            id = id,
            name = displayName.ifBlank { address.substringBefore('@') },
            address = address,
            provider = known,
            avatarFile = avatarFile,
        )
    }

    override fun toString(): String = "AccountRecord(id=$id, provider=$provider, address=$address)"

    companion object {
        const val PROVIDER_QQ = "qq"
        const val PROVIDER_GMAIL = "gmail"
    }
}

fun parseSecretDocument(raw: String): SecretDocument =
    documentJson.decodeFromString<SecretDocument>(raw).normalized()
