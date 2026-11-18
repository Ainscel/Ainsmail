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
import androidx.compose.runtime.getValue
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
import com.ainscel.ainsmail.data.mail.MailFirstSync
import com.ainscel.ainsmail.data.mail.MailLocalStore
import com.ainscel.ainsmail.data.sampleCatalog
import com.ainscel.ainsmail.data.sampleMails
import com.ainscel.ainsmail.model.MailboxScope
import com.ainscel.ainsmail.model.Provider
import com.ainscel.ainsmail.security.AccountBook
import com.ainscel.ainsmail.security.SecretStore
import com.ainscel.ainsmail.ui.account.AccountLoginLayer
import com.ainscel.ainsmail.ui.account.AccountLoginViewModel
import com.ainscel.ainsmail.data.mail.FirstSyncOutcome
import com.ainscel.ainsmail.ui.mailbox.MailboxIntent
import com.ainscel.ainsmail.ui.mailbox.MailboxSyncHint
import com.ainscel.ainsmail.ui.mailbox.MailboxViewModel
import com.ainscel.ainsmail.ui.shell.AccountOverlay
import com.ainscel.ainsmail.ui.shell.OverlayController
import com.ainscel.ainsmail.ui.shell.isWideWindow
import com.ainscel.ainsmail.ui.shell.rememberSplitFraction
import kotlinx.coroutines.Dispatchers

@Composable
fun MailApp(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val secrets = remember { SecretStore(appContext) }
    val book = remember { AccountBook(appContext, secrets) }
    val mailStore = remember { MailLocalStore(appContext, Dispatchers.IO) }
    val firstSync = remember(book, mailStore) { MailFirstSync(book, mailStore, Dispatchers.IO) }
    val mailboxFactory = remember(book, mailStore, firstSync) {
        MailboxViewModel.factory(
            accounts = SampleAccounts,
            mails = sampleMails(System.currentTimeMillis()),
            catalog = sampleCatalog(),
            book = book,
            observeMails = mailStore::observeMails,
            syncAccounts = { ids ->
                firstSync.syncAll(ids).mapNotNull { outcome ->
                    when (outcome) {
                        FirstSyncOutcome.Ok -> null
                        is FirstSyncOutcome.NeedsReauth -> MailboxSyncHint.NeedsReauth(outcome.accountId)
                        is FirstSyncOutcome.Failed -> MailboxSyncHint.Failed
                    }
                }
            },
            persistMails = mailStore::persistLocalMails,
            io = Dispatchers.IO,
        )
    }
    val mailbox = viewModel<MailboxViewModel>(factory = mailboxFactory)
    val login = viewModel<AccountLoginViewModel>(factory = AccountLoginViewModel.factory(context, book))
    val chrome = mailbox.chrome
    val backStack = rememberNavBackStack(MailListRoute)
    val overlay = remember { OverlayController() }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val allAccounts = stringResource(R.string.account_all)
    var addDialog by rememberSaveable { mutableStateOf(false) }
    var unavailable by remember { mutableStateOf<UnavailableHint?>(null) }
    val splitFraction = rememberSplitFraction()

    MailboxNoticeEffect(
        notice = chrome.notice,
        hostState = snackbarHostState,
        onUndo = { mailbox.perform(MailboxIntent.Undo) },
        onConsumed = { mailbox.perform(MailboxIntent.ConsumeNotice(it)) },
    )
    UnavailableHintEffect(
        hint = unavailable,
        hostState = snackbarHostState,
        onConsumed = { unavailable = null },
    )
    LaunchedEffect(login.saved.tick) {
        val saved = login.saved
        if (saved.tick == 0L) return@LaunchedEffect
        val id = saved.accountId ?: return@LaunchedEffect
        mailbox.perform(MailboxIntent.ClearReauth(id))
    }
    LaunchedEffect(chrome.sectionMailIds) {
        val openId = backStack.filterIsInstance<MailDetailRoute>().lastOrNull()?.mailId ?: return@LaunchedEffect
        if (openId !in chrome.sectionMailIds) backStack.removeAll { it is MailDetailRoute }
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
                    book = book,
                    secrets = secrets,
                    onRelogin = { account ->
                        when (account.provider) {
                            Provider.Gmail -> (context as? Activity)?.let(login::startGmail)
                            Provider.Qq -> login.openQq(account.address, account.name)
                        }
                    },
                    backStack = backStack,
                    overlay = overlay,
                    scope = scope,
                    strategy = strategy,
                    onUnavailable = { kind ->
                        unavailable = UnavailableHint(kind, System.nanoTime())
                    },
                    onExit = { (context as? Activity)?.finish() },
                )
                AccountOverlay(
                    progress = overlay::progress,
                    panelWidth = panelWidth,
                    panelWidthPx = panelWidthPx,
                    accounts = chrome.accounts,
                    selectedAccountId = (chrome.scope as? MailboxScope.Account)?.id,
                    currentName = chrome.selectedAccountName ?: allAccounts,
                    section = chrome.section,
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
                )
            }
        }
    }

    if (addDialog) {
        AddAccountDialog(
            onDismiss = { addDialog = false },
            onLogin = {
                addDialog = false
                login.openProviders()
            },
            onGroup = {
                addDialog = false
                unavailable = UnavailableHint(UnavailableKind.Group, System.nanoTime())
            },
        )
    }
    AccountLoginLayer(
        login = login,
        snackbarHostState = snackbarHostState,
    )
}
