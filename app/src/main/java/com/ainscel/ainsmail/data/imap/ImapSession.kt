package com.ainscel.ainsmail.data.imap

import com.ainscel.ainsmail.model.Provider
import com.ainscel.ainsmail.security.ProviderCredentials
import com.ainscel.ainsmail.security.gmailAccessExpired
import jakarta.mail.Session
import jakarta.mail.Store
import java.util.Properties

sealed interface ImapConnectResult {
    data class Ok(val store: Store) : ImapConnectResult
    data object NeedsReauth : ImapConnectResult
    data class Failed(val reason: String) : ImapConnectResult
}

/**
 * 打开一条 IMAP TLS 连接。调用方负责 [Store.close]。
 * Gmail 令牌已过期时返回 [ImapConnectResult.NeedsReauth]，不在这里弹授权页。
 */
fun openImapStore(credentials: ProviderCredentials): ImapConnectResult {
    return try {
        when (credentials) {
            is ProviderCredentials.Qq -> {
                val endpoint = imapEndpoint(Provider.Qq)
                val store = createStore(oauth = false)
                store.connect(endpoint.host, endpoint.port, credentials.address, credentials.authCode)
                ImapConnectResult.Ok(store)
            }
            is ProviderCredentials.Gmail -> {
                if (gmailAccessExpired(credentials.expiresAtEpochMs)) {
                    return ImapConnectResult.NeedsReauth
                }
                val endpoint = imapEndpoint(Provider.Gmail)
                val store = createStore(oauth = true)
                store.connect(endpoint.host, endpoint.port, credentials.address, credentials.accessToken)
                ImapConnectResult.Ok(store)
            }
        }
    } catch (error: Exception) {
        val message = error.message.orEmpty()
        if (credentials is ProviderCredentials.Gmail && looksLikeAuthFailure(message)) {
            ImapConnectResult.NeedsReauth
        } else {
            ImapConnectResult.Failed(error.javaClass.simpleName + ": " + message.take(120))
        }
    }
}

private fun createStore(oauth: Boolean): Store {
    val props = Properties()
    props["mail.store.protocol"] = "imaps"
    props["mail.imaps.port"] = "993"
    props["mail.imaps.ssl.enable"] = "true"
    props["mail.imaps.connectiontimeout"] = "20000"
    props["mail.imaps.timeout"] = "60000"
    if (oauth) {
        props["mail.imaps.auth.mechanisms"] = "XOAUTH2"
        props["mail.imaps.auth.login.disable"] = "true"
        props["mail.imaps.auth.plain.disable"] = "true"
    }
    val session = Session.getInstance(props)
    return session.getStore("imaps")
}

private fun looksLikeAuthFailure(message: String): Boolean {
    val lower = message.lowercase()
    return "auth" in lower || "login" in lower || "credential" in lower || "oauth" in lower ||
        "invalid credentials" in lower || "authenticationfailed" in lower
}
