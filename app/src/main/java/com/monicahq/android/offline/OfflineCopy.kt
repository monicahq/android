package com.monicahq.android.offline

import com.monicahq.android.vault.Vault

/** Whom the offline copy belongs to: one account on one instance. */
data class Owner(
    val instanceUrl: String,
    val userId: String,
)

/** Something read from the offline copy, and when the instance last answered it. */
data class Cached<T>(
    val value: T,
    /** Milliseconds, phone clock. */
    val syncedAt: Long,
)

/**
 * The copy of what the instance answered, kept on the phone so screens can be read offline
 * (constitution, principle VIII; specs/004-offline-reading/contracts/offline-copy.md).
 *
 * Only `SessionManager` opens and erases it: the copy lives exactly as long as the session. Every
 * write names the [Owner] it was made for and is dropped unless that owner is still the current
 * one, so an answer arriving after a sign-out never recreates data.
 */
interface OfflineCopy {
    /**
     * Opens the copy for [owner]. Erases it first when it belongs to someone else, cannot be read,
     * or has not been confirmed by the instance for more than 30 days (or the clock went back
     * before the last confirmation).
     */
    suspend fun openOrErase(
        owner: Owner,
        now: Long,
    )

    /** Erases everything: the key first, so what is left on disk is unreadable at once, then the files. */
    suspend fun erase()

    /** The instance answered an authenticated request: the copy is still the session's. */
    suspend fun contacted(now: Long)

    val vaults: VaultCopy
}

/** The vaults part of the copy. */
interface VaultCopy {
    /** The vault list, in the instance's order, or null when it was never received. */
    suspend fun all(): Cached<List<Vault>>?

    /** One vault, with the time it (or the list holding it) was last received; null when unknown. */
    suspend fun one(id: String): Cached<Vault>?

    /** Replaces the list with a complete answer from the instance (specs/004 data-model.md, "Update rule"). */
    suspend fun replaceAll(
        owner: Owner,
        vaults: List<Vault>,
        now: Long,
    )

    /** Replaces one vault with the instance's answer. */
    suspend fun replaceOne(
        owner: Owner,
        vault: Vault,
        now: Long,
    )

    /** The instance says this vault no longer exists. */
    suspend fun remove(
        owner: Owner,
        id: String,
    )
}
