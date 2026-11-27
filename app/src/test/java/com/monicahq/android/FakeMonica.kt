package com.monicahq.android

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
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
 * `/api/v1/me` and the vault endpoints, which accept only the latest access token. Every request is recorded.
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
                        "/api/v1/vaults" -> if (request.method == "POST") createVault(request) else vaults(request)
                        else -> if (path.startsWith("/api/v1/vaults/")) vault(request, path.substringAfterLast('/')) else notFound()
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

    private fun authorized(request: RecordedRequest): Boolean {
        val token = validAccessToken
        return token != null && request.headers["Authorization"] == "Bearer $token"
    }

    private fun unauthenticated() = json("""{"message":"Unauthenticated."}""", code = 401)

    private fun notFound() = json("""{"message":"Not found."}""", code = 404)

    private fun me(request: RecordedRequest): MockResponse {
        if (!authorized(request)) return unauthenticated()
        return json("""{"data":{"type":"user","id":"7","attributes":{"first_name":"Jane","last_name":"Doe","email":"jane@example.com"}}}""")
    }

    /** The account's vaults, in their arranged order. Empty, as every account starts. */
    val vaults = CopyOnWriteArrayList<Vault>()

    data class Vault(
        val id: String,
        val name: String,
        val description: String? = null,
        val colour: String = "#3f5fd0",
        val isDefault: Boolean = false,
        val contactCount: Int = 0,
    )

    /** Adds vaults named [names], the first one default. */
    fun addVaults(vararg names: String) {
        names.forEach { name ->
            vaults += Vault(id = "vault-${vaults.size + 1}", name = name, isDefault = vaults.isEmpty())
        }
    }

    private fun vaults(request: RecordedRequest): MockResponse {
        if (!authorized(request)) return unauthenticated()
        val page = request.url.queryParameter("page")?.toIntOrNull() ?: 1
        val perPage = request.url.queryParameter("per_page")?.toIntOrNull() ?: 25
        val items = vaults.drop((page - 1) * perPage).take(perPage)
        return json(
            """{"data":[${items.joinToString(",") { vaultJson(it) }}],"meta":{"page":$page,"per_page":$perPage,"total":${vaults.size}}}""",
        )
    }

    private fun vault(
        request: RecordedRequest,
        id: String,
    ): MockResponse {
        if (!authorized(request)) return unauthenticated()
        val vault = vaults.firstOrNull { it.id == id } ?: return notFound()
        return json("""{"data":${vaultJson(vault)}}""")
    }

    private fun createVault(request: RecordedRequest): MockResponse {
        if (!authorized(request)) return unauthenticated()
        val body = Json.parseToJsonElement(request.body?.utf8().orEmpty()).jsonObject
        val makeDefault = vaults.isEmpty() || body["is_default"]?.jsonPrimitive?.booleanOrNull == true
        if (makeDefault) vaults.replaceAll { it.copy(isDefault = false) }
        val vault =
            Vault(
                id = "vault-${vaults.size + 1}",
                name = body.getValue("name").jsonPrimitive.content,
                description = body["description"]?.jsonPrimitive?.contentOrNull,
                colour = body["colour"]?.jsonPrimitive?.content ?: "#3f5fd0",
                isDefault = makeDefault,
            )
        vaults += vault
        return json("""{"data":${vaultJson(vault)}}""", code = 201)
    }

    private fun vaultJson(vault: Vault): String =
        buildJsonObject {
            put("type", "vault")
            put("id", vault.id)
            put(
                "attributes",
                buildJsonObject {
                    put("slug", vault.name.lowercase())
                    put("name", vault.name)
                    put("description", vault.description)
                    put("colour", vault.colour)
                    put("position", vaults.indexOf(vault) + 1)
                    put("is_default", vault.isDefault)
                    put("contact_count", vault.contactCount)
                },
            )
        }.toString()

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
