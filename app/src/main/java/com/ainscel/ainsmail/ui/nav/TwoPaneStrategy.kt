package com.ainscel.ainsmail.ui.nav

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.NavMetadataKey
import androidx.navigation3.runtime.contains
import androidx.navigation3.runtime.get
import androidx.navigation3.runtime.metadata
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import androidx.navigation3.ui.NavDisplay
import com.ainscel.ainsmail.ui.detail.EmptyDetail
import com.ainscel.ainsmail.ui.settings.SettingsPaneEmpty
import com.ainscel.ainsmail.ui.shell.LocalDetailHandlesBack
import com.ainscel.ainsmail.ui.shell.SplitLayout

/** 左栏还没有右栏内容时，右侧画哪一种空状态。 */
enum class PrimaryEmpty {
    Detail,
    Settings,
}

/**
 * 宽屏下，最近的左栏和压在它上面的右栏并排。
 * 邮件列表、设置栏目都是左栏；邮件正文、设置栏目内容都是右栏。比例由外部的同一份状态保存。
 * 栈顶既不是左栏也不是右栏时，这个策略让位，页面整屏显示。
 */
class TwoPaneStrategy(
    private val isWide: Boolean,
    private val preferredFraction: MutableFloatState,
    private val onResizing: (Boolean) -> Unit,
) : SceneStrategy<NavKey> {
    override fun SceneStrategyScope<NavKey>.calculateScene(entries: List<NavEntry<NavKey>>): Scene<NavKey>? {
        if (!isWide) return null
        val primary = entries.findLast { it.metadata.contains(PrimaryKey) } ?: return null
        val top = entries.last()
        val topIsPrimary = top.metadata.contains(PrimaryKey)
        val topIsSecondary = top.metadata.contains(SecondaryKey)
        if (!topIsPrimary && !topIsSecondary) return null
        val secondary = top.takeIf { topIsSecondary }
        return TwoPaneScene(
            previousEntries = entries.dropLast(1),
            primary = primary,
            secondary = secondary,
            preferredFraction = preferredFraction,
            onResizing = onResizing,
        )
    }

    companion object {
        private object PrimaryKey : NavMetadataKey<Boolean>
        private object SecondaryKey : NavMetadataKey<Boolean>
        private object EmptyKey : NavMetadataKey<PrimaryEmpty>

        fun primaryPane(empty: PrimaryEmpty = PrimaryEmpty.Detail) = metadata {
            put(PrimaryKey, true)
            put(EmptyKey, empty)
        }

        fun secondaryPane() = metadata { put(SecondaryKey, true) } + noSceneFade()

        internal fun emptyOf(entry: NavEntry<NavKey>): PrimaryEmpty {
            return entry.metadata[EmptyKey] ?: PrimaryEmpty.Detail
        }
    }
}

@Composable
fun rememberTwoPaneStrategy(
    widthIsWide: Boolean,
    preferredFraction: MutableFloatState,
    onResizing: (Boolean) -> Unit = {},
): TwoPaneStrategy {
    val current = rememberUpdatedState(onResizing)
    return remember(widthIsWide, preferredFraction) {
        TwoPaneStrategy(widthIsWide, preferredFraction) { current.value(it) }
    }
}

private data class TwoPaneScene(
    override val previousEntries: List<NavEntry<NavKey>>,
    private val primary: NavEntry<NavKey>,
    private val secondary: NavEntry<NavKey>?,
    private val preferredFraction: MutableFloatState,
    private val onResizing: (Boolean) -> Unit,
) : Scene<NavKey> {
    // 只认左栏。右栏换成另一封信或另一个栏目时，场景身份不变，整页不会被拆掉重画。
    override val key: Any = primary.contentKey

    override val entries: List<NavEntry<NavKey>> = listOfNotNull(primary, secondary)

    // 左栏还是原来的那一栏时不做淡入淡出。换成另一页时交回 NavDisplay 的默认过渡。
    override val metadata: Map<String, Any> = holdSceneWhenPaneStays()

    override val content: @Composable () -> Unit = {
        SplitLayout(
            preferredFraction = preferredFraction,
            rightOpen = secondary != null,
            onResizing = onResizing,
        ) {
            list { primary.Content() }
            detail {
                CompositionLocalProvider(LocalDetailHandlesBack provides false) {
                    if (secondary == null) {
                        when (TwoPaneStrategy.emptyOf(primary)) {
                            PrimaryEmpty.Settings -> SettingsPaneEmpty()
                            PrimaryEmpty.Detail -> EmptyDetail()
                        }
                    } else {
                        secondary.Content()
                    }
                }
            }
        }
    }
}

private fun samePane(initial: Scene<*>, target: Scene<*>): Boolean {
    return initial is TwoPaneScene && target is TwoPaneScene && initial.key == target.key
}

private fun stillTransform() = ContentTransform(EnterTransition.None, ExitTransition.None)

/** 窄屏整页切换正文或栏目时，不把这一页淡掉。 */
private fun noSceneFade(): Map<String, Any> =
    NavDisplay.transitionSpec { stillTransform() } +
        NavDisplay.popTransitionSpec { stillTransform() } +
        NavDisplay.predictivePopTransitionSpec { stillTransform() }

private fun holdSceneWhenPaneStays(): Map<String, Any> =
    NavDisplay.transitionSpec {
        if (samePane(initialState, targetState)) stillTransform() else null
    } + NavDisplay.popTransitionSpec {
        if (samePane(initialState, targetState)) stillTransform() else null
    } + NavDisplay.predictivePopTransitionSpec {
        if (samePane(initialState, targetState)) stillTransform() else null
    }
