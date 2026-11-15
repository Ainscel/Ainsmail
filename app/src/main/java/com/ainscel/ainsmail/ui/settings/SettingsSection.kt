package com.ainscel.ainsmail.ui.settings

import androidx.annotation.StringRes
import com.ainscel.ainsmail.R
import kotlinx.serialization.Serializable

/** 设置栏目。没有内容的栏目先留空页。 */
@Serializable
enum class SettingsSection(@StringRes val label: Int) {
    Accounts(R.string.settings_accounts),
    Notifications(R.string.settings_notifications),
    Privacy(R.string.settings_privacy),
    Secrets(R.string.settings_secrets),
}
