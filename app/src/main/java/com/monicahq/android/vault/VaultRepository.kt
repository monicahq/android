package com.monicahq.android.vault

import com.monicahq.android.auth.SessionManager
import com.monicahq.android.network.ApiClient
import com.monicahq.android.network.ApiResult
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The vaults of the signed-in account, read from and written to the instance. This is where the
 * vault endpoints and their JSON live; `ApiClient` only carries requests (see
 * docs/architecture.md, "Talking to the API").
 *
 * The client is taken from [sessions] at each call and never kept, so after a sign-out or an
 * instance switch no call can reach the previous instance.
 */
class VaultRepository(
    private val sessions: SessionManager,
) {
    /** Every vault, in the order the account arranged them, asking page after page. */
    suspend fun vaults(): ApiResult<List<Vault>> {
        val client = sessions.apiClient() ?: return ApiResult.Discarded
        val vaults = mutableListOf<Vault>()
        var page = 1
        while (true) {
            val result = client.get("/api/v1/vaults?page=$page&per_page=$PER_PAGE") { json.decodeFromString(VaultPage.serializer(), it) }
            if (result !is ApiResult.Success) return result.withoutValue()
            vaults += result.value.data.map { it.toVault() }
            // An empty page ends it too: vaults deleted while paging would otherwise loop forever.
            if (vaults.size >= result.value.meta.total || result.value.data.isEmpty()) return ApiResult.Success(vaults)
            page++
        }
    }

    /** One vault, by its id. [ApiResult.NotFound] when it was deleted. */
    suspend fun vault(id: String): ApiResult<Vault> = withClient { it.get("/api/v1/vaults/$id", ::parseOne) }

    /** Creates a vault. It goes at the end of the list; refusals come back as [ApiResult.Invalid]. */
    suspend fun create(newVault: NewVault): ApiResult<Vault> {
        val body =
            buildJsonObject {
                put("name", newVault.name.trim())
                newVault.description
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { put("description", it) }
                put("colour", newVault.colour.apiValue)
                if (newVault.makeDefault) put("is_default", true)
            }
        return withClient { it.post("/api/v1/vaults", body.toString(), ::parseOne) }
    }

    private suspend fun <T> withClient(call: suspend (ApiClient) -> ApiResult<T>): ApiResult<T> =
        sessions.apiClient()?.let { call(it) } ?: ApiResult.Discarded

    private fun parseOne(body: String): Vault = json.decodeFromString(VaultDocument.serializer(), body).data.toVault()

    private companion object {
        /** The most the API gives in one page. */
        const val PER_PAGE = 100
        val json = Json { ignoreUnknownKeys = true }
    }
}

/** Any result but a success, retyped: it carries no value. */
private fun ApiResult<*>.withoutValue(): ApiResult<Nothing> =
    when (this) {
        is ApiResult.Success -> error("a success has a value")
        is ApiResult.Failed -> this
        ApiResult.SessionEnded -> ApiResult.SessionEnded
        ApiResult.Discarded -> ApiResult.Discarded
        ApiResult.NotFound -> ApiResult.NotFound
        is ApiResult.Invalid -> this
    }

@Serializable
private data class VaultPage(
    val data: List<VaultObject>,
    val meta: PageMeta,
)

@Serializable
private data class PageMeta(
    val total: Int,
)

@Serializable
private data class VaultDocument(
    val data: VaultObject,
)

@Serializable
private data class VaultObject(
    val id: String,
    val attributes: VaultAttributes,
)

@Serializable
private data class VaultAttributes(
    val name: String,
    val description: String? = null,
    val colour: String? = null,
    @SerialName("is_default") val isDefault: Boolean = false,
    @SerialName("contact_count") val contactCount: Int = 0,
)

private fun VaultObject.toVault(): Vault =
    Vault(
        id = id,
        name = attributes.name,
        description = attributes.description?.takeIf { it.isNotBlank() },
        colour = VaultColour.fromApi(attributes.colour),
        isDefault = attributes.isDefault,
        contactCount = attributes.contactCount,
    )
