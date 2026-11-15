package com.ainscel.ainsmail.ui.nav

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.ui.NavDisplay
import com.ainscel.ainsmail.ui.detail.ComposePlaceholder
import com.ainscel.ainsmail.ui.detail.MailDetailPane
import com.ainscel.ainsmail.ui.list.MailListPane
import com.ainscel.ainsmail.ui.mailbox.MailboxIntent
import com.ainscel.ainsmail.ui.mailbox.MailboxViewModel
import com.ainscel.ainsmail.ui.settings.SettingsHomePane
import com.ainscel.ainsmail.ui.settings.SettingsSection
import com.ainscel.ainsmail.ui.settings.SettingsSectionPane
import com.ainscel.ainsmail.ui.shell.OverlayController
import kotlinx.coroutines.CoroutineScope

@Composable
internal fun MailNavHost(
    mailbox: MailboxViewModel,
    backStack: NavBackStack<NavKey>,
    overlay: OverlayController,
    scope: CoroutineScope,
    strategy: SceneStrategy<NavKey>,
    panelWidthPx: Float,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NavDisplay(
        backStack = backStack,
        modifier = modifier.fillMaxSize(),
        onBack = {
            if (backStack.size > 1) {
                backStack.removeLastOrNull()
            } else {
                onExit()
            }
        },
        sceneStrategies = listOf(strategy),
        entryProvider = entryProvider {
            entry<MailListRoute>(
                metadata = TwoPaneStrategy.primaryPane(PrimaryEmpty.Detail),
            ) {
                val state = mailbox.state
                MailListPane(
                    state = state,
                    openMailId = backStack.filterIsInstance<MailDetailRoute>().lastOrNull()?.mailId,
                    onIntent = mailbox::onIntent,
                    onOpenMail = { id -> openMail(backStack, id) },
                    onCompose = { backStack.add(ComposeRoute()) },
                    onOpenOverlay = { overlay.open(scope) },
                    onOverlayDrag = { overlay.dragBy(it, panelWidthPx) },
                    onOverlayRelease = { overlay.release(scope) },
                )
            }
            entry<MailDetailRoute>(metadata = TwoPaneStrategy.secondaryPane()) { route ->
                // 读取 state 让这一页跟着投影重组。查找本身不再碰快照。
                @Suppress("UNUSED_VARIABLE")
                val state = mailbox.state
                MailDetailPane(
                    detail = mailbox.detail(route.mailId),
                    onClose = { backStack.removeAll { it is MailDetailRoute } },
                    onOriginalLayout = { mailbox.perform(MailboxIntent.ShowOriginalPending) },
                    onEdit = { mailbox.perform(MailboxIntent.ShowEditPending) },
                    onDelete = {
                        mailbox.perform(MailboxIntent.DeleteMail(route.mailId))
                        backStack.removeAll { it is MailDetailRoute }
                    },
                )
            }
            entry<SettingsHomeRoute>(
                metadata = TwoPaneStrategy.primaryPane(PrimaryEmpty.Settings),
            ) {
                SettingsHomePane(
                    selected = backStack.filterIsInstance<SettingsSectionRoute>().lastOrNull()?.section,
                    onBack = {
                        backStack.removeAll {
                            it is SettingsHomeRoute || it is SettingsSectionRoute
                        }
                    },
                    onOpen = { section -> openSettingsSection(backStack, section) },
                )
            }
            entry<SettingsSectionRoute>(metadata = TwoPaneStrategy.secondaryPane()) { route ->
                SettingsSectionPane(
                    section = route.section,
                    onBack = { backStack.removeLastOrNull() },
                )
            }
            entry<ComposeRoute> { route ->
                @Suppress("UNUSED_VARIABLE")
                val state = mailbox.state
                ComposePlaceholder(
                    replySubject = route.mailId?.let(mailbox::subjectOf),
                    onBack = { backStack.removeLastOrNull() },
                )
            }
        },
    )
}

private fun openSettingsSection(
    backStack: NavBackStack<NavKey>,
    section: SettingsSection,
) {
    val current = backStack.lastOrNull()
    if (current is SettingsSectionRoute && current.section == section) return
    backStack.removeAll { it is SettingsSectionRoute }
    backStack.add(SettingsSectionRoute(section))
}

private fun openMail(backStack: NavBackStack<NavKey>, id: String) {
    val current = backStack.lastOrNull()
    if (current is MailDetailRoute && current.mailId == id) return
    backStack.removeAll { it is MailDetailRoute }
    backStack.add(MailDetailRoute(id))
}
