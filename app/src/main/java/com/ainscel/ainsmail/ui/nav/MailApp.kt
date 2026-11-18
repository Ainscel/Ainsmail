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
import androidx.compose.runtime.DisposableEffect
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.rememberNavBackStack
import com.ainscel.ainsmail.R
import com.ainscel.ainsmail.data.SampleAccounts
import com.ainscel.ainsmail.data.mail.MailFirstSync
import com.ainscel.ainsmail.data.mail.MailLiveSync
import com.ainscel.ainsmail.data.mail.MailLocalStore
import com.ainscel.ainsmail.data.mail.MailSyncGate
import com.ainscel.ainsmail.data.mail.scheduleInboxSync
import com.ainscel.ainsmail.data.sampleCatalog
import com.ainscel.ainsmail.data.sampleMails
import com.ainscel.ainsmail.model.MailboxScope
import com.ainscel.ainsmail.model.Provider
import com.ainscel.ainsmail.security.AccountBook
import com.ainscel.ainsmail.security.SecretStore
import com.ainscel.ainsmail.ui.account.AccountLoginLayer
import com.ainscel.ainsmail.ui.account.AccountLoginViewModel
import com.ainscel.ainsmail.data.mail.FirstSyncOutcome
import com.ainscel.ainsmail.data.prefs.DisplayPreferences
import com.ainscel.ainsmail.ui.mailbox.InitialSyncResult
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
    val display = remember { DisplayPreferences(appContext) }
    val mailStore = remember { MailLocalStore(appContext, Dispatchers.IO) }
    val firstSync = remember(book, mailStore) { MailFirstSync(book, mailStore, Dispatchers.IO) }
    val liveSync = remember(book, mailStore) { MailLiveSync(book, mailStore, Dispatchers.IO) }
    val mailboxFactory = remember(book, mailStore, firstSync, liveSync, display) {
        MailboxViewModel.factory(
            accounts = SampleAccounts,
            mails = sampleMails(System.currentTimeMillis()),
            catalog = sampleCatalog(),
            book = book,
            observeMails = mailStore::observeMails,
            syncAccounts = { ids -> initialSync(mailStore, firstSync, ids) },
            watchScope = { ids, section, onHint ->
                liveSync.watch(ids, section) { outcome ->
                    outcome.toHint()?.let(onHint)
                }
            },
            pushSeen = liveSync::pushSeen,
            persistMails = mailStore::persistLocalMails,
            forgetAccounts = firstSync::forgetAccounts,
            display = display,
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
    var scrolling by remember { mutableStateOf(false) }
    var resizing by remember { mutableStateOf(false) }
    val processLifecycle = remember { ProcessLifecycleOwner.get().lifecycle }

    DisposableEffect(processLifecycle, mailbox) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    MailSyncGate.foreground = true
                    mailbox.setForeground(true)
                }
                Lifecycle.Event.ON_STOP -> {
                    MailSyncGate.foreground = false
                    mailbox.setForeground(false)
                }
                else -> Unit
            }
        }
        processLifecycle.addObserver(observer)
        if (processLifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            MailSyncGate.foreground = true
            mailbox.setForeground(true)
        }
        onDispose {
            processLifecycle.removeObserver(observer)
            MailSyncGate.foreground = false
            mailbox.setForeground(false)
        }
    }
    LaunchedEffect(mailbox) {
        appContext.onlineNetwork().collect { mailbox.setOnline(it) }
    }
    LaunchedEffect(Unit) { scheduleInboxSync(appContext) }
    LaunchedEffect(scrolling, resizing, overlay.animating) {
        mailbox.setSnapshotPaused(scrolling || resizing || overlay.animating)
    }

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
            val strategy = rememberTwoPaneStrategy(wide, splitFraction) { resizing = it }
            val panelWidth = minOf(300.dp, maxWidth * 0.88f)
            val panelWidthPx = with(LocalDensity.current) { panelWidth.toPx() }
            Box(Modifier.fillMaxSize()) {
                MailNavHost(
                    mailbox = mailbox,
                    book = book,
                    secrets = secrets,
                    display = display,
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
                    onScrollActive = { scrolling = it },
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

private suspend fun initialSync(
    mailStore: MailLocalStore,
    firstSync: MailFirstSync,
    ids: List<String>,
): InitialSyncResult {
    val need = ids.filter { mailStore.needsInitialSync(it) }
    if (need.isEmpty()) return InitialSyncResult(hints = emptyList(), readyIds = ids)
    val hints = firstSync.syncAll(need).mapNotNull { it.toHint() }
    val failed = hints.map { it.accountId }.toSet()
    return InitialSyncResult(
        hints = hints,
        readyIds = ids.filter { it !in failed },
    )
}

private fun FirstSyncOutcome.toHint(): MailboxSyncHint? = when (this) {
    FirstSyncOutcome.Ok -> null
    is FirstSyncOutcome.NeedsReauth -> MailboxSyncHint.NeedsReauth(accountId)
    is FirstSyncOutcome.Failed -> MailboxSyncHint.Failed(accountId)
}
