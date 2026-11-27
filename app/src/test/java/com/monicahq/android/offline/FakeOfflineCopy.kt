package com.monicahq.android.offline

import com.monicahq.android.vault.Vault

/**
 * The offline copy in memory, with the real rules: owner check on writes, the 30-day and clock
 * checks on opening, the update rule of [planCollection]. [onErase] lets a test see the app's state
 * at the moment of the erasure.
 */
class FakeOfflineCopy : OfflineCopy {
    var owner: Owner? = null
        private set
    var lastContactAt: Long? = null
        private set
    var erasures = 0
        private set

    /** Simulates a copy that cannot be opened (lost key, damaged file). */
    var unreadable = false

    /** Simulates opening that throws (full disk, broken Keystore). */
    var failOnOpen = false

    /** Makes erasing take time, so a test can cancel its caller meanwhile. */
    var eraseDelayMillis = 0L

    var onErase: () -> Unit = {}

    private var storedOwner: Owner? = null
    private val rows = mutableListOf<VaultEntityLike>()
    private val syncedAt = mutableMapOf<String, Long>()

    /** True when something is kept, open or not. */
    val hasData: Boolean get() = storedOwner != null || rows.isNotEmpty() || syncedAt.isNotEmpty()

    /** Pretends a copy for [owner] was left on disk by an earlier run, and the app just started: nothing is open. */
    fun leaveOnDisk(
        owner: Owner,
        lastContactAt: Long,
        vaults: List<Vault> = emptyList(),
        syncedAt: Long = lastContactAt,
    ) {
        this.owner = null
        storedOwner = owner
        this.lastContactAt = lastContactAt
        rows.clear()
        rows += vaults.mapIndexed { i, v -> VaultEntityLike(v, i) }
        if (vaults.isNotEmpty()) this.syncedAt["vaults"] = syncedAt
    }

    override suspend fun openOrErase(
        owner: Owner,
        now: Long,
    ) {
        if (failOnOpen) throw java.io.IOException("disk full")
        if (this.owner == owner) return
        val last = lastContactAt
        val usable =
            !unreadable && storedOwner == owner && last != null && now >= last && now - last <= RoomOfflineCopy.MAX_AGE
        if (!usable) {
            erase()
            lastContactAt = now
        }
        storedOwner = owner
        this.owner = owner
    }

    override suspend fun erase() {
        if (eraseDelayMillis > 0) kotlinx.coroutines.delay(eraseDelayMillis)
        onErase()
        erasures++
        owner = null
        storedOwner = null
        lastContactAt = null
        rows.clear()
        syncedAt.clear()
    }

    override suspend fun contacted(now: Long) {
        if (owner != null) lastContactAt = now
    }

    override val vaults: VaultCopy =
        object : VaultCopy {
            override suspend fun all(): Cached<List<Vault>>? {
                if (owner == null) return null
                val time = syncedAt["vaults"] ?: return null
                return Cached(rows.sortedBy { it.position }.map { it.vault }, time)
            }

            override suspend fun one(id: String): Cached<Vault>? {
                if (owner == null) return null
                val row = rows.firstOrNull { it.serverId == id } ?: return null
                val time = syncedAt["vault:$id"] ?: syncedAt["vaults"] ?: return null
                return Cached(row.vault, time)
            }

            override suspend fun replaceAll(
                owner: Owner,
                vaults: List<Vault>,
                now: Long,
            ) {
                if (owner != this@FakeOfflineCopy.owner) return
                val plan = planCollection(rows.toList(), vaults.mapIndexed { i, v -> VaultEntityLike(v, i) }) { row, _ -> row }
                rows.removeAll { it.localId in plan.deletions }
                plan.upserts.forEach { upsert -> rows.removeAll { it.localId == upsert.localId } }
                rows += plan.upserts
                syncedAt["vaults"] = now
            }

            override suspend fun replaceOne(
                owner: Owner,
                vault: Vault,
                now: Long,
            ) {
                if (owner != this@FakeOfflineCopy.owner) return
                val current = rows.firstOrNull { it.serverId == vault.id }
                val plan = planOne(current, VaultEntityLike(vault, current?.position ?: Int.MAX_VALUE)) { row, _ -> row }
                plan.upserts.forEach { upsert -> rows.removeAll { it.localId == upsert.localId } }
                rows += plan.upserts
                syncedAt["vault:${vault.id}"] = now
            }

            override suspend fun remove(
                owner: Owner,
                id: String,
            ) {
                if (owner != this@FakeOfflineCopy.owner) return
                val plan = planRemoval(rows.firstOrNull { it.serverId == id })
                rows.removeAll { it.localId in plan.deletions }
                syncedAt.remove("vault:$id")
            }
        }

    private data class VaultEntityLike(
        val vault: Vault,
        val position: Int,
        override val syncState: String = SyncState.Synced.stored,
    ) : SyncedRow {
        override val localId: String get() = vault.id
        override val serverId: String get() = vault.id
    }
}
