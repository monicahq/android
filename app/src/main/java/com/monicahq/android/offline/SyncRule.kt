package com.monicahq.android.offline

/**
 * Where a row stands with the instance. Always [Synced] today: the other states exist so that
 * offline writing can come later without changing any record's identity
 * (specs/004-offline-reading/research.md, R3).
 */
enum class SyncState(
    val stored: String,
) {
    Synced("synced"),
    PendingCreate("pending_create"),
    PendingUpdate("pending_update"),
    PendingDelete("pending_delete"),
    Failed("failed"),
}

/** A row of the copy that comes from the instance. */
interface SyncedRow {
    /** The row's own id on the phone; equal to [serverId] for every row today. */
    val localId: String

    /** The instance's id; null only for a record not yet created on the instance. */
    val serverId: String?

    /** One of [SyncState.stored]. */
    val syncState: String
}

/** What to write so that the copy matches an answer: rows to insert or replace, local ids to delete. */
data class SyncPlan<T>(
    val upserts: List<T>,
    val deletions: List<String>,
)

private val SyncedRow.isSynced: Boolean get() = syncState == SyncState.Synced.stored

/**
 * The update rule for a complete collection answered by the instance:
 * 1. a synced row present in the answer is replaced (keeping its local id);
 * 2. a synced row absent from the answer is deleted;
 * 3. a record with no row is inserted;
 * 4. a row with a pending change is never replaced nor deleted.
 *
 * [answer] rows carry their server id; [withLocalId] gives one the local id of the row it replaces.
 */
fun <T : SyncedRow> planCollection(
    current: List<T>,
    answer: List<T>,
    withLocalId: (T, String) -> T,
): SyncPlan<T> {
    val byServerId = current.filter { it.serverId != null }.associateBy { it.serverId }
    val upserts =
        answer.mapNotNull { received ->
            val existing = byServerId[received.serverId]
            when {
                existing == null -> received
                existing.isSynced -> withLocalId(received, existing.localId)
                else -> null
            }
        }
    val answered = answer.mapTo(HashSet()) { it.serverId }
    val deletions = current.filter { it.isSynced && it.serverId !in answered }.map { it.localId }
    return SyncPlan(upserts, deletions)
}

/** The update rule for one record answered by the instance: replace it, unless it has a pending change. */
fun <T : SyncedRow> planOne(
    current: T?,
    answer: T,
    withLocalId: (T, String) -> T,
): SyncPlan<T> =
    when {
        current == null -> SyncPlan(listOf(answer), emptyList())
        current.isSynced -> SyncPlan(listOf(withLocalId(answer, current.localId)), emptyList())
        else -> SyncPlan(emptyList(), emptyList())
    }

/** The instance says a record no longer exists: delete its row, unless it has a pending change. */
fun <T : SyncedRow> planRemoval(current: T?): SyncPlan<T> =
    if (current != null && current.isSynced) SyncPlan(emptyList(), listOf(current.localId)) else SyncPlan(emptyList(), emptyList())
