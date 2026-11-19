package com.ainscel.ainsmail.data.prefs

import android.content.Context
import com.ainscel.ainsmail.model.AccountBadgeStyle
import com.ainscel.ainsmail.model.WeekDateStyle
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
    private val weekDate = MutableStateFlow(loadWeekDate())

    val accountBadge: StateFlow<AccountBadgeStyle> = badge.asStateFlow()
    val weekDateStyle: StateFlow<WeekDateStyle> = weekDate.asStateFlow()

    fun setAccountBadge(style: AccountBadgeStyle) {
        if (badge.value == style) return
        prefs.edit().putString(KEY_ACCOUNT_BADGE, style.name).apply()
        badge.value = style
    }

    fun setWeekDateStyle(style: WeekDateStyle) {
        if (weekDate.value == style) return
        prefs.edit().putString(KEY_WEEK_DATE, style.name).apply()
        weekDate.value = style
    }

    private fun loadBadge(): AccountBadgeStyle = loadEnum(KEY_ACCOUNT_BADGE, AccountBadgeStyle.EveryRow)

    private fun loadWeekDate(): WeekDateStyle = loadEnum(KEY_WEEK_DATE, WeekDateStyle.Date)

    private inline fun <reified T : Enum<T>> loadEnum(key: String, fallback: T): T {
        val saved = prefs.getString(key, null) ?: return fallback
        return enumValues<T>().firstOrNull { it.name == saved } ?: fallback
    }

    private companion object {
        const val FILE = "ainsmail-display"
        const val KEY_ACCOUNT_BADGE = "account_badge"
        const val KEY_WEEK_DATE = "week_date"
    }
}
