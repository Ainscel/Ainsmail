package com.ainscel.mymail.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.NavMetadataKey
import androidx.navigation3.runtime.contains
import androidx.navigation3.runtime.metadata
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import com.ainscel.mymail.ui.detail.EmptyDetail
import com.ainscel.mymail.ui.settings.SettingsPaneEmpty
import com.ainscel.mymail.ui.shell.LocalDetailHandlesBack
import com.ainscel.mymail.ui.shell.SplitLayout

/**
 * 宽屏下，最近的左栏和压在它上面的右栏并排。
 * 邮件列表、设置栏目都是左栏；邮件正文、设置栏目内容都是右栏。比例由外部的同一份状态保存。
 * 栈顶既不是左栏也不是右栏时，这个策略让位，页面整屏显示。
 */
class TwoPaneStrategy(
    private val isWide: Boolean,
    private val preferredFraction: MutableFloatState,
) : SceneStrategy<NavKey> {
    override fun SceneStrategyScope<NavKey>.calculateScene(entries: List<NavEntry<NavKey>>): Scene<NavKey>? {
        if (!isWide) return null
        val primary = entries.findLast { it.metadata.contains(PrimaryKey) } ?: return null
        val top = entries.last()
        val topIsPrimary = top.metadata.contains(PrimaryKey)
        val topIsSecondary = top.metadata.contains(SecondaryKey)
        if (!topIsPrimary && !topIsSecondary) return null
        return TwoPaneScene(
            key = primary.contentKey,
            previousEntries = entries.dropLast(1),
            primary = primary,
            secondary = top.takeIf { topIsSecondary },
            preferredFraction = preferredFraction,
        )
    }

    companion object {
        private object PrimaryKey : NavMetadataKey<Boolean>
        private object SecondaryKey : NavMetadataKey<Boolean>

        fun primaryPane() = metadata { put(PrimaryKey, true) }
        fun secondaryPane() = metadata { put(SecondaryKey, true) }
    }
}

@Composable
fun rememberTwoPaneStrategy(widthIsWide: Boolean, preferredFraction: MutableFloatState): TwoPaneStrategy {
    return remember(widthIsWide, preferredFraction) {
        TwoPaneStrategy(widthIsWide, preferredFraction)
    }
}

private data class TwoPaneScene(
    override val key: Any,
    override val previousEntries: List<NavEntry<NavKey>>,
    private val primary: NavEntry<NavKey>,
    private val secondary: NavEntry<NavKey>?,
    private val preferredFraction: MutableFloatState,
) : Scene<NavKey> {
    override val entries: List<NavEntry<NavKey>> = listOfNotNull(primary, secondary)

    override val content: @Composable () -> Unit = {
        SplitLayout(
            preferredFraction = preferredFraction,
            rightOpen = secondary != null,
        ) {
            list { primary.Content() }
            detail {
                CompositionLocalProvider(LocalDetailHandlesBack provides false) {
                    if (secondary == null) {
                        when (primary.contentKey) {
                            is SettingsHomeRoute -> SettingsPaneEmpty()
                            else -> EmptyDetail()
                        }
                    } else {
                        secondary.Content()
                    }
                }
            }
        }
    }
}
