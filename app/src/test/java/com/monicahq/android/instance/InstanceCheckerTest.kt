package com.monicahq.android.instance

import com.monicahq.android.FakeMonica
import com.monicahq.android.FakeMonica.Companion.json
import com.monicahq.android.auth.AuthError
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InstanceCheckerTest {
    private val monica = FakeMonica()
    private val checker =
        InstanceChecker(
            com.monicahq.android.network.HttpClients
                .create(),
            appVersion = "1.2.0",
            allowHttp = true,
        )

    @After
    fun tearDown() = monica.close()

    private suspend fun failure(input: String = monica.url): AuthError? = (checker.check(input) as? CheckResult.Failed)?.error

    @Test
    fun `a compatible instance gives everything sign-in needs`() =
        runTest {
            val result = checker.check(monica.url)

            assertEquals(
                CheckResult.Compatible(
                    monica.url,
                    InstanceConfiguration(
                        issuer = monica.url,
                        authorizationEndpoint = "${monica.url}/oauth/authorize",
                        tokenEndpoint = "${monica.url}/oauth/token",
                        revocationEndpoint = "${monica.url}/oauth/revoke",
                        clientId = FakeMonica.CLIENT_ID,
                        scope = "api",
                        registrationEnabled = true,
                    ),
                ),
                result,
            )
        }

    @Test
    fun `an instance served under a path is compatible`() =
        runTest {
            FakeMonica(basePath = "/monica").use { underPath ->
                val result = checker.check(underPath.url + "/")

                assertTrue(result is CheckResult.Compatible)
                assertEquals(underPath.url, (result as CheckResult.Compatible).url)
                assertEquals(1, underPath.requestsTo("/api/v1/status").size)
            }
        }

    @Test
    fun `the api scope comes from the status, not from the OAuth description`() =
        runTest {
            val result = checker.check(monica.url) as CheckResult.Compatible

            assertEquals("api", result.configuration.scope)
        }

    @Test
    fun `invalid and http addresses are refused before any request`() =
        runTest {
            val release =
                InstanceChecker(
                    com.monicahq.android.network.HttpClients
                        .create(),
                    appVersion = "1.2.0",
                    allowHttp = false,
                )

            assertEquals(CheckResult.Failed(AuthError.InvalidAddress), release.check("not an address"))
            assertEquals(CheckResult.Failed(AuthError.HttpsRequired), release.check(monica.url))
            assertTrue(monica.requests.isEmpty())
        }

    @Test
    fun `a Monica without the mobile block is too old`() =
        runTest {
            monica.overrides["/api/v1/status"] = { json("""{"status":"ok","version":"5.0.0","instance":"x"}""") }

            assertEquals(AuthError.MonicaTooOld, failure())
        }

    @Test
    fun `api version 0 is too old`() =
        runTest {
            monica.overrides["/api/v1/status"] = { json(monica.status().replace("\"api_version\":1", "\"api_version\":0")) }

            assertEquals(AuthError.MonicaTooOld, failure())
        }

    @Test
    fun `an instance asking for a newer app says so`() =
        runTest {
            monica.minimumAndroidVersion = "1.10.0"

            assertEquals(AuthError.AppTooOld, failure())
        }

    @Test
    fun `an instance accepting this version is compatible`() =
        runTest {
            monica.minimumAndroidVersion = "1.2"

            assertTrue(checker.check(monica.url) is CheckResult.Compatible)
        }

    @Test
    fun `an empty client id is refused`() =
        runTest {
            monica.overrides["/api/v1/status"] = { json(monica.status().replace(FakeMonica.CLIENT_ID, " ")) }

            assertEquals(AuthError.NoAppSignIn, failure())
        }

    @Test
    fun `an issuer other than the instance is refused`() =
        runTest {
            monica.overrides["/.well-known/oauth-authorization-server"] = { json(monica.metadata(issuer = "https://auth.example.com")) }

            assertEquals(AuthError.NoAppSignIn, failure())
        }

    @Test
    fun `an endpoint on another host is refused`() =
        runTest {
            val metadata = monica.metadata().replace("${monica.url}/oauth/token", "https://evil.example/oauth/token")
            monica.overrides["/.well-known/oauth-authorization-server"] = { json(metadata) }

            assertEquals(AuthError.NoAppSignIn, failure())
        }

    @Test
    fun `a revocation endpoint outside the base path is refused`() =
        runTest {
            FakeMonica(basePath = "/monica").use { underPath ->
                val metadata =
                    underPath.metadata().replace(
                        "${underPath.url}/oauth/revoke",
                        "${underPath.url.removeSuffix("/monica")}/oauth/revoke",
                    )
                underPath.overrides["/.well-known/oauth-authorization-server"] = { json(metadata) }

                assertEquals(AuthError.NoAppSignIn, failure(underPath.url))
            }
        }

    @Test
    fun `an instance without S256 is refused`() =
        runTest {
            monica.overrides["/.well-known/oauth-authorization-server"] = { json(monica.metadata().replace("\"S256\"", "\"plain\"")) }

            assertEquals(AuthError.NoAppSignIn, failure())
        }

    @Test
    fun `an instance without an OAuth description is refused`() =
        runTest {
            monica.overrides["/.well-known/oauth-authorization-server"] = { MockResponse.Builder().code(404).build() }

            assertEquals(AuthError.NoAppSignIn, failure())
        }

    @Test
    fun `an HTML page is not Monica`() =
        runTest {
            monica.overrides["/api/v1/status"] = {
                MockResponse
                    .Builder()
                    .code(200)
                    .body("<html>Hello</html>")
                    .build()
            }

            assertEquals(AuthError.NotMonica, failure())
        }

    @Test
    fun `a missing status is not Monica`() =
        runTest {
            monica.overrides["/api/v1/status"] = { MockResponse.Builder().code(404).build() }

            assertEquals(AuthError.NotMonica, failure())
        }

    @Test
    fun `a redirect means the instance moved, and is not followed`() =
        runTest {
            monica.overrides["/api/v1/status"] = {
                MockResponse
                    .Builder()
                    .code(301)
                    .addHeader("Location", "https://elsewhere.example/api/v1/status")
                    .build()
            }

            assertEquals(AuthError.Moved, failure())
            assertEquals(1, monica.requests.size)
        }

    @Test
    fun `a failing instance is a server error`() =
        runTest {
            monica.overrides["/api/v1/status"] = { json("""{"status":"error"}""", code = 503) }

            assertEquals(AuthError.ServerError, failure())
        }

    @Test
    fun `an unreachable instance says so`() =
        runTest {
            val url = monica.url
            monica.close()

            assertEquals(AuthError.Unreachable, failure(url))
        }

    @Test
    fun `versions compare part by part`() {
        assertTrue(compareVersions("1.10.0", "1.9.9") > 0)
        assertTrue(compareVersions("1.2", "1.2.0") == 0)
        assertTrue(compareVersions("0.1.0", "1.0.0") < 0)
        assertTrue(compareVersions("2.0.0-beta", "2.0.0") == 0)
    }
}
