package com.ainscel.ainsmail.security

import android.app.Activity
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.android.gms.common.api.CommonStatusCodes
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
        val refreshToken: String,
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
 * Android 客户端通过 Play 授权回到应用，不再走本机回环。
 * 需要刷新令牌时请求 offline access，再用 server auth code 换令牌。
 */
class GoogleAndroidLogin(
    private val io: CoroutineDispatcher,
) {
    fun authorizationRequest(client: GoogleClient): AuthorizationRequest =
        AuthorizationRequest.builder()
            .setRequestedScopes(
                listOf(
                    Scope("openid"),
                    Scope("email"),
                    Scope("profile"),
                    Scope(GMAIL_MAIL_SCOPE),
                ),
            )
            .requestOfflineAccess(client.id, true)
            .build()

    suspend fun begin(activity: Activity, client: GoogleClient): AuthorizationResult {
        val request = authorizationRequest(client)
        return Identity.getAuthorizationClient(activity).authorize(request).await()
    }

    fun resultFromIntent(activity: Activity, data: Intent?): AuthorizationResult =
        Identity.getAuthorizationClient(activity).getAuthorizationResultFromIntent(data)

    suspend fun complete(
        client: GoogleClient,
        result: AuthorizationResult,
    ): GoogleSignIn = withContext(io) {
        try {
            if (result.grantedScopes.none { it.toString().contains("mail.google.com") }) {
                return@withContext GoogleSignIn.Failed(GoogleSignInFailure.Denied)
            }
            val code = result.serverAuthCode?.trim().orEmpty()
            val access = result.accessToken?.trim().orEmpty()
            val tokens = when {
                code.isNotEmpty() -> exchangeServerAuth(client, code)
                else -> null
            }
            val accessToken = tokens?.accessToken?.ifBlank { null } ?: access.ifBlank { null }
                ?: return@withContext GoogleSignIn.Failed(GoogleSignInFailure.Rejected)
            val refreshToken = tokens?.refreshToken?.trim().orEmpty()
            if (refreshToken.isEmpty()) {
                return@withContext GoogleSignIn.Failed(GoogleSignInFailure.Rejected)
            }
            val profile = parseGoogleProfile(getAuthorized(GOOGLE_USERINFO, accessToken))
                ?: return@withContext GoogleSignIn.Failed(GoogleSignInFailure.Rejected)
            val picture = profile.pictureUrl?.let { download(it) }
            GoogleSignIn.Ok(profile.email, profile.name, refreshToken, picture)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (api: ApiException) {
            GoogleSignIn.Failed(api.toFailure())
        } catch (_: Exception) {
            GoogleSignIn.Failed(GoogleSignInFailure.Rejected)
        }
    }

    private fun exchangeServerAuth(client: GoogleClient, code: String): IssuedTokens? {
        val body = serverAuthTokenForm(client.id, client.secret, code)
        val raw = postForm(GOOGLE_TOKEN, body) ?: return null
        return parseIssuedTokens(raw)
    }

    private fun postForm(url: String, body: String): String? {
        val connection = open(url, "POST")
        return try {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            readText(connection)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun getAuthorized(url: String, accessToken: String): String {
        val connection = open(url, "GET")
        try {
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
            return readText(connection) ?: throw AuthHttp()
        } finally {
            connection.disconnect()
        }
    }

    private fun download(url: String): ByteArray? {
        if (!url.startsWith("https://")) return null
        val connection = open(url, "GET")
        return try {
            if (connection.responseCode !in 200..299) return null
            connection.inputStream?.use { readCap(it, MAX_PICTURE) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
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

    private fun readText(connection: HttpURLConnection): String? {
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val bytes = stream?.use { readCap(it, MAX_TEXT) } ?: return null
        if (code !in 200..299) return null
        return bytes.toString(Charsets.UTF_8)
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
