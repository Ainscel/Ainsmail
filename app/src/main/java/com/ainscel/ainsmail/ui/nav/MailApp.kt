package com.ainscel.ainsmail.ui.nav

import android.app.Activity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.ainscel.ainsmail.R
import com.ainscel.ainsmail.ui.detail.ComposePlaceholder
import com.ainscel.ainsmail.ui.detail.MailDetailPane
import com.ainscel.ainsmail.ui.list.MailListPane
import com.ainscel.ainsmail.ui.mailbox.MailboxIntent
import com.ainscel.ainsmail.ui.mailbox.MailboxViewModel
import com.ainscel.ainsmail.ui.settings.SettingsHomePane
import com.ainscel.ainsmail.ui.settings.SettingsSection
import com.ainscel.ainsmail.ui.settings.SettingsSectionPane
import com.ainscel.ainsmail.ui.shell.AccountOverlay
import com.ainscel.ainsmail.ui.shell.OverlayController
import com.ainscel.ainsmail.ui.shell.isWideWindow
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

@Composable
fun MailApp(modifier: Modifier = Modifier) {
    val mailbox = viewModel<MailboxViewModel>()
    val state = mailbox.state
    val backStack = rememberNavBackStack(MailListRoute)
    val overlay = remember { OverlayController() }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val originalPending = stringResource(R.string.detail_original_pending)
    val editPending = stringResource(R.string.detail_edit_pending)
    val deletedMessage = stringResource(R.string.list_deleted)
    val loginPending = stringResource(R.string.account_add_login_pending)
    val groupPending = stringResource(R.string.account_add_group_pending)
    val allAccounts = stringResource(R.string.account_all)
    var addDialog by rememberSaveable { mutableStateOf(false) }
    val splitFraction = rememberSaveable { mutableFloatStateOf(0.40f) }
    val context = LocalContext.current
    // 上一版默认是 34%。已经存下来的比例抬到 40%，并夹到左栏下限 35%。
    SideEffect {
        val current = splitFraction.floatValue
        val next = when {
            current == 0.34f -> 0.40f
            current < 0.35f -> 0.35f
            else -> current
        }
        if (next != current) splitFraction.floatValue = next
    }

    MailboxNoticeEffect(
        notice = state.notice,
        hostState = snackbarHostState,
        onUndo = { mailbox.onIntent(MailboxIntent.Undo) },
        onConsumed = { mailbox.onIntent(MailboxIntent.ConsumeNotice(it)) },
    )
    LaunchedEffect(state.accountId, state.folder, state.revision) {
        val openId = backStack.filterIsInstance<MailDetailRoute>().lastOrNull()?.mailId ?: return@LaunchedEffect
        if (!mailbox.staysOpen(openId)) backStack.removeAll { it is MailDetailRoute }
    }
    OverlayBackHandler(overlay, scope)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(innerPadding)) {
            val wide = isWideWindow(maxWidth)
            val strategy = rememberTwoPaneStrategy(wide, splitFraction)
            val panelWidth = minOf(300.dp, maxWidth * 0.88f)
            val panelWidthPx = with(LocalDensity.current) { panelWidth.toPx() }
            Box(Modifier.fillMaxSize()) {
                NavDisplay(
                    backStack = backStack,
                    modifier = Modifier.fillMaxSize(),
                    onBack = {
                        if (backStack.size > 1) {
                            backStack.removeLastOrNull()
                        } else {
                            (context as? Activity)?.finish()
                        }
                    },
                    sceneStrategies = listOf(strategy),
                    entryProvider = entryProvider {
                        entry<MailListRoute>(
                            metadata = TwoPaneStrategy.primaryPane(PrimaryEmpty.Detail),
                        ) {
                            MailListPane(
                                state = state,
                                openMailId = backStack.filterIsInstance<MailDetailRoute>().lastOrNull()?.mailId,
                                onIntent = mailbox::onIntent,
                                onAddTag = mailbox::addTag,
                                onOpenMail = { id -> openMail(backStack, id) },
                                onCompose = { backStack.add(ComposeRoute()) },
                                onOpenOverlay = { overlay.open(scope) },
                                onOverlayDrag = { overlay.dragBy(it, panelWidthPx) },
                                onOverlayRelease = { overlay.release(scope) },
                            )
                        }
                        entry<MailDetailRoute>(metadata = TwoPaneStrategy.secondaryPane()) { route ->
                            MailDetailPane(
                                detail = mailbox.detail(route.mailId),
                                onClose = { backStack.removeAll { it is MailDetailRoute } },
                                onOriginalLayout = { mailbox.onIntent(MailboxIntent.ShowMessage(originalPending)) },
                                onEdit = { mailbox.onIntent(MailboxIntent.ShowMessage(editPending)) },
                                onDelete = {
                                    mailbox.onIntent(MailboxIntent.DeleteMail(route.mailId, deletedMessage))
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
                            ComposePlaceholder(
                                replySubject = route.mailId?.let(mailbox::subjectOf),
                                onBack = { backStack.removeLastOrNull() },
                            )
                        }
                    },
                )
                AccountOverlay(
                    progress = overlay::progress,
                    panelWidth = panelWidth,
                    panelWidthPx = panelWidthPx,
                    accounts = state.accounts,
                    selectedAccountId = state.accountId,
                    currentName = state.selectedAccountName ?: allAccounts,
                    folder = state.folder,
                    onSelectAccount = {
                        mailbox.onIntent(MailboxIntent.SelectAccount(it))
                        overlay.close(scope)
                    },
                    onSelectFolder = {
                        mailbox.onIntent(MailboxIntent.SelectFolder(it))
                        overlay.close(scope)
                    },
                    onAdd = { addDialog = true },
                    onSettings = {
                        overlay.close(scope)
                        if (backStack.none { it is SettingsHomeRoute }) backStack.add(SettingsHomeRoute)
                    },
                    onClose = { overlay.close(scope) },
                    onDrag = { overlay.dragBy(it, panelWidthPx) },
                    onDragFinish = { overlay.release(scope) },
                )
            }
        }
    }

    if (addDialog) {
        AddAccountDialog(
            onDismiss = { addDialog = false },
            onLogin = {
                addDialog = false
                mailbox.onIntent(MailboxIntent.ShowMessage(loginPending))
            },
            onGroup = {
                addDialog = false
                mailbox.onIntent(MailboxIntent.ShowMessage(groupPending))
            },
        )
    }
}

private fun openSettingsSection(
    backStack: androidx.navigation3.runtime.NavBackStack<NavKey>,
    section: SettingsSection,
) {
    val current = backStack.lastOrNull()
    if (current is SettingsSectionRoute && current.section == section) return
    backStack.removeAll { it is SettingsSectionRoute }
    backStack.add(SettingsSectionRoute(section))
}

private fun openMail(backStack: androidx.navigation3.runtime.NavBackStack<NavKey>, id: String) {
    val current = backStack.lastOrNull()
    if (current is MailDetailRoute && current.mailId == id) return
    backStack.removeAll { it is MailDetailRoute }
    backStack.add(MailDetailRoute(id))
}
