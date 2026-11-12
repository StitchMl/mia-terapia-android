package it.miaterapia.family.sync

import it.miaterapia.family.data.EventType

data class SyncEvent(
    val eventId: String,
    val patientId: String,
    val occurrenceId: String,
    val type: EventType,
    val occurredAtEpochMillis: Long,
    val actorDeviceId: String,
    val supersedesEventId: String? = null
)

data class MergeResult(val accepted: List<SyncEvent>, val rejectedDuplicates: Int, val conflicts: Set<String>)

object SyncProtocol {
    fun merge(local: Collection<SyncEvent>, remote: Collection<SyncEvent>): MergeResult {
        val byId = linkedMapOf<String, SyncEvent>()
        var duplicates = 0
        (local + remote).sortedWith(compareBy<SyncEvent> { it.occurredAtEpochMillis }.thenBy { it.eventId }).forEach { event ->
            if (byId.putIfAbsent(event.eventId, event) != null) duplicates++
        }
        val terminal = setOf(EventType.TAKEN, EventType.SKIPPED)
        val conflicts = byId.values.groupBy { it.occurrenceId }.filterValues { events ->
            events.filter { it.type in terminal && it.supersedesEventId == null }.map { it.type }.distinct().size > 1
        }.keys
        return MergeResult(byId.values.toList(), duplicates, conflicts)
    }

    fun remotePath(patientId: String, eventId: String): String = "MiaTerapia/v1/patients/$patientId/events/$eventId.json.enc"
}
