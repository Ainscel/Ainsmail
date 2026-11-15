package com.ainscel.ainsmail.data.imap

import com.ainscel.ainsmail.model.Provider

data class ImapEndpoint(
    val host: String,
    val port: Int = 993,
)

fun imapEndpoint(provider: Provider): ImapEndpoint = when (provider) {
    Provider.Qq -> ImapEndpoint("imap.qq.com")
    Provider.Gmail -> ImapEndpoint("imap.gmail.com")
}
