package com.monicahq.android.vault

import com.monicahq.android.FakeMonica
import com.monicahq.android.TestGraph
import com.monicahq.android.network.ApiResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultRepositoryTest {
    private val monica = FakeMonica()
    private val other = FakeMonica(tokenPrefix = "b-")
    private val graph = TestGraph()
    private val repository = graph.vaultRepository

    @After
    fun tearDown() {
        monica.close()
        other.close()
    }

    private suspend fun signedIn(): FakeMonica {
        graph.signIn(monica)
        monica.requests.clear()
        return monica
    }

    private fun ApiResult<List<Vault>>.names(): List<String> = (this as ApiResult.Success).value.map { it.name }

    private fun lastBody(): JsonObject =
        Json
            .parseToJsonElement(
                monica
                    .requestsTo("/api/v1/vaults")
                    .last()
                    .body
                    ?.utf8()
                    .orEmpty(),
            ).jsonObject

    @Test
    fun `an account with no vault answers an empty list`() =
        runTest {
            signedIn()

            assertEquals(ApiResult.Success(emptyList<Vault>()), repository.vaults())
        }

    @Test
    fun `vaults come with every field the screens show`() =
        runTest {
            signedIn().vaults +=
                FakeMonica.Vault(
                    id = "v1",
                    name = "Personal",
                    description = "Mine",
                    colour = "#4f8a49",
                    isDefault = true,
                    contactCount = 25,
                )

            assertEquals(
                ApiResult.Success(listOf(Vault("v1", "Personal", "Mine", VaultColour.Green, isDefault = true, contactCount = 25))),
                repository.vaults(),
            )
            assertEquals(
                "100",
                monica.requests
                    .single()
                    .url
                    .queryParameter("per_page"),
            )
        }

    @Test
    fun `every page is read, in the instance's order`() =
        runTest {
            signedIn().addVaults(*Array(250) { "Vault ${it + 1}" })

            val names = repository.vaults().names()

            assertEquals(250, names.size)
            assertEquals((1..250).map { "Vault $it" }, names)
            assertEquals(listOf("1", "2", "3"), monica.requests.map { it.url.queryParameter("page") })
        }

    @Test
    fun `an empty page ends the reading, even short of the total`() =
        runTest {
            signedIn()
            monica.overrides["/api/v1/vaults"] = { request ->
                if (request.url.queryParameter("page") == "1") {
                    FakeMonica.json(
                        """{"data":[{"id":"v1","attributes":{"name":"Personal"}}],"meta":{"total":200}}""",
                    )
                } else {
                    FakeMonica.json("""{"data":[],"meta":{"total":200}}""")
                }
            }

            assertEquals(listOf("Personal"), repository.vaults().names())
            assertEquals(2, monica.requests.size)
        }

    @Test
    fun `a blank description is no description and an unknown colour is graphite`() =
        runTest {
            signedIn().vaults += FakeMonica.Vault(id = "v1", name = "Work", description = "  ", colour = "#123456")

            val vault = (repository.vaults() as ApiResult.Success).value.single()

            assertNull(vault.description)
            assertEquals(VaultColour.Slate, vault.colour)
        }

    @Test
    fun `a failure while paging is passed on`() =
        runTest {
            signedIn()
            monica.overrides["/api/v1/vaults"] = { FakeMonica.json("{}", code = 500) }

            assertTrue(repository.vaults() is ApiResult.Failed)
        }

    @Test
    fun `one vault is read by its id`() =
        runTest {
            signedIn().addVaults("Personal", "Family")

            assertEquals("Family", (repository.vault("vault-2") as ApiResult.Success).value.name)
        }

    @Test
    fun `a deleted vault is not found`() =
        runTest {
            signedIn()

            assertEquals(ApiResult.NotFound, repository.vault("gone"))
        }

    @Test
    fun `creating sends the trimmed name, the description and the colour`() =
        runTest {
            signedIn()

            val result = repository.create(NewVault(name = "  Family ", description = " Everybody ", colour = VaultColour.Plum))

            assertEquals("Family", (result as ApiResult.Success).value.name)
            val body = lastBody()
            assertEquals("Family", body.getValue("name").jsonPrimitive.content)
            assertEquals("Everybody", body.getValue("description").jsonPrimitive.content)
            assertEquals("#a8447a", body.getValue("colour").jsonPrimitive.content)
            assertFalse(body.containsKey("is_default"))
            assertFalse(body.containsKey("structure_version_id"))
        }

    @Test
    fun `a blank description is not sent`() =
        runTest {
            signedIn()

            repository.create(NewVault(name = "Family", description = "   "))

            assertFalse(lastBody().containsKey("description"))
        }

    @Test
    fun `making the vault the default is sent only when asked`() =
        runTest {
            signedIn().addVaults("Personal")

            val result = repository.create(NewVault(name = "Family", makeDefault = true))

            assertEquals("true", lastBody().getValue("is_default").jsonPrimitive.content)
            assertTrue((result as ApiResult.Success).value.isDefault)
        }

    @Test
    fun `a refusal comes back with the instance's words`() =
        runTest {
            signedIn()
            monica.overrides["/api/v1/vaults"] = {
                FakeMonica.json(
                    """{"message":"The free plan holds 1 vault.","errors":{"name":["The free plan holds 1 vault."]}}""",
                    code = 422,
                )
            }

            assertEquals(
                ApiResult.Invalid("The free plan holds 1 vault.", mapOf("name" to "The free plan holds 1 vault.")),
                repository.create(NewVault(name = "Family")),
            )
        }

    @Test
    fun `nothing is asked while signed out`() =
        runTest {
            graph.sessionManager.load()

            assertEquals(ApiResult.Discarded, repository.vaults())
            assertEquals(ApiResult.Discarded, repository.vault("v1"))
            assertEquals(ApiResult.Discarded, repository.create(NewVault(name = "Family")))
        }

    @Test
    fun `a list from the previous instance is discarded after switching`() =
        runTest {
            signedIn().addVaults("Personal")
            monica.overrides["/api/v1/vaults"] = { FakeMonica.delayed(FakeMonica.json("""{"data":[],"meta":{"total":0}}"""), 500) }

            val request = async(Dispatchers.Default) { repository.vaults() }
            Thread.sleep(100)
            graph.sessionManager.logOut()
            graph.signIn(other)

            assertEquals(ApiResult.Discarded, request.await())
            assertTrue(other.requests.none { it.headers["Authorization"] == "Bearer access-1" })
            assertTrue(other.requestsTo("/api/v1/vaults").isEmpty())
        }
}
