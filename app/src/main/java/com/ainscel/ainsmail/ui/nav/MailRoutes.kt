package com.ainscel.ainsmail.ui.nav

import androidx.navigation3.runtime.NavKey
import com.ainscel.ainsmail.ui.settings.SettingsSection
import kotlinx.serialization.Serializable

@Serializable
data object MailListRoute : NavKey

@Serializable
data class MailDetailRoute(val mailId: String) : NavKey

@Serializable
data object SettingsHomeRoute : NavKey

@Serializable
data class SettingsSectionRoute(val section: SettingsSection) : NavKey

@Serializable
data class ComposeRoute(val mailId: String? = null) : NavKey
