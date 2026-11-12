package it.miaterapia.family

import it.miaterapia.family.data.EventType
import it.miaterapia.family.sync.SyncEvent
import it.miaterapia.family.sync.SyncProtocol
import org.junit.Assert.*
import org.junit.Test

class SyncProtocolTest {
    private fun event(id: String, type: EventType, time: Long = 1) = SyncEvent(id, "p1", "o1", type, time, "d1")

    @Test fun t17DuplicateRemoteEventIsIdempotent() {
        val item = event("e1", EventType.TAKEN)
        val result = SyncProtocol.merge(listOf(item), listOf(item))
        assertEquals(1, result.accepted.size)
        assertEquals(1, result.rejectedDuplicates)
    }

    @Test fun t18ConcurrentTakenAndSkippedCreatesVisibleConflict() {
        val result = SyncProtocol.merge(listOf(event("e1", EventType.TAKEN)), listOf(event("e2", EventType.SKIPPED)))
        assertEquals(setOf("o1"), result.conflicts)
        assertEquals(2, result.accepted.size)
    }

    @Test fun t19DrivePathContainsNoClinicalName() {
        assertEquals("MiaTerapia/v1/patients/p1/events/e1.json.enc", SyncProtocol.remotePath("p1", "e1"))
    }
}
