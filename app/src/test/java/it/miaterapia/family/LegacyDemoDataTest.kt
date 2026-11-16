package it.miaterapia.family

import it.miaterapia.family.data.*
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class LegacyDemoDataTest {
    private val zone = ZoneId.systemDefault()
    private val at = LocalDate.of(2026, 11, 16).atTime(LocalTime.of(20, 0)).atZone(zone).toInstant().toEpochMilli()
    private val sample = LegacyDemoData(
        PatientEntity("patient-demo", "Matteo", PatientType.HUMAN),
        MedicationEntity("med-demo", "patient-demo", "Farmaco dimostrativo", "Compressa", "10 mg"),
        DoseOccurrenceEntity("dose-demo", "patient-demo", "med-demo", at, doseAmount = "1", doseUnit = StockUnit.TABLET),
        listOf(StockMovementEntity("stock-demo", "med-demo", null, "30", StockUnit.TABLET, MovementType.INITIAL)),
        emptyList(), emptyList(), emptyList(), 0
    )
    private val plan = TreatmentPlanEntity("plan-demo", "patient-demo", "med-demo", "1970-01-01", null, zone.id)
    private val phase = SchedulePhaseEntity("phase-demo", "plan-demo", "1970-01-01", null, "20:00", doseAmount = "1", doseUnit = StockUnit.TABLET)

    @Test fun emptyDatabaseHasNothingToRemove() {
        assertEquals(LegacyDemoCleanupResult.ABSENT,
            LegacyDemoData(null, null, null, emptyList(), emptyList(), emptyList(), emptyList(), 0).cleanupDecision())
    }

    @Test fun originalUnusedAlphaSampleIsRecognized() {
        assertEquals(LegacyDemoCleanupResult.REMOVED, sample.cleanupDecision())
        assertEquals(LegacyDemoCleanupResult.REMOVED,
            sample.copy(plans = listOf(plan), phases = listOf(phase)).cleanupDecision())
    }

    @Test fun identifiersWithEditedDataArePreserved() {
        listOf(sample.copy(patient = sample.patient!!.copy(alias = "Profilo personale")),
            sample.copy(medication = sample.medication!!.copy(name = "Farmaco personale")),
            sample.copy(medication = sample.medication!!.copy(strength = "20 mg")),
            sample.copy(dose = sample.dose!!.copy(patientId = "other"))).forEach {
            assertEquals(LegacyDemoCleanupResult.PRESERVED, it.cleanupDecision())
        }
    }

    @Test fun anyDoseProgressProtectsHistory() {
        DoseStatus.entries.filter { it != DoseStatus.PLANNED }.forEach {
            assertEquals(LegacyDemoCleanupResult.PRESERVED,
                sample.copy(dose = sample.dose!!.copy(status = it)).cleanupDecision())
        }
        assertEquals(LegacyDemoCleanupResult.PRESERVED,
            sample.copy(dose = sample.dose!!.copy(confirmedAtEpochMillis = at)).cleanupDecision())
    }

    @Test fun evenAutomaticEventsArePreservedWhenOriginIsUncertain() {
        listOf(EventType.NOTIFIED, EventType.CANCELLED, EventType.CORRECTION, EventType.TAKEN).forEach {
            val event = DoseEventEntity(occurrenceId = "dose-demo", type = it, occurredAtEpochMillis = at, actorDeviceId = "local")
            assertEquals(LegacyDemoCleanupResult.PRESERVED, sample.copy(events = listOf(event)).cleanupDecision())
        }
    }

    @Test fun changedOrAdditionalStockProtectsAllRecords() {
        val initial = sample.movements.single()
        listOf(listOf(initial.copy(amountDecimal = "29")),
            listOf(initial, initial.copy(id = "refill", type = MovementType.REFILL)),
            listOf(initial.copy(causeOccurrenceId = "dose-demo"))).forEach {
            assertEquals(LegacyDemoCleanupResult.PRESERVED, sample.copy(movements = it).cleanupDecision())
        }
    }

    @Test fun otherPatientDataPreventsDeletingItsProfile() {
        assertEquals(LegacyDemoCleanupResult.PRESERVED, sample.copy(otherReferenceCount = 1).cleanupDecision())
        assertEquals(LegacyDemoCleanupResult.PRESERVED,
            LegacyDemoData(null, null, null, emptyList(), emptyList(), emptyList(), emptyList(), 1).cleanupDecision())
    }

    @Test fun completeTechnicalPlanAndPhaseCanBeRemovedAfterOldCleanup() {
        val orphans = LegacyDemoData(null, null, null, emptyList(), emptyList(), listOf(plan), listOf(phase), 0)
        assertEquals(LegacyDemoCleanupResult.REMOVED, orphans.cleanupDecision())
    }

    @Test fun unknownOrModifiedPlansAndPhasesArePreserved() {
        listOf(sample.copy(plans = listOf(plan.copy(instructions = "Istruzioni personali")), phases = listOf(phase)),
            sample.copy(plans = listOf(plan, plan.copy(id = "user-plan")), phases = listOf(phase)),
            sample.copy(plans = listOf(plan), phases = listOf(phase.copy(timesCsv = "08:00"))),
            sample.copy(plans = listOf(plan), phases = listOf(phase, phase.copy(id = "user-phase")))).forEach {
            assertEquals(LegacyDemoCleanupResult.PRESERVED, it.cleanupDecision())
        }
    }

    @Test fun incompleteFragmentsArePreserved() {
        assertEquals(LegacyDemoCleanupResult.PRESERVED, sample.copy(dose = null).cleanupDecision())
        assertEquals(LegacyDemoCleanupResult.PRESERVED,
            LegacyDemoData(null, null, null, sample.movements, emptyList(), emptyList(), emptyList(), 0).cleanupDecision())
    }

    @Test fun malformedPlanIsPreservedWithoutThrowing() {
        assertEquals(LegacyDemoCleanupResult.PRESERVED,
            sample.copy(plans = listOf(plan.copy(zoneId = "invalid")), phases = listOf(phase)).cleanupDecision())
    }

    @Test fun aRescheduledDoseIsNotAnUnusedSample() {
        assertEquals(LegacyDemoCleanupResult.PRESERVED,
            sample.copy(dose = sample.dose!!.copy(scheduledAtEpochMillis = at + 15 * 60_000L)).cleanupDecision())
    }
}
