package com.ainscel.mymail.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** 用系统密钥库里的密钥加密本地配置。密钥、客户端 ID 和刷新令牌都走这里，不写日志。 */
class SecretStore(context: Context) {
    private val file = File(context.applicationContext.filesDir, FILE_NAME)
    private val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }

    fun read(): Secrets {
        synchronized(file) {
            if (!file.exists()) return Secrets()
            return try {
                Secrets.fromJson(String(decrypt(file.readBytes()), Charsets.UTF_8))
            } catch (_: Exception) {
                file.delete()
                Secrets()
            }
        }
    }

    fun write(secrets: Secrets) {
        synchronized(file) {
            val encoded = encrypt(secrets.normalized().toJson().toByteArray(Charsets.UTF_8))
            val temporary = File(file.parentFile, "$FILE_NAME.tmp")
            temporary.writeBytes(encoded)
            if (!temporary.renameTo(file)) {
                file.writeBytes(encoded)
                temporary.delete()
            }
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
        val ivLength = payload[0].toInt() and 0xff
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

        fun toJson(): String = JSONObject()
            .put(JSON_API_KEY, deepSeekApiKey)
            .put(JSON_MODEL, deepSeekModel)
            .put(JSON_CLIENT_ID, googleClientId)
            .put(JSON_CLIENT_SECRET, googleClientSecret)
            .toString()

        companion object {
            fun fromJson(raw: String): Secrets {
                val json = JSONObject(raw)
                return Secrets(
                    deepSeekApiKey = json.optString(JSON_API_KEY),
                    deepSeekModel = json.optString(JSON_MODEL).ifBlank { DEFAULT_MODEL },
                    googleClientId = json.optString(JSON_CLIENT_ID),
                    googleClientSecret = json.optString(JSON_CLIENT_SECRET),
                ).normalized()
            }
        }
    }

    companion object {
        const val DEFAULT_MODEL = "deepseek-flash"

        private const val ANDROID_KEY_STORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "mymail.secrets"
        private const val FILE_NAME = "secrets.bin"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
        private const val JSON_API_KEY = "deepSeekApiKey"
        private const val JSON_MODEL = "deepSeekModel"
        private const val JSON_CLIENT_ID = "googleClientId"
        private const val JSON_CLIENT_SECRET = "googleClientSecret"
    }
}
