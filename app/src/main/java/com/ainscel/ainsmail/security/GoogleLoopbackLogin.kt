package com.ainscel.ainsmail.security

import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.URL
import kotlin.concurrent.thread
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.isActive
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
 * 系统浏览器 + PKCE + 本机回环。
 * 刷新令牌交给调用方落盘，访问令牌只用这一次，失败信息里不带令牌。
 */
class GoogleLoopbackLogin(
    private val io: CoroutineDispatcher,
    private val main: CoroutineDispatcher,
) {
    suspend fun signIn(
        client: GoogleClient,
        openBrowser: (String) -> Unit,
    ): GoogleSignIn = withContext(io) {
        val server = ServerSocket()
        try {
            server.bind(InetSocketAddress(InetAddress.getByName(LOOPBACK), 0))
            val verifier = newAuthSecret()
            val state = newAuthSecret()
            val redirect = redirectUri(server.localPort)
            val url = authorizeUrl(client.id, redirect, state, verifier)
            withContext(main) { openBrowser(url) }
            server.soTimeout = WAIT_MILLIS
            when (val redirectResult = readRedirect(server, state)) {
                is AuthRedirect.Code -> withContext(io) {
                    exchange(client, redirect, redirectResult.value, verifier)
                }
                AuthRedirect.Denied -> GoogleSignIn.Failed(GoogleSignInFailure.Denied)
                AuthRedirect.Invalid -> GoogleSignIn.Failed(GoogleSignInFailure.Rejected)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: SocketTimeoutException) {
            GoogleSignIn.Failed(GoogleSignInFailure.TimedOut)
        } catch (_: SocketException) {
            if (!isActive) throw CancellationException("login cancelled")
            GoogleSignIn.Failed(GoogleSignInFailure.Rejected)
        } catch (_: AuthHttp) {
            GoogleSignIn.Failed(GoogleSignInFailure.Rejected)
        } catch (_: Exception) {
            GoogleSignIn.Failed(GoogleSignInFailure.Rejected)
        } finally {
            server.close()
        }
    }

    private fun exchange(
        client: GoogleClient,
        redirect: String,
        code: String,
        verifier: String,
    ): GoogleSignIn {
        val tokenBody = postForm(
            GOOGLE_TOKEN,
            tokenForm(client.id, client.secret, redirect, code, verifier),
        )
        val tokens = parseIssuedTokens(tokenBody) ?: return GoogleSignIn.Failed(GoogleSignInFailure.Rejected)
        val profile = parseGoogleProfile(getAuthorized(GOOGLE_USERINFO, tokens.accessToken))
            ?: return GoogleSignIn.Failed(GoogleSignInFailure.Rejected)
        val picture = profile.pictureUrl?.let { download(it) }
        return GoogleSignIn.Ok(profile.email, profile.name, tokens.refreshToken, picture)
    }

    private suspend fun readRedirect(server: ServerSocket, state: String): AuthRedirect {
        val socket = server.acceptCancellable()
        socket.use { client ->
            client.soTimeout = READ_MILLIS
            val reader = BufferedReader(InputStreamReader(client.getInputStream(), Charsets.ISO_8859_1))
            val line = reader.readLine().orEmpty()
            while (true) {
                val header = reader.readLine() ?: break
                if (header.isEmpty()) break
            }
            val redirect = parseAuthRedirect(line, state)
            writePage(client, redirect is AuthRedirect.Code)
            return redirect
        }
    }

    private fun writePage(socket: Socket, ok: Boolean) {
        val body = if (ok) {
            "<html><body><p>可以回到 Ainsmail</p></body></html>"
        } else {
            "<html><body><p>登录没有完成</p></body></html>"
        }
        val bytes = body.toByteArray(Charsets.UTF_8)
        val header = "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\n" +
            "Content-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
        val out = socket.getOutputStream()
        out.write(header.toByteArray(Charsets.US_ASCII))
        out.write(bytes)
        out.flush()
    }

    private fun postForm(url: String, body: String): String {
        val connection = open(url, "POST")
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        return readText(connection)
    }

    private fun getAuthorized(url: String, accessToken: String): String {
        val connection = open(url, "GET")
        connection.setRequestProperty("Authorization", "Bearer $accessToken")
        return readText(connection)
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

    private fun readText(connection: HttpURLConnection): String {
        try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val bytes = stream?.use { readCap(it, MAX_TEXT) }
            if (code !in 200..299 || bytes == null) throw AuthHttp()
            return bytes.toString(Charsets.UTF_8)
        } finally {
            connection.disconnect()
        }
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
        const val LOOPBACK = "127.0.0.1"
        const val WAIT_MILLIS = 180_000
        const val READ_MILLIS = 10_000
        const val HTTP_MILLIS = 20_000
        const val MAX_TEXT = 256 * 1024
        const val MAX_PICTURE = 2 * 1024 * 1024
    }
}

private class AuthHttp : Exception()

/** accept 是阻塞调用。取消时关掉端口，线程才能从 accept 里出来。 */
private suspend fun ServerSocket.acceptCancellable(): Socket = suspendCancellableCoroutine { cont ->
    cont.invokeOnCancellation { close() }
    thread(isDaemon = true, name = "ainsmail-loopback") {
        try {
            val socket = accept()
            if (!cont.isActive) {
                socket.close()
                return@thread
            }
            try {
                cont.resume(socket)
            } catch (closed: IllegalStateException) {
                socket.close()
            }
        } catch (error: Exception) {
            if (!cont.isActive) return@thread
            try {
                cont.resumeWithException(error)
            } catch (closed: IllegalStateException) {
                // 协程已经取消，异常不用再投递。
                return@thread
            }
        }
    }
}
