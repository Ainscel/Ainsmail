package com.ainscel.mymail.ui.nav

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.ainscel.mymail.R
import com.ainscel.mymail.security.SecretStore
import com.ainscel.mymail.ui.detail.ComposePlaceholder
import com.ainscel.mymail.ui.detail.MailDetailPane
import com.ainscel.mymail.ui.list.MailListPane
import com.ainscel.mymail.ui.mailbox.MailboxViewModel
import com.ainscel.mymail.ui.settings.SettingsHomePane
import com.ainscel.mymail.ui.settings.SettingsSection
import com.ainscel.mymail.ui.settings.SettingsSectionPane
import com.ainscel.mymail.ui.shell.AccountOverlay
import com.ainscel.mymail.ui.shell.OverlayController
import com.ainscel.mymail.ui.shell.isWideWindow
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
fun MailApp(
    secretStore: SecretStore,
    modifier: Modifier = Modifier,
) {
    val mailbox = viewModel<MailboxViewModel>()
    val backStack = rememberNavBackStack(MailListRoute)
    val overlay = remember { OverlayController() }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val undoLabel = stringResource(R.string.undo)
    val originalPending = stringResource(R.string.detail_original_pending)
    val editPending = stringResource(R.string.detail_edit_pending)
    val deletedMessage = stringResource(R.string.list_deleted)
    val loginPending = stringResource(R.string.account_add_login_pending)
    val groupPending = stringResource(R.string.account_add_group_pending)
    val allAccounts = stringResource(R.string.account_all)
    var addDialog by rememberSaveable { mutableStateOf(false) }
    val splitFraction = rememberSaveable { mutableFloatStateOf(0.40f) }
    // 上一版默认是 34%，已经存下来的比例改到现在的 40%。左栏下限是 35%。
    if (splitFraction.floatValue == 0.34f) splitFraction.floatValue = 0.40f
    if (splitFraction.floatValue < 0.35f) splitFraction.floatValue = 0.35f
    val context = LocalContext.current

    LaunchedEffect(mailbox.accountId, mailbox.folder, mailbox.mails.toList()) {
        val openId = backStack.filterIsInstance<MailDetailRoute>().lastOrNull()?.mailId ?: return@LaunchedEffect
        val mail = mailbox.mails.find { it.id == openId }
        val inScope = mail != null &&
            (mailbox.accountId == null || mail.accountId == mailbox.accountId) &&
            mail.folder == mailbox.folder
        if (!inScope) backStack.removeAll { it is MailDetailRoute }
    }

    LaunchedEffect(mailbox.notice?.id) {
        val current = mailbox.notice ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = current.text,
            actionLabel = if (current.undo != null) undoLabel else null,
        )
        if (result == SnackbarResult.ActionPerformed) current.undo?.invoke()
        if (mailbox.notice?.id == current.id) mailbox.notice = null
    }

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
                        entry<MailListRoute>(metadata = TwoPaneStrategy.primaryPane()) {
                            MailListPane(
                                mailbox = mailbox,
                                openMailId = backStack.filterIsInstance<MailDetailRoute>().lastOrNull()?.mailId,
                                overlay = overlay,
                                overlayWidthPx = panelWidthPx,
                                onOpenMail = { id -> openMail(backStack, id) },
                                onCompose = { backStack.add(ComposeRoute()) },
                            )
                        }
                        entry<MailDetailRoute>(metadata = TwoPaneStrategy.secondaryPane()) { route ->
                            val mail = mailbox.mails.find { it.id == route.mailId }
                            MailDetailPane(
                                mail = mail,
                                accountName = mail?.let { mailbox.account(it.accountId)?.name }.orEmpty(),
                                onClose = { backStack.removeAll { it is MailDetailRoute } },
                                onOriginalLayout = { mailbox.message(originalPending) },
                                onEdit = { mailbox.message(editPending) },
                                onDelete = {
                                    mailbox.deleteMail(route.mailId, deletedMessage)
                                    backStack.removeAll { it is MailDetailRoute }
                                },
                            )
                        }
                        entry<SettingsHomeRoute>(metadata = TwoPaneStrategy.primaryPane()) {
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
                                store = secretStore,
                                onBack = { backStack.removeLastOrNull() },
                            )
                        }
                        entry<ComposeRoute> { route ->
                            val subject = route.mailId?.let { id ->
                                mailbox.mails.find { it.id == id }?.subject
                            }
                            ComposePlaceholder(
                                replySubject = subject,
                                onBack = { backStack.removeLastOrNull() },
                            )
                        }
                    },
                )
                AccountOverlay(
                    progress = overlay.progress,
                    panelWidth = panelWidth,
                    panelWidthPx = panelWidthPx,
                    accounts = mailbox.accounts,
                    selectedAccountId = mailbox.accountId,
                    currentName = mailbox.account(mailbox.accountId ?: "")?.name ?: allAccounts,
                    folder = mailbox.folder,
                    onSelectAccount = {
                        mailbox.selectAccount(it)
                        overlay.close(scope)
                    },
                    onSelectFolder = {
                        mailbox.selectFolder(it)
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

    BackHandler(enabled = overlay.progress > 0.02f) {
        overlay.close(scope)
    }

    if (addDialog) {
        AlertDialog(
            onDismissRequest = { addDialog = false },
            confirmButton = {
                TextButton(onClick = { addDialog = false }) {
                    Text(stringResource(R.string.list_cancel))
                }
            },
            title = { Text(stringResource(R.string.account_add)) },
            text = {
                androidx.compose.foundation.layout.Column {
                    TextButton(onClick = {
                        addDialog = false
                        mailbox.message(loginPending)
                    }) {
                        Text(stringResource(R.string.account_add_login))
                    }
                    TextButton(onClick = {
                        addDialog = false
                        mailbox.message(groupPending)
                    }) {
                        Text(stringResource(R.string.account_add_group))
                    }
                }
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
