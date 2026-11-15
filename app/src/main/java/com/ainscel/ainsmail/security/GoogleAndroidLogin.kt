package com.ainscel.ainsmail.security

import android.app.Activity
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

sealed interface GoogleSignIn {
    class Ok(
        val email: String,
        val name: String,
        val accessToken: String,
        val expiresAtEpochMs: Long,
        val picture: ByteArray?,
    ) : GoogleSignIn {
        override fun toString(): String = "GoogleSignIn.Ok"
    }

    data class Failed(val reason: GoogleSignInFailure) : GoogleSignIn
}

enum class GoogleSignInFailure {
    TimedOut,
    Denied,
    Rejected,
}

/**
 * 仅用 Android 客户端 + Play [AuthorizationClient]。
 * 不申请 offline access，不换刷新令牌；访问令牌过期后再静默 authorize。
 * Android 客户端 ID 只在 Cloud Console 登记包名与 SHA-1，不传入授权请求。
 */
class GoogleAndroidLogin(
    private val io: CoroutineDispatcher,
) {
    fun authorizationRequest(): AuthorizationRequest =
        AuthorizationRequest.builder()
            .setRequestedScopes(
                listOf(
                    Scope("openid"),
                    Scope("email"),
                    Scope("profile"),
                    Scope(GMAIL_MAIL_SCOPE),
                ),
            )
            .build()

    suspend fun begin(activity: Activity): AuthorizationResult =
        Identity.getAuthorizationClient(activity).authorize(authorizationRequest()).await()

    fun resultFromIntent(activity: Activity, data: Intent?): AuthorizationResult =
        Identity.getAuthorizationClient(activity).getAuthorizationResultFromIntent(data)

    suspend fun complete(result: AuthorizationResult): GoogleSignIn = withContext(io) {
        try {
            val granted = result.grantedScopes.map { it.toString() }
            if (granted.none { it.contains("mail.google.com") }) {
                LoginLog.w("gmail_scope", "mail.google.com missing granted=$granted")
                return@withContext GoogleSignIn.Failed(GoogleSignInFailure.Denied)
            }
            val accessToken = result.accessToken?.trim().orEmpty()
            if (accessToken.isEmpty()) {
                LoginLog.w("access_token", "empty after authorize")
                return@withContext GoogleSignIn.Failed(GoogleSignInFailure.Rejected)
            }
            val profileRaw = try {
                getAuthorized(GOOGLE_USERINFO, accessToken)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                LoginLog.e(
                    "userinfo",
                    "${error.javaClass.simpleName}: ${error.message}",
                    error,
                )
                return@withContext GoogleSignIn.Failed(GoogleSignInFailure.Rejected)
            }
            val profile = parseGoogleProfile(profileRaw)
            if (profile == null) {
                LoginLog.w("userinfo", "profile parse failed length=${profileRaw.length}")
                return@withContext GoogleSignIn.Failed(GoogleSignInFailure.Rejected)
            }
            val picture = profile.pictureUrl?.let { download(it) }
            if (profile.pictureUrl != null && picture == null) {
                LoginLog.w("avatar", "download skipped or failed")
            }
            GoogleSignIn.Ok(
                email = profile.email,
                name = profile.name,
                accessToken = accessToken,
                expiresAtEpochMs = System.currentTimeMillis() + GOOGLE_ACCESS_TOKEN_TTL_MS,
                picture = picture,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (api: ApiException) {
            val reason = api.toFailure()
            LoginLog.e(
                "complete_api",
                "status=${api.statusCode} message=${api.status.statusMessage} -> $reason",
                api,
            )
            GoogleSignIn.Failed(reason)
        } catch (error: Exception) {
            LoginLog.e("complete", "${error.javaClass.simpleName}: ${error.message}", error)
            GoogleSignIn.Failed(GoogleSignInFailure.Rejected)
        }
    }

    private fun getAuthorized(url: String, accessToken: String): String {
        val connection = open(url, "GET")
        try {
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
            return readText(connection, "userinfo_http") ?: throw AuthHttp()
        } finally {
            connection.disconnect()
        }
    }

    private fun download(url: String): ByteArray? {
        if (!url.startsWith("https://")) return null
        val connection = open(url, "GET")
        return try {
            if (connection.responseCode !in 200..299) {
                LoginLog.w("avatar_http", "status=${connection.responseCode}")
                return null
            }
            connection.inputStream?.use { readCap(it, MAX_PICTURE) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            LoginLog.e("avatar_http", "${error.javaClass.simpleName}: ${error.message}", error)
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun open(url: String, method: String): HttpURLConnection {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.instanceFollowRedirects = false
        connection.connectTimeout = HTTP_MILLIS
        connection.readTimeout = HTTP_MILLIS
        return connection
    }

    private fun readText(connection: HttpURLConnection, step: String): String? {
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val bytes = stream?.use { readCap(it, MAX_TEXT) }
        if (bytes == null) {
            LoginLog.w(step, "status=$code empty_body")
            return null
        }
        val text = bytes.toString(Charsets.UTF_8)
        if (code !in 200..299) {
            LoginLog.w(step, "status=$code bodyLength=${text.length}")
            return null
        }
        return text
    }

    private fun readCap(input: InputStream, max: Int): ByteArray? {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            if (total > max) return null
            out.write(buffer, 0, count)
        }
        return out.toByteArray()
    }

    private companion object {
        const val HTTP_MILLIS = 20_000
        const val MAX_TEXT = 256 * 1024
        const val MAX_PICTURE = 2 * 1024 * 1024
    }
}

private class AuthHttp : Exception()

private fun ApiException.toFailure(): GoogleSignInFailure = when (statusCode) {
    CommonStatusCodes.CANCELED -> GoogleSignInFailure.Denied
    CommonStatusCodes.TIMEOUT -> GoogleSignInFailure.TimedOut
    else -> GoogleSignInFailure.Rejected
}

/** 把 Google Task 挂到协程上，取消时尽量摘掉监听。 */
suspend fun <T> com.google.android.gms.tasks.Task<T>.await(): T =
    suspendCancellableCoroutine { cont ->
        addOnSuccessListener { value -> cont.resume(value) }
        addOnFailureListener { error -> cont.resumeWithException(error) }
        addOnCanceledListener {
            cont.resumeWithException(CancellationException("google task cancelled"))
        }
    }
