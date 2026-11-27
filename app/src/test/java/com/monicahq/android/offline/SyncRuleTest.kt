package com.monicahq.android.offline

import org.junit.Assert.assertEquals
import org.junit.Test

class SyncRuleTest {
    private data class Row(
        override val localId: String,
        override val serverId: String?,
        override val syncState: String = SyncState.Synced.stored,
        val name: String,
    ) : SyncedRow

    private fun answer(
        id: String,
        name: String,
    ) = Row(localId = id, serverId = id, name = name)

    private val keepLocalId: (Row, String) -> Row = { row, localId -> row.copy(localId = localId) }

    @Test
    fun `a synced row present in the answer is replaced, keeping its local id`() {
        val current = listOf(Row("local-1", "a", name = "Old"))

        val plan = planCollection(current, listOf(answer("a", "New")), keepLocalId)

        assertEquals(listOf(Row("local-1", "a", name = "New")), plan.upserts)
        assertEquals(emptyList<String>(), plan.deletions)
    }

    @Test
    fun `a synced row absent from the answer is deleted`() {
        val current = listOf(Row("a", "a", name = "Kept"), Row("b", "b", name = "Gone"))

        val plan = planCollection(current, listOf(answer("a", "Kept")), keepLocalId)

        assertEquals(listOf("b"), plan.deletions)
    }

    @Test
    fun `a new record is inserted`() {
        val plan = planCollection(emptyList(), listOf(answer("a", "New")), keepLocalId)

        assertEquals(listOf(answer("a", "New")), plan.upserts)
    }

    @Test
    fun `a row with a pending change is never replaced nor deleted`() {
        val pending =
            listOf(
                Row("a", "a", SyncState.PendingUpdate.stored, "Edited offline"),
                Row("b", "b", SyncState.PendingDelete.stored, "Deleted offline"),
                Row("local-new", null, SyncState.PendingCreate.stored, "Created offline"),
                Row("c", "c", SyncState.Failed.stored, "Refused"),
            )

        val plan = planCollection(pending, listOf(answer("a", "From the instance")), keepLocalId)

        assertEquals(emptyList<Row>(), plan.upserts)
        assertEquals(emptyList<String>(), plan.deletions)
    }

    @Test
    fun `an empty answer deletes every synced row and only those`() {
        val current = listOf(Row("a", "a", name = "A"), Row("p", null, SyncState.PendingCreate.stored, "P"))

        val plan = planCollection(current, emptyList(), keepLocalId)

        assertEquals(listOf("a"), plan.deletions)
    }

    @Test
    fun `one record replaces its synced row, or is inserted`() {
        assertEquals(listOf(answer("a", "New")), planOne(null, answer("a", "New"), keepLocalId).upserts)
        assertEquals(
            listOf(Row("local-1", "a", name = "New")),
            planOne(Row("local-1", "a", name = "Old"), answer("a", "New"), keepLocalId).upserts,
        )
    }

    @Test
    fun `one record with a pending change is kept`() {
        val pending = Row("a", "a", SyncState.PendingUpdate.stored, "Edited offline")

        assertEquals(SyncPlan(emptyList<Row>(), emptyList()), planOne(pending, answer("a", "New"), keepLocalId))
    }

    @Test
    fun `a record the instance no longer has is removed unless pending`() {
        assertEquals(listOf("a"), planRemoval(Row("a", "a", name = "A")).deletions)
        assertEquals(emptyList<String>(), planRemoval(Row("a", "a", SyncState.PendingUpdate.stored, "A")).deletions)
        assertEquals(emptyList<String>(), planRemoval<Row>(null).deletions)
    }
}
