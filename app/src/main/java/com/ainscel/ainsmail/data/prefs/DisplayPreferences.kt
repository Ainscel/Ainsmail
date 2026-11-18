package com.ainscel.ainsmail.data.prefs

import android.content.Context
import com.ainscel.ainsmail.model.AccountBadgeStyle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 显示偏好。不进加密账号文件，改它也不收信。
 * 构造时读一次，之后界面和列表订同一份流。
 */
class DisplayPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val badge = MutableStateFlow(loadBadge())

    val accountBadge: StateFlow<AccountBadgeStyle> = badge.asStateFlow()

    fun setAccountBadge(style: AccountBadgeStyle) {
        if (badge.value == style) return
        prefs.edit().putString(KEY_ACCOUNT_BADGE, style.name).apply()
        badge.value = style
    }

    private fun loadBadge(): AccountBadgeStyle {
        val saved = prefs.getString(KEY_ACCOUNT_BADGE, null) ?: return AccountBadgeStyle.EveryRow
        return AccountBadgeStyle.entries.find { it.name == saved } ?: AccountBadgeStyle.EveryRow
    }

    private companion object {
        const val FILE = "ainsmail-display"
        const val KEY_ACCOUNT_BADGE = "account_badge"
    }
}
