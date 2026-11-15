package com.ainscel.ainsmail.ui.account

import android.app.Activity
import android.content.Context
import android.net.Uri
import androidx.activity.result.ActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ainscel.ainsmail.security.AccountBook
import com.ainscel.ainsmail.security.AccountWrite
import com.ainscel.ainsmail.security.GoogleAndroidLogin
import com.ainscel.ainsmail.security.GoogleSignIn
import com.ainscel.ainsmail.security.GoogleSignInFailure
import com.ainscel.ainsmail.security.LoginLog
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Immutable
data class LoginUiState(
    val gmailReady: Boolean = false,
    val saving: Boolean = false,
    val waitingForGoogle: Boolean = false,
    val authorizationRequest: IntentSenderRequest? = null,
    val failure: LoginFailure? = null,
    val failureTick: Long = 0,
    val savedTick: Long = 0,
)

enum class LoginFailure {
    NotConfigured,
    TimedOut,
    Denied,
    Rejected,
    Duplicate,
    InvalidAddress,
    BlankCode,
    BlankName,
    Unreadable,
    BadImage,
}

class AccountLoginViewModel(
    private val appContext: Context,
    private val book: AccountBook,
    private val login: GoogleAndroidLogin,
    private val io: CoroutineDispatcher,
) : ViewModel() {
    var state by mutableStateOf(LoginUiState())
        private set

    private var gmailJob: Job? = null
    private var gmailInFlight: Boolean = false

    fun refreshGmail() {
        viewModelScope.launch {
            val ready = withContext(io) { book.gmailReady() }
            state = state.copy(gmailReady = ready)
        }
    }

    fun addQq(address: String, displayName: String, authCode: String, avatar: Uri?) {
        if (state.saving) return
        state = state.copy(saving = true, failure = null)
        viewModelScope.launch {
            val written = withContext(io) {
                val bytes = avatar?.let { appContext.readImage(it) }
                if (avatar != null && bytes == null) {
                    AccountWrite.BadImage
                } else {
                    book.addQq(address, displayName, authCode, bytes)
                }
            }
            state = if (written is AccountWrite.Saved) {
                state.copy(saving = false, failure = null, savedTick = state.savedTick + 1)
            } else {
                val reason = written.toFailure()
                LoginLog.w("qq_save", "failed reason=$reason write=$written")
                state.failed(reason)
            }
        }
    }

    fun startGmail(activity: Activity) {
        if (state.waitingForGoogle || state.saving || state.authorizationRequest != null) return
        gmailJob = viewModelScope.launch {
            val ready = withContext(io) { book.gmailReady() }
            if (!ready) {
                LoginLog.w("start_gmail", "client_not_configured")
                state = state.failed(LoginFailure.NotConfigured)
                return@launch
            }
            gmailInFlight = true
            state = state.copy(waitingForGoogle = true, failure = null, authorizationRequest = null)
            try {
                val result = login.begin(activity)
                if (result.hasResolution()) {
                    val pending = result.pendingIntent
                    if (pending == null) {
                        LoginLog.w("start_gmail", "hasResolution but pendingIntent null")
                        fail(LoginFailure.Rejected)
                        return@launch
                    }
                    state = state.copy(
                        authorizationRequest = IntentSenderRequest.Builder(pending).build(),
                    )
                } else {
                    finishGmail(result)
                }
            } catch (api: ApiException) {
                failApi("start_gmail_api", api)
            } catch (error: Exception) {
                LoginLog.e("start_gmail", "${error.javaClass.simpleName}: ${error.message}", error)
                fail(LoginFailure.Rejected)
            }
        }
    }

    fun onAuthorizationLaunched() {
        state = state.copy(authorizationRequest = null)
    }

    fun onAuthorizationResult(activity: Activity, activityResult: ActivityResult) {
        if (!gmailInFlight) {
            LoginLog.w("auth_result", "no in-flight login resultCode=${activityResult.resultCode}")
            fail(LoginFailure.Rejected)
            return
        }
        gmailJob = viewModelScope.launch {
            try {
                if (activityResult.resultCode != Activity.RESULT_OK) {
                    LoginLog.w(
                        "auth_result",
                        "non_ok resultCode=${activityResult.resultCode} hasData=${activityResult.data != null}",
                    )
                    if (activityResult.data == null) {
                        fail(LoginFailure.Denied)
                        return@launch
                    }
                }
                val result = login.resultFromIntent(activity, activityResult.data)
                finishGmail(result)
            } catch (api: ApiException) {
                failApi("auth_result_api", api)
            } catch (error: Exception) {
                LoginLog.e("auth_result", "${error.javaClass.simpleName}: ${error.message}", error)
                fail(LoginFailure.Rejected)
            }
        }
    }

    fun cancelGmail() {
        gmailJob?.cancel()
        gmailInFlight = false
        state = state.copy(waitingForGoogle = false, authorizationRequest = null)
    }

    fun consumeFailure() {
        state = state.copy(failure = null)
    }

    private suspend fun finishGmail(result: AuthorizationResult) {
        when (val signed = login.complete(result)) {
            is GoogleSignIn.Failed -> {
                val reason = signed.reason.toFailure()
                LoginLog.w("finish_gmail", "sign_in_failed google=${signed.reason} -> $reason")
                fail(reason)
            }
            is GoogleSignIn.Ok -> saveGmail(signed)
        }
    }

    private suspend fun saveGmail(signed: GoogleSignIn.Ok) {
        val written = withContext(io) {
            book.addGmail(
                address = signed.email,
                displayName = signed.name,
                accessToken = signed.accessToken,
                expiresAtEpochMs = signed.expiresAtEpochMs,
                avatar = signed.picture,
            )
        }
        gmailInFlight = false
        state = if (written is AccountWrite.Saved) {
            state.copy(
                waitingForGoogle = false,
                authorizationRequest = null,
                failure = null,
                savedTick = state.savedTick + 1,
            )
        } else {
            val mapped = written.toFailure()
            val reason = if (mapped == LoginFailure.BlankCode) LoginFailure.Rejected else mapped
            LoginLog.w("gmail_save", "failed write=$written -> $reason")
            state.failed(reason)
        }
    }

    private fun failApi(step: String, api: ApiException) {
        val reason = api.toLoginFailure()
        LoginLog.e(
            step,
            "status=${api.statusCode} message=${api.status.statusMessage} -> $reason",
            api,
        )
        fail(reason)
    }

    private fun fail(reason: LoginFailure) {
        gmailInFlight = false
        state = state.failed(reason)
    }

    companion object {
        fun factory(context: Context, book: AccountBook): ViewModelProvider.Factory {
            val appContext = context.applicationContext
            val login = GoogleAndroidLogin(Dispatchers.IO)
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return AccountLoginViewModel(appContext, book, login, Dispatchers.IO) as T
                }
            }
        }
    }
}

