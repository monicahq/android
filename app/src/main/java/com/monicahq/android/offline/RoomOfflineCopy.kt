package com.monicahq.android.offline

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import com.monicahq.android.auth.TokenCipher
import com.monicahq.android.vault.Vault
import com.monicahq.android.vault.VaultColour
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * The offline copy in a Room database encrypted by SQLCipher, in [directory] (inside
 * `noBackupFilesDir`, which Android never backs up or transfers). The database passphrase is kept
 * encrypted by [cipher], a Keystore key of its own: erasing destroys that key first, so the files
 * are unreadable even before they are deleted (specs/004-offline-reading/research.md, R2).
 *
 * Every operation holds [mutex]: an erase can never interleave with a write, and a write for an
 * owner that is no longer current is dropped.
 */
class RoomOfflineCopy(
    private val context: Context,
    private val directory: File,
    private val cipher: TokenCipher,
    private val dispatcher: CoroutineDispatcher,
    private val clock: () -> Long = System::currentTimeMillis,
) : OfflineCopy {
    init {
        // SQLCipher's native code, needed before the first database is opened. Loading twice is a no-op.
        System.loadLibrary("sqlcipher")
    }

    private val mutex = Mutex()
    private val passphrase = Passphrase(File(directory, KEY_FILE), cipher)
    private val databaseFile = File(directory, DATABASE_FILE)

    private var database: OfflineDatabase? = null
    private var owner: Owner? = null

    /** Read without the lock to skip most calls cheaply; written under it. */
    @Volatile
    private var lastContactWritten = 0L

    private var lastSeen = 0L

    override suspend fun openOrErase(
        owner: Owner,
        now: Long,
    ) = withContext(dispatcher) {
        mutex.withLock {
            if (this@RoomOfflineCopy.owner == owner && database != null && isFresh(now)) return@withLock
            try {
                openLocked(owner, now)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                // A copy that cannot be opened or created (full disk, broken Keystore) is no copy:
                // erased, and the app works online without one. Writes are dropped, reads find nothing.
                eraseLocked()
            }
            this@RoomOfflineCopy.owner = owner
        }
    }

    private suspend fun openLocked(
        owner: Owner,
        now: Long,
    ) {
        closeLocked()
        val existing = if (passphrase.exists() || databaseFile.exists()) openExistingLocked() else null
        val meta = existing?.let { runCatching { it.sessionMeta().get() }.getOrNull() }
        if (existing != null && meta != null && meta.belongsTo(owner)) {
            database = existing
            lastContactWritten = meta.lastContactAt
            lastSeen = meta.lastSeenAt
            if (isFresh(now)) {
                seen(existing, owner, now)
                return
            }
        }
        existing?.close()
        database = null
        eraseLocked()
        val created = build(passphrase.create())
        database = created
        lastContactWritten = now
        lastSeen = now
        created.sessionMeta().upsert(
            SessionMetaEntity(instanceUrl = owner.instanceUrl, userId = owner.userId, lastContactAt = now, lastSeenAt = now),
        )
    }

    /** Fresh: confirmed by the instance within 30 days, and the clock never went back. */
    private fun isFresh(now: Long) = now >= lastContactWritten && now >= lastSeen && now - lastContactWritten <= MAX_AGE

    private suspend fun seen(
        db: OfflineDatabase,
        owner: Owner,
        now: Long,
    ) {
        lastSeen = maxOf(lastSeen, now)
        db.sessionMeta().upsert(
            SessionMetaEntity(
                instanceUrl = owner.instanceUrl,
                userId = owner.userId,
                lastContactAt = lastContactWritten,
                lastSeenAt = lastSeen,
            ),
        )
    }

    override suspend fun erase() = withContext(dispatcher) { mutex.withLock { eraseLocked() } }

    /** Closes the database and forgets the owner, keeping the files: what the end of the process does. */
    internal suspend fun close() =
        withContext(dispatcher) {
            mutex.withLock {
                closeLocked()
                owner = null
            }
        }

    override suspend fun contacted(now: Long) {
        // Recorded at most once an hour: far finer than the 30-day limit, and no write per request.
        if (now >= lastContactWritten && now - lastContactWritten < CONTACT_INTERVAL) return
        withContext(dispatcher) {
            mutex.withLock {
                val current = owner ?: return@withLock
                val db = database ?: return@withLock
                lastContactWritten = now
                runCatching { seen(db, current, now) }
            }
        }
    }

    override val vaults: VaultCopy =
        object : VaultCopy {
            override suspend fun all(): Cached<List<Vault>>? =
                read { db ->
                    val syncedAt = db.syncMeta().get(VAULTS)?.syncedAt ?: return@read null
                    Cached(db.vaults().all().map { it.toVault() }, syncedAt)
                }

            override suspend fun one(id: String): Cached<Vault>? =
                read { db ->
                    val row = db.vaults().byServerId(id) ?: return@read null
                    val syncedAt = (db.syncMeta().get(vaultResource(id)) ?: db.syncMeta().get(VAULTS))?.syncedAt ?: return@read null
                    Cached(row.toVault(), syncedAt)
                }

            override suspend fun replaceAll(
                owner: Owner,
                vaults: List<Vault>,
                now: Long,
            ) = write(owner) { db ->
                val dao = db.vaults()
                val answer = vaults.mapIndexed { position, vault -> vault.toEntity(position, now) }
                val plan = planCollection(dao.all(), answer) { row, localId -> row.copy(localId = localId) }
                dao.delete(plan.deletions)
                dao.upsert(plan.upserts)
                db.syncMeta().upsert(SyncMetaEntity(VAULTS, now))
            }

            override suspend fun replaceOne(
                owner: Owner,
                vault: Vault,
                now: Long,
            ) = write(owner) { db ->
                val dao = db.vaults()
                val current = dao.byServerId(vault.id)
                val plan =
                    planOne(
                        current,
                        vault.toEntity(current?.position ?: Int.MAX_VALUE, now),
                    ) { row, localId -> row.copy(localId = localId) }
                dao.upsert(plan.upserts)
                db.syncMeta().upsert(SyncMetaEntity(vaultResource(vault.id), now))
            }

            override suspend fun remove(
                owner: Owner,
                id: String,
            ) = write(owner) { db ->
                val dao = db.vaults()
                dao.delete(planRemoval(dao.byServerId(id)).deletions)
                db.syncMeta().delete(vaultResource(id))
            }
        }

    private suspend fun <T> read(block: suspend (OfflineDatabase) -> T?): T? =
        withContext(dispatcher) {
            mutex.withLock {
                // Checked on every read too: an app left open offline for 30 days loses its copy.
                if (database != null && !isFresh(clock())) eraseLocked()
                val db = database ?: return@withLock null
                // A copy that cannot be read is no copy: the screen asks the instance instead.
                runCatching { block(db) }.getOrNull()
            }
        }

    private suspend fun write(
        owner: Owner,
        block: suspend (OfflineDatabase) -> Unit,
    ) = withContext(dispatcher) {
        mutex.withLock {
            // An answer for a session that has ended since is dropped: it must not recreate data.
            if (owner != this@RoomOfflineCopy.owner) return@withLock
            val db = database ?: return@withLock
            runCatching { db.withTransaction { block(db) } }
        }
        Unit
    }

    private fun openExistingLocked(): OfflineDatabase? {
        var opened: OfflineDatabase? = null
        return runCatching {
            opened = build(passphrase.read())
            // Opening for real checks the key: a wrong or lost key throws here.
            opened?.openHelper?.writableDatabase
            opened
        }.getOrElse {
            opened?.close()
            null
        }
    }

    private fun build(key: ByteArray): OfflineDatabase {
        directory.mkdirs()
        return Room
            .databaseBuilder(context, OfflineDatabase::class.java, databaseFile.absolutePath)
            .openHelperFactory(SupportOpenHelperFactory(key))
            .build()
    }

    /** Forgets the database first, so nothing can use it even if closing fails. */
    private fun closeLocked() {
        val open = database
        database = null
        runCatching { open?.close() }
    }

    private fun eraseLocked() {
        owner = null
        lastContactWritten = 0L
        lastSeen = 0L
        closeLocked()
        // The key first: from here on, whatever is still on disk cannot be decrypted.
        runCatching { cipher.destroyKey() }
        if (!directory.deleteRecursively()) {
            // Whatever could not be deleted is unreadable without the key; the next start tries again.
            directory.walkBottomUp().forEach { runCatching { it.delete() } }
        }
    }

    private fun SessionMetaEntity.belongsTo(owner: Owner) = instanceUrl == owner.instanceUrl && userId == owner.userId

    companion object {
        const val DATABASE_FILE = "offline.db"
        const val KEY_FILE = "offline.key"

        /** The copy is erased after this long without any answer from the instance (spec FR-013). */
        val MAX_AGE = TimeUnit.DAYS.toMillis(30)
        val CONTACT_INTERVAL = TimeUnit.HOURS.toMillis(1)

        private const val VAULTS = "vaults"

        private fun vaultResource(id: String) = "vault:$id"
    }
}

private fun Vault.toEntity(
    position: Int,
    now: Long,
) = VaultEntity(
    localId = id,
    serverId = id,
    syncState = SyncState.Synced.stored,
    serverUpdatedAt = serverUpdatedAt,
    syncedAt = now,
    name = name,
    description = description,
    colour = colour.apiValue,
    isDefault = isDefault,
    contactCount = contactCount,
    position = position,
)

private fun VaultEntity.toVault() =
    Vault(
        id = serverId ?: localId,
        name = name,
        description = description,
        colour = VaultColour.fromApi(colour),
        isDefault = isDefault,
        contactCount = contactCount,
        serverUpdatedAt = serverUpdatedAt,
    )
