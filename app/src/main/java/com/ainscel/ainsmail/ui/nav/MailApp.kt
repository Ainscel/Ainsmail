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
import androidx.navigation3.runtime.rememberNavBackStack
import com.ainscel.ainsmail.R
import com.ainscel.ainsmail.data.SampleAccounts
import com.ainscel.ainsmail.data.sampleCatalog
import com.ainscel.ainsmail.data.sampleMails
import com.ainscel.ainsmail.model.MailboxScope
import com.ainscel.ainsmail.ui.mailbox.MailboxIntent
import com.ainscel.ainsmail.ui.mailbox.MailboxViewModel
import com.ainscel.ainsmail.ui.shell.AccountOverlay
import com.ainscel.ainsmail.ui.shell.OverlayController
import com.ainscel.ainsmail.ui.shell.isWideWindow

@Composable
fun MailApp(modifier: Modifier = Modifier) {
    val mailboxFactory = remember {
        MailboxViewModel.factory(
            accounts = SampleAccounts,
            mails = sampleMails(System.currentTimeMillis()),
            catalog = sampleCatalog(),
        )
    }
    val mailbox = viewModel<MailboxViewModel>(factory = mailboxFactory)
    val state = mailbox.state
    val backStack = rememberNavBackStack(MailListRoute)
    val overlay = remember { OverlayController() }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
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
        onUndo = { mailbox.perform(MailboxIntent.Undo) },
        onConsumed = { mailbox.perform(MailboxIntent.ConsumeNotice(it)) },
    )
    LaunchedEffect(state.sectionMailIds) {
        val openId = backStack.filterIsInstance<MailDetailRoute>().lastOrNull()?.mailId ?: return@LaunchedEffect
        if (openId !in state.sectionMailIds) backStack.removeAll { it is MailDetailRoute }
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
                MailNavHost(
                    mailbox = mailbox,
                    backStack = backStack,
                    overlay = overlay,
                    scope = scope,
                    strategy = strategy,
                    panelWidthPx = panelWidthPx,
                    onExit = { (context as? Activity)?.finish() },
                )
                AccountOverlay(
                    progress = overlay::progress,
                    panelWidth = panelWidth,
                    panelWidthPx = panelWidthPx,
                    accounts = state.accounts,
                    selectedAccountId = (state.scope as? MailboxScope.Account)?.id,
                    currentName = state.selectedAccountName ?: allAccounts,
                    section = state.section,
                    onSelectAccount = { id ->
                        mailbox.perform(
                            MailboxIntent.SelectScope(
                                if (id == null) MailboxScope.All else MailboxScope.Account(id),
                            ),
                        )
                        overlay.close(scope)
                    },
                    onSelectSection = {
                        mailbox.perform(MailboxIntent.SelectSection(it))
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
                mailbox.perform(MailboxIntent.ShowLoginPending)
            },
            onGroup = {
                addDialog = false
                mailbox.perform(MailboxIntent.ShowGroupPending)
            },
        )
    }
}
