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

/** 登录新邮箱。新建聚合仍留在外层。 */
@Composable
internal fun AccountLoginLayer(
    login: AccountLoginViewModel,
    snackbarHostState: SnackbarHostState,
    showProviders: Boolean,
    showQq: Boolean,
    onDismissProviders: () -> Unit,
    onOpenQq: () -> Unit,
    onDismissQq: () -> Unit,
) {
    val activity = LocalContext.current as Activity
    val authorizationLauncher = rememberLauncherForActivityResult(StartIntentSenderForResult()) { result ->
        login.onAuthorizationResult(activity, result)
    }
    val savedMessage = stringResource(R.string.account_saved)
    val failureMessage = login.state.failure?.let { loginFailureText(it) }
    LaunchedEffect(showProviders) {
        if (showProviders) login.refreshGmail()
    }
    LaunchedEffect(login.state.authorizationRequest) {
        val request = login.state.authorizationRequest ?: return@LaunchedEffect
        login.onAuthorizationLaunched()
        authorizationLauncher.launch(request)
    }
    LaunchedEffect(login.state.savedTick) {
        if (login.state.savedTick == 0L) return@LaunchedEffect
        snackbarHostState.showSnackbar(savedMessage)
    }
    LaunchedEffect(login.state.failureTick) {
        val message = failureMessage ?: return@LaunchedEffect
        if (showQq) return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        login.consumeFailure()
    }
    if (showProviders) {
        ProviderLoginDialog(
            gmailReady = login.state.gmailReady,
            onQq = onOpenQq,
            onGmail = {
                onDismissProviders()
                login.startGmail(activity)
            },
            onDismiss = onDismissProviders,
        )
    }
    if (showQq) {
        QqLoginDialog(
            saving = login.state.saving,
            failure = login.state.failure,
            onSubmit = login::addQq,
            onDismiss = onDismissQq,
        )
    }
    if (login.state.waitingForBrowser) {
        GmailWaitDialog(onCancel = login::cancelGmail)
    }
}
