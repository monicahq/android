package com.monicahq.android.network

import com.monicahq.android.auth.AuthError
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Request
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLHandshakeException

class HttpClientsTest {
    private val server = MockWebServer()
    private val client = HttpClients.create()

    @After
    fun tearDown() = server.close()

    @Test
    fun `redirects are returned, not followed`() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .code(302)
                    .addHeader("Location", "https://elsewhere.example/")
                    .build(),
            )
            server.start()

            val response = client.newCall(Request.Builder().url(server.url("/")).build()).fetch()

            assertEquals(302, response.code)
            assertEquals(1, server.requestCount)
        }

    @Test
    fun `timeouts are set`() {
        assertEquals(10_000, client.connectTimeoutMillis)
        assertEquals(20_000, client.readTimeoutMillis)
        assertEquals(30_000, client.callTimeoutMillis)
        assertTrue(client.interceptors.isEmpty() && client.networkInterceptors.isEmpty())
    }

    @Test
    fun `an untrusted certificate is a certificate error`() =
        runTest {
            val certificate = HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
            server.useHttps(
                HandshakeCertificates
                    .Builder()
                    .heldCertificate(certificate)
                    .build()
                    .sslSocketFactory(),
            )
            server.start()

            val failure = runCatching { client.newCall(Request.Builder().url(server.url("/")).build()).fetch() }.exceptionOrNull()

            assertEquals(AuthError.Certificate, failure?.let(AuthError::from))
        }

    @Test
    fun `a server that never answers is a timeout`() =
        runTest {
            server.enqueue(MockResponse.Builder().headersDelay(2, TimeUnit.SECONDS).build())
            server.start()
            val impatient = client.newBuilder().readTimeout(200, TimeUnit.MILLISECONDS).build()

            val failure = runCatching { impatient.newCall(Request.Builder().url(server.url("/")).build()).fetch() }.exceptionOrNull()

            assertEquals(AuthError.Timeout, failure?.let(AuthError::from))
        }

    @Test
    fun `exceptions map to the error the person reads`() {
        assertEquals(AuthError.Unreachable, AuthError.from(UnknownHostException()))
        assertEquals(AuthError.Unreachable, AuthError.from(ConnectException()))
        assertEquals(AuthError.Unreachable, AuthError.from(IOException()))
        assertEquals(AuthError.Timeout, AuthError.from(SocketTimeoutException()))
        assertEquals(AuthError.Timeout, AuthError.from(InterruptedIOException()))
        assertEquals(AuthError.Certificate, AuthError.from(SSLHandshakeException("untrusted")))
        assertEquals(AuthError.ServerError, AuthError.from(IllegalStateException()))
        assertEquals(AuthError.Certificate, AuthError.from(IOException(SSLHandshakeException("untrusted"))))
        val firstRouteRefused = ConnectException().apply { addSuppressed(SSLHandshakeException("untrusted")) }
        assertEquals(AuthError.Certificate, AuthError.from(firstRouteRefused))
    }
}
