package com.ainscel.ainsmail.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import kotlinx.serialization.SerializationException
import java.io.File
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.util.concurrent.CancellationException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 用系统密钥库加密本地配置。
 * 全局的 DeepSeek 和 Google Android 客户端 ID（以及可选的 Web 客户端密钥）在 [Secrets]。
 * 按账号的授权码和刷新令牌在同一份文件的另一段，不进 [Secrets]，也不写日志。
 */
class SecretStore(context: Context) {
    private val file = File(context.applicationContext.filesDir, FILE_NAME)
    private val gate = gateFor(file.absolutePath)
    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
    }

    fun read(): SecretRead = when (val loaded = load()) {
        is Loaded.Ok -> SecretRead.Ok(loaded.document.toSecrets())
        Loaded.Missing -> SecretRead.Missing
        is Loaded.Unreadable -> SecretRead.Unreadable(loaded.cause)
    }

    fun readAccounts(): AccountRead = when (val loaded = load()) {
        is Loaded.Ok -> AccountRead.Ok(loaded.document.accounts)
        Loaded.Missing -> AccountRead.Missing
        is Loaded.Unreadable -> AccountRead.Unreadable(loaded.cause)
    }

    /** 只替换全局配置。文件里已有的账号段保持不动。 */
    fun write(secrets: Secrets) {
        synchronized(gate) {
            val current = when (val loaded = load()) {
                is Loaded.Ok -> loaded.document
                Loaded.Missing -> SecretDocument()
                is Loaded.Unreadable -> throw loaded.cause
            }
            persist(current.replacingSecrets(secrets))
        }
    }

    /** 只替换账号段。全局配置保持不动。读不出来时不写，避免把密钥覆盖掉。 */
    fun writeAccounts(accounts: List<AccountRecord>) {
        synchronized(gate) {
            val current = when (val loaded = load()) {
                is Loaded.Ok -> loaded.document
                Loaded.Missing -> SecretDocument()
                is Loaded.Unreadable -> throw loaded.cause
            }
            persist(current.replacingAccounts(accounts))
        }
    }

    private fun load(): Loaded {
        synchronized(gate) {
            if (!file.exists()) return Loaded.Missing
            return try {
                Loaded.Ok(parseSecretDocument(String(decrypt(file.readBytes()), Charsets.UTF_8)))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (error.isCorrupt()) {
                    file.delete()
                    Loaded.Missing
                } else {
                    Loaded.Unreadable(error)
                }
            }
        }
    }

    private fun persist(document: SecretDocument) {
        val encoded = encrypt(document.encode().toByteArray(Charsets.UTF_8))
        val temporary = File(file.parentFile, "$FILE_NAME.tmp")
        temporary.writeBytes(encoded)
        if (!temporary.renameTo(file)) {
            file.writeBytes(encoded)
            temporary.delete()
        }
    }

    private fun secretKey(): SecretKey {
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val iv = cipher.iv
        val cipherText = cipher.doFinal(plain)
        val output = ByteArray(1 + iv.size + cipherText.size)
        output[0] = iv.size.toByte()
        System.arraycopy(iv, 0, output, 1, iv.size)
        System.arraycopy(cipherText, 0, output, 1 + iv.size, cipherText.size)
        return output
    }

    private fun decrypt(payload: ByteArray): ByteArray {
        if (payload.size < 2) throw IllegalArgumentException("empty")
        val ivLength = payload[0].toInt() and 0xff
        if (ivLength <= 0 || payload.size < 1 + ivLength + 1) throw IllegalArgumentException("short")
        val iv = payload.copyOfRange(1, 1 + ivLength)
        val cipherText = payload.copyOfRange(1 + ivLength, payload.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            secretKey(),
            GCMParameterSpec(GCM_TAG_BITS, iv),
        )
        return cipher.doFinal(cipherText)
    }

    data class Secrets(
        val deepSeekApiKey: String = "",
        val deepSeekModel: String = DEFAULT_MODEL,
        val googleClientId: String = "",
        val googleClientSecret: String = "",
    ) {
        fun normalized(): Secrets = copy(
            deepSeekApiKey = deepSeekApiKey.trim(),
            deepSeekModel = deepSeekModel.trim().ifBlank { DEFAULT_MODEL },
            googleClientId = googleClientId.trim(),
            googleClientSecret = googleClientSecret.trim(),
        )
    }

    companion object {
        const val DEFAULT_MODEL = "deepseek-flash"

        private const val ANDROID_KEY_STORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "ainsmail.secrets"
        private const val FILE_NAME = "secrets.bin"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
        private val gates = HashMap<String, Any>()

        private fun gateFor(path: String): Any = synchronized(gates) {
            gates.getOrPut(path) { Any() }
        }
    }
}

sealed interface SecretRead {
    data class Ok(val secrets: SecretStore.Secrets) : SecretRead
    data object Missing : SecretRead
    data class Unreadable(val cause: Exception) : SecretRead
}

sealed interface AccountRead {
    data class Ok(val accounts: List<AccountRecord>) : AccountRead
    data object Missing : AccountRead
    data class Unreadable(val cause: Exception) : AccountRead
}

private sealed interface Loaded {
    data class Ok(val document: SecretDocument) : Loaded
    data object Missing : Loaded
    data class Unreadable(val cause: Exception) : Loaded
}

/** 内容坏了才删文件。磁盘错误留下来，取消必须原样抛出。 */
private fun Exception.isCorrupt(): Boolean = when (this) {
    is GeneralSecurityException,
    is SerializationException,
    is IllegalArgumentException,
    is IndexOutOfBoundsException,
    -> true
    else -> false
}
