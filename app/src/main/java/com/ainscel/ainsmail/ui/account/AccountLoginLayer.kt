package com.ainscel.ainsmail.ui.account

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.ainscel.ainsmail.R
import com.ainscel.ainsmail.ui.accountErrorText

/** 登录新邮箱。新建聚合仍留在外层。对话框步骤由 [AccountLoginViewModel] 持有。 */
@Composable
internal fun AccountLoginLayer(
    login: AccountLoginViewModel,
    snackbarHostState: SnackbarHostState,
) {
    val activity = LocalContext.current as Activity
    val authorizationLauncher = rememberLauncherForActivityResult(StartIntentSenderForResult()) { result ->
        login.onAuthorizationResult(activity, result)
    }
    val savedMessage = stringResource(R.string.account_saved)
    val failureMessage = login.state.failure?.let { accountErrorText(it) }
    val sheet = login.state.sheet
    LaunchedEffect(login.state.authorizationRequest) {
        val request = login.state.authorizationRequest ?: return@LaunchedEffect
        login.onAuthorizationLaunched()
        authorizationLauncher.launch(request)
    }
    LaunchedEffect(login.saved.tick) {
        val tick = login.saved.tick
        if (!login.claimSavedTick(tick)) return@LaunchedEffect
        snackbarHostState.showSnackbar(savedMessage)
    }
    LaunchedEffect(login.state.failureTick) {
        val message = failureMessage ?: return@LaunchedEffect
        if (sheet == LoginSheet.Qq) return@LaunchedEffect
        if (!login.claimFailureTick(login.state.failureTick)) return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        login.consumeFailure()
    }
    if (sheet == LoginSheet.Providers) {
        ProviderLoginDialog(
            gmailReady = login.state.gmailReady,
            onQq = { login.openQq() },
            onGmail = { login.startGmail(activity) },
            onDismiss = login::dismissSheet,
        )
    }
    if (sheet == LoginSheet.Qq) {
        QqLoginDialog(
            initialAddress = login.state.qqAddress,
            initialName = login.state.qqName,
            saving = login.state.saving,
            failure = login.state.failure,
            onSubmit = login::addQq,
            onDismiss = login::dismissSheet,
        )
    }
    if (login.state.waitingForGoogle) {
        GmailWaitDialog(onCancel = login::cancelGmail)
    }
}
