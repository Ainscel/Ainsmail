package com.ainscel.ainsmail.model

/** 第一版 QQ 和 Foxmail 走同一套授权码。地址统一成小写再比较。 */
fun qqAddress(raw: String): String? {
    val address = mailboxAddress(raw) ?: return null
    val domain = address.substringAfter('@')
    if (domain != "qq.com" && domain != "foxmail.com") return null
    return address
}

/** Google 登录返回的邮箱。不限制必须是 @gmail.com，Workspace 也走同一套凭证。 */
fun mailboxAddress(raw: String): String? {
    val address = raw.trim().lowercase()
    val at = address.indexOf('@')
    if (at <= 0 || at != address.lastIndexOf('@')) return null
    if (address.any { it.isWhitespace() }) return null
    return address
}

/** QQ 没改过显示名时，用邮箱 @ 前面的一段。 */
fun defaultDisplayName(address: String): String =
    address.substringBefore('@').trim().ifBlank { address.trim() }

/** Android 客户端只需已保存的 Client ID。密钥不再作为添加 Gmail 的门槛。 */
fun gmailClientReady(clientId: String): Boolean = clientId.isNotBlank()
