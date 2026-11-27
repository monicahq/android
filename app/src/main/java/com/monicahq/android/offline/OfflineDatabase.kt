package com.monicahq.android.offline

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert

/**
 * The offline copy's tables (specs/004-offline-reading/data-model.md). Every table holding instance
 * data has the five synchronisation columns. The schema is exported to `app/schemas/`: a new version
 * comes with a migration and its test, never a destructive fallback.
 */
@Database(
    entities = [VaultEntity::class, SyncMetaEntity::class, SessionMetaEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class OfflineDatabase : RoomDatabase() {
    abstract fun vaults(): VaultDao

    abstract fun syncMeta(): SyncMetaDao

    abstract fun sessionMeta(): SessionMetaDao
}

@Entity(tableName = "vaults", indices = [Index(value = ["server_id"], unique = true)])
data class VaultEntity(
    @PrimaryKey @ColumnInfo(name = "local_id") override val localId: String,
    @ColumnInfo(name = "server_id") override val serverId: String?,
    @ColumnInfo(name = "sync_state", defaultValue = "synced") override val syncState: String,
    @ColumnInfo(name = "server_updated_at") val serverUpdatedAt: Long?,
    @ColumnInfo(name = "synced_at") val syncedAt: Long,
    val name: String,
    val description: String?,
    val colour: String,
    @ColumnInfo(name = "is_default") val isDefault: Boolean,
    @ColumnInfo(name = "contact_count") val contactCount: Int,
    /** The vault's place in the instance's answer, so the list reads back in the same order. */
    val position: Int,
) : SyncedRow

@Dao
interface VaultDao {
    @Query("SELECT * FROM vaults ORDER BY position, server_id")
    suspend fun all(): List<VaultEntity>

    @Query("SELECT * FROM vaults WHERE server_id = :serverId")
    suspend fun byServerId(serverId: String): VaultEntity?

    @Upsert
    suspend fun upsert(rows: List<VaultEntity>)

    @Query("DELETE FROM vaults WHERE local_id IN (:localIds)")
    suspend fun delete(localIds: List<String>)
}

/** When a collection (`vaults`) or a record (`vault:<id>`) was last answered by the instance. */
@Entity(tableName = "sync_meta")
data class SyncMetaEntity(
    @PrimaryKey val resource: String,
    @ColumnInfo(name = "synced_at") val syncedAt: Long,
)

@Dao
interface SyncMetaDao {
    @Query("SELECT * FROM sync_meta WHERE resource = :resource")
    suspend fun get(resource: String): SyncMetaEntity?

    @Upsert
    suspend fun upsert(row: SyncMetaEntity)

    @Query("DELETE FROM sync_meta WHERE resource = :resource")
    suspend fun delete(resource: String)
}

/** Whom the copy belongs to and when the instance last answered: exactly one row. */
@Entity(tableName = "session_meta")
data class SessionMetaEntity(
    @PrimaryKey val id: Int = 1,
    @ColumnInfo(name = "instance_url") val instanceUrl: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "last_contact_at") val lastContactAt: Long,
    /** The latest time the phone's clock showed while the copy was open: a clock set back before it erases. */
    @ColumnInfo(name = "last_seen_at") val lastSeenAt: Long,
)

@Dao
interface SessionMetaDao {
    @Query("SELECT * FROM session_meta WHERE id = 1")
    suspend fun get(): SessionMetaEntity?

    @Upsert
    suspend fun upsert(row: SessionMetaEntity)
}