private fun LoginUiState.failed(reason: LoginFailure): LoginUiState = copy(
    saving = false,
    waitingForGoogle = false,
    authorizationRequest = null,
    failure = reason,
    failureTick = failureTick + 1,
)

internal fun AccountWrite.toFailure(): LoginFailure = when (this) {
    AccountWrite.Saved -> LoginFailure.Rejected
    AccountWrite.Duplicate -> LoginFailure.Duplicate
    AccountWrite.InvalidAddress -> LoginFailure.InvalidAddress
    AccountWrite.BlankCode -> LoginFailure.BlankCode
    AccountWrite.BlankName -> LoginFailure.BlankName
    AccountWrite.Unreadable -> LoginFailure.Unreadable
    AccountWrite.BadImage -> LoginFailure.BadImage
}

private fun GoogleSignInFailure.toFailure(): LoginFailure = when (this) {
    GoogleSignInFailure.TimedOut -> LoginFailure.TimedOut
    GoogleSignInFailure.Denied -> LoginFailure.Denied
    GoogleSignInFailure.Rejected -> LoginFailure.Rejected
}

private fun ApiException.toLoginFailure(): LoginFailure = when (statusCode) {
    CommonStatusCodes.CANCELED -> LoginFailure.Denied
    CommonStatusCodes.TIMEOUT -> LoginFailure.TimedOut
    else -> LoginFailure.Rejected
}
