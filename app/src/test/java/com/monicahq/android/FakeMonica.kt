package com.monicahq.android

import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import java.io.Closeable
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * A Monica instance on MockWebServer, served under [basePath], answering like the real one:
 * status, OAuth description, token endpoint with rotating refresh tokens, revocation, and
 * `/api/v1/me`, which accepts only the latest access token. Every request is recorded.
 */
class FakeMonica(
    private val basePath: String = "",
    private val tokenPrefix: String = "",
) : Closeable {
    private val server = MockWebServer()
    val requests = CopyOnWriteArrayList<RecordedRequest>()
    private val issued = AtomicInteger(0)

    /** The access token `/me` accepts; null refuses every token, as after a revocation. */
    @Volatile
    var validAccessToken: String? = null

    /** The refresh token the token endpoint accepts. */
    @Volatile
    var validRefreshToken: String? = null

    /** Replaces the answer to a path (relative to [basePath]), e.g. "/api/v1/status". */
    val overrides = mutableMapOf<String, (RecordedRequest) -> MockResponse>()

    /** Delay before the token endpoint answers, to let concurrent requests pile up. */
    @Volatile
    var tokenDelayMillis = 0L

    @Volatile
    var registrationEnabled = true

    @Volatile
    var minimumAndroidVersion: String? = null

    init {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    requests += request
                    val path = request.url.encodedPath.removePrefix(basePath)
                    overrides[path]?.let { return it(request) }
                    return when (path) {
                        "/api/v1/status" -> json(status())
                        "/.well-known/oauth-authorization-server" -> json(metadata())
                        "/oauth/token" -> token(request)
                        "/oauth/revoke" -> revoke(request)
                        "/api/v1/me" -> me(request)
                        else -> MockResponse.Builder().code(404).build()
                    }
                }
            }
        server.start()
    }

    /** The instance address, as a person would type it. */
    val url: String get() = server.url(basePath).toString().trimEnd('/')

    fun requestsTo(path: String): List<RecordedRequest> = requests.filter { it.url.encodedPath == basePath + path }

    fun form(request: RecordedRequest): Map<String, String> =
        request.body
            ?.utf8()
            .orEmpty()
            .split('&')
            .filter { it.isNotEmpty() }
            .associate {
                java.net.URLDecoder.decode(it.substringBefore('='), "UTF-8") to java.net.URLDecoder.decode(it.substringAfter('='), "UTF-8")
            }

    fun status(): String =
        """
        {"status":"ok","version":"5.3.0","instance":"monica.test","mobile":{"api_version":1,"client_id":"$CLIENT_ID",
        "scope":"api","registration_enabled":$registrationEnabled,
        "minimum_android_version":${minimumAndroidVersion?.let { "\"$it\"" } ?: "null"}}}
        """.trimIndent()

    fun metadata(issuer: String = url): String =
        """
        {"issuer":"$issuer","authorization_endpoint":"$url/oauth/authorize","token_endpoint":"$url/oauth/token",
        "revocation_endpoint":"$url/oauth/revoke","code_challenge_methods_supported":["S256"],
        "scopes_supported":["mcp:use"],"prompt_values_supported":["none","login","consent","create"]}
        """.trimIndent()

    private fun token(request: RecordedRequest): MockResponse {
        if (tokenDelayMillis > 0) Thread.sleep(tokenDelayMillis)
        val form = form(request)
        val accepted =
            when (form["grant_type"]) {
                "authorization_code" -> form["code"] == CODE
                "refresh_token" -> form["refresh_token"] != null && form["refresh_token"] == validRefreshToken
                else -> false
            }
        if (!accepted) return json("""{"error":"invalid_grant"}""", code = 400)
        val n = issued.incrementAndGet()
        val access = "${tokenPrefix}access-$n"
        val refresh = "${tokenPrefix}refresh-$n"
        validAccessToken = access
        validRefreshToken = refresh
        return json("""{"token_type":"Bearer","expires_in":3600,"access_token":"$access","refresh_token":"$refresh"}""")
    }

    private fun revoke(request: RecordedRequest): MockResponse {
        if (form(request)["token"] == validRefreshToken) {
            validAccessToken = null
            validRefreshToken = null
        }
        return MockResponse.Builder().code(200).build()
    }

    private fun me(request: RecordedRequest): MockResponse {
        val token = validAccessToken
        if (token == null || request.headers["Authorization"] != "Bearer $token") {
            return json("""{"message":"Unauthenticated."}""", code = 401)
        }
        return json("""{"data":{"type":"user","id":"7","attributes":{"first_name":"Jane","last_name":"Doe","email":"jane@example.com"}}}""")
    }

    override fun close() = server.close()

    companion object {
        const val CLIENT_ID = "0198f6a4-7c2e-7d3b-9a61-5e0b4c8d2f17"
        const val CODE = "the-code"

        fun json(
            body: String,
            code: Int = 200,
        ): MockResponse =
            MockResponse
                .Builder()
                .code(code)
                .addHeader("Content-Type", "application/json")
                .body(body)
                .build()

        fun delayed(
            response: MockResponse,
            millis: Long,
        ): MockResponse =
            response
                .newBuilder()
                .headersDelay(millis, TimeUnit.MILLISECONDS)
                .build()
    }
}
