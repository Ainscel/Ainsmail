package com.ainscel.ainsmail.security

import android.content.Context
import com.ainscel.ainsmail.model.Account
import com.ainscel.ainsmail.model.defaultDisplayName
import com.ainscel.ainsmail.model.gmailClientReady
import com.ainscel.ainsmail.model.mailboxAddress
import com.ainscel.ainsmail.model.qqAddress
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface AccountDirectory {
    data object Loading : AccountDirectory
    data class Ready(val accounts: List<Account>) : AccountDirectory
    data object Unreadable : AccountDirectory
}

sealed interface AccountWrite {
    data class Saved(val id: String) : AccountWrite
    data object Duplicate : AccountWrite
    data object InvalidAddress : AccountWrite
    data object BlankCode : AccountWrite
    data object BlankName : AccountWrite
    data object Unreadable : AccountWrite
    data object BadImage : AccountWrite
    /** 令牌为空等无法当成授权码缺失来提示的失败。 */
    data object Rejected : AccountWrite
}

/**
 * 已登录账号的唯一入口。
 * 界面只看见 [Account]。QQ 授权码与 Gmail 访问令牌留在加密文件里。
 */
class AccountBook(
    context: Context,
    private val store: SecretStore = SecretStore(context),
) {
    private val filesDir = context.applicationContext.filesDir
    private val state = MutableStateFlow<AccountDirectory>(AccountDirectory.Loading)
    private val lock = Any()
    private var records: List<AccountRecord> = emptyList()

    val directory: StateFlow<AccountDirectory> = state.asStateFlow()

    fun load() {
        synchronized(lock) {
            when (val read = store.readAccounts()) {
                is AccountRead.Ok -> {
                    records = read.accounts
                    publish()
                }
                AccountRead.Missing -> {
                    records = emptyList()
                    publish()
                }
                is AccountRead.Unreadable -> state.value = AccountDirectory.Unreadable
            }
        }
    }

    fun gmailReady(): Boolean {
        val read = store.read()
        if (read !is SecretRead.Ok) {
            LoginLog.w("gmail_ready", "secrets unavailable kind=${read::class.simpleName}")
            return false
        }
        val ready = gmailClientReady(read.secrets.googleClientId)
        if (!ready) LoginLog.w("gmail_ready", "android_client_id_blank")
        return ready
    }

    /**
     * 给同步层取凭证。返回 null 表示没有这条账号或凭证为空。
     * 不要放进 UI state。
     */
    fun credentialsFor(id: String): ProviderCredentials? = synchronized(lock) {
        val record = records.find { it.id == id } ?: return null
        when (record.provider) {
            AccountRecord.PROVIDER_QQ -> {
                val code = record.authCode.trim()
                if (code.isEmpty()) null else ProviderCredentials.Qq(record.address, code)
            }
            AccountRecord.PROVIDER_GMAIL -> {
                val token = record.accessToken.trim()
                if (token.isEmpty()) {
                    null
                } else {
                    ProviderCredentials.Gmail(
                        address = record.address,
                        accessToken = token,
                        expiresAtEpochMs = record.accessTokenExpiresAtEpochMs,
                    )
                }
            }
            else -> null
        }
    }

    fun addQq(address: String, displayName: String, authCode: String, avatar: ByteArray?): AccountWrite {
        val email = qqAddress(address) ?: return AccountWrite.InvalidAddress
        val code = authCode.trim()
        if (code.isEmpty()) return AccountWrite.BlankCode
        synchronized(lock) {
            replaceSameAddress(email, AccountRecord.PROVIDER_QQ, displayName) { record, name ->
                record.copy(displayName = name, authCode = code)
            }?.let { return it }
        }
        return insert(
            providerKey = AccountRecord.PROVIDER_QQ,
            address = email,
            displayName = displayName,
            authCode = code,
            accessToken = "",
            accessTokenExpiresAtEpochMs = 0L,
            avatar = avatar,
            avatarRequired = avatar != null,
        )
    }

    fun addGmail(
        address: String,
        displayName: String,
        accessToken: String,
        expiresAtEpochMs: Long,
        avatar: ByteArray?,
    ): AccountWrite {
        val email = mailboxAddress(address) ?: return AccountWrite.InvalidAddress
        val token = accessToken.trim()
        if (token.isEmpty()) return AccountWrite.Rejected
        synchronized(lock) {
            replaceSameAddress(email, AccountRecord.PROVIDER_GMAIL, displayName) { record, name ->
                record.copy(
                    displayName = name,
                    accessToken = token,
                    accessTokenExpiresAtEpochMs = expiresAtEpochMs,
                )
            }?.let { return it }
        }
        return insert(
            providerKey = AccountRecord.PROVIDER_GMAIL,
            address = email,
            displayName = displayName,
            authCode = "",
            accessToken = token,
            accessTokenExpiresAtEpochMs = expiresAtEpochMs,
            avatar = avatar,
            avatarRequired = false,
        )
    }

    fun rename(id: String, displayName: String): AccountWrite = synchronized(lock) {
        val name = displayName.trim()
        if (name.isEmpty()) return AccountWrite.BlankName
        if (records.none { it.id == id }) return AccountWrite.Unreadable
        write(records.map { if (it.id == id) it.copy(displayName = name) else it }, id)
    }

    fun replaceAvatar(id: String, bytes: ByteArray): AccountWrite = synchronized(lock) {
        val current = records.find { it.id == id } ?: return AccountWrite.Unreadable
        val scaled = scaleAvatar(bytes) ?: return AccountWrite.BadImage
        val relative = writeAvatar(id, scaled) ?: return AccountWrite.BadImage
        val written = write(records.map { if (it.id == id) it.copy(avatarFile = relative) else it }, id)
        if (written is AccountWrite.Saved) deleteAvatar(current.avatarFile) else deleteAvatar(relative)
        written
    }

    fun remove(id: String): AccountWrite = synchronized(lock) {
        val current = records.find { it.id == id } ?: return AccountWrite.Saved(id)
        val written = write(records.filterNot { it.id == id }, id)
        if (written is AccountWrite.Saved) deleteAvatar(current.avatarFile)
        written
    }

    /** 静默重授权成功后写回访问令牌。不改展示名与头像。 */
    fun updateGmailAccess(id: String, accessToken: String, expiresAtEpochMs: Long): AccountWrite =
        synchronized(lock) {
            val token = accessToken.trim()
            if (token.isEmpty()) return AccountWrite.Rejected
            val current = records.find { it.id == id } ?: return AccountWrite.Unreadable
            if (current.provider != AccountRecord.PROVIDER_GMAIL) return AccountWrite.Unreadable
            write(
                records.map {
                    if (it.id == id) {
                        it.copy(accessToken = token, accessTokenExpiresAtEpochMs = expiresAtEpochMs)
                    } else {
                        it
                    }
                },
                id,
            )
        }

    /**
     * 同地址则写回原记录。调用方须已持有 [lock]。
     * 没有这条地址时返回 null，由调用方插入。
     */
    private fun replaceSameAddress(
        address: String,
        providerKey: String,
        displayName: String,
        copy: (AccountRecord, String) -> AccountRecord,
    ): AccountWrite? {
        val existing = records.find { it.address.equals(address, ignoreCase = true) } ?: return null
        if (existing.provider != providerKey) return AccountWrite.Duplicate
        val name = displayName.trim().ifBlank { existing.displayName }
        val next = records.map { record ->
            if (record.id != existing.id) record else copy(record, name)
        }
        return write(next, existing.id)
    }

    private fun insert(
        providerKey: String,
        address: String,
        displayName: String,
        authCode: String,
        accessToken: String,
        accessTokenExpiresAtEpochMs: Long,
        avatar: ByteArray?,
        avatarRequired: Boolean,
    ): AccountWrite = synchronized(lock) {
        if (records.any { it.address.equals(address, ignoreCase = true) }) {
            return AccountWrite.Duplicate
        }
        val name = displayName.trim().ifBlank { defaultDisplayName(address) }
        if (name.isEmpty()) return AccountWrite.BlankName
        val scaled = avatar?.let { scaleAvatar(it) }
        if (avatarRequired && scaled == null) return AccountWrite.BadImage
        val id = UUID.randomUUID().toString()
        val relative = scaled?.let { writeAvatar(id, it) }
        if (avatarRequired && relative == null) return AccountWrite.BadImage
        val written = write(
            records + AccountRecord(
                id = id,
                provider = providerKey,
                address = address,
                displayName = name,
                avatarFile = relative,
                authCode = authCode,
                accessToken = accessToken,
                accessTokenExpiresAtEpochMs = accessTokenExpiresAtEpochMs,
            ),
            id,
        )
        if (written !is AccountWrite.Saved) deleteAvatar(relative)
        written
    }

    /** 调用方须已持有 [lock]。写失败时先回到改之前的记录，再按磁盘结果刷新。 */
    private fun write(next: List<AccountRecord>, savedId: String): AccountWrite {
        val previous = records
        records = next
        return try {
            store.writeAccounts(records)
            publish()
            AccountWrite.Saved(savedId)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            records = previous
            throw cancelled
        } catch (error: Exception) {
            records = previous
            LoginLog.e(
                "account_commit",
                "${error.javaClass.simpleName}: ${error.message}",
                error,
            )
            load()
            AccountWrite.Unreadable
        }
    }

    private fun publish() {
        state.value = AccountDirectory.Ready(records.mapNotNull { it.profile() })
    }

    private fun writeAvatar(accountId: String, bytes: ByteArray): String? {
        val relative = "account/$accountId/avatar-${System.nanoTime()}"
        val target = filesDir.resolveInside(relative) ?: return null
        return try {
            target.parentFile?.mkdirs()
            target.writeBytes(bytes)
            relative
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }

    private fun deleteAvatar(relative: String?) {
        if (relative == null) return
        filesDir.resolveInside(relative)?.delete()
    }
}

/** 只允许写到 files 目录里面，挡住带 .. 的路径。 */
internal fun File.resolveInside(relative: String): File? {
    if (relative.isBlank() || relative.startsWith("/") || relative.contains("..")) return null
    return try {
        val root = canonicalFile
        val target = File(root, relative).canonicalFile
        if (!target.path.startsWith(root.path + File.separator)) null else target
    } catch (cancelled: kotlinx.coroutines.CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }
}
