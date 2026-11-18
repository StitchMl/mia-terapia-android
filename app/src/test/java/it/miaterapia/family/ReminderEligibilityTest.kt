package it.miaterapia.family

import it.miaterapia.family.data.*
import it.miaterapia.family.domain.ReminderEligibility
import it.miaterapia.family.domain.ScheduleEngine
import it.miaterapia.family.domain.DoseStateMachine
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class ReminderEligibilityTest {
    private val date = LocalDate.of(2026, 11, 16)
    private val time = LocalTime.of(8, 0)
    private val zone = ZoneId.of("Europe/Rome")
    private val medication = MedicationEntity("med", "patient", "Test", "Compressa", null)
    private val plan = TreatmentPlanEntity("plan", "patient", "med", date.toString(), null, zone.id)
    private val phase = SchedulePhaseEntity("phase", "plan", date.toString(), null, "08:00", doseAmount = "1", doseUnit = StockUnit.TABLET)
    private val dose = DoseOccurrenceEntity(ScheduleEngine.stableOccurrenceId(phase.id, date, time, zone), "patient", "med",
        date.atTime(time).atZone(zone).toInstant().toEpochMilli(), doseAmount = "1", doseUnit = StockUnit.TABLET)

    private fun eligible(occurrence: DoseOccurrenceEntity? = dose, med: MedicationEntity? = medication,
        plans: List<TreatmentPlanEntity> = listOf(plan), phases: List<SchedulePhaseEntity> = listOf(phase),
        assigned: Boolean = true) = ReminderEligibility.canNotify(occurrence, med, plans, phases, assigned)

    @Test fun operationalStatesAreEligible() {
        listOf(DoseStatus.PLANNED, DoseStatus.NOTIFIED, DoseStatus.SNOOZED).forEach {
            assertTrue(eligible(dose.copy(status = it)))
        }
    }

    @Test fun cancellationIsAllowedOnlyForOperationalStates() {
        DoseStatus.entries.forEach { status ->
            val machine = DoseStateMachine(status)
            if (status.isOperational()) {
                machine.cancelled()
                assertEquals(DoseStatus.CANCELLED, machine.state)
            } else {
                try {
                    machine.cancelled()
                    fail("Uno stato terminale non deve essere modificato")
                } catch (expected: IllegalStateException) {
                    assertEquals(status, machine.state)
                }
            }
        }
    }

    @Test fun t05ArchivedMedicationCannotNotify() {
        assertFalse(eligible(med = medication.copy(active = false)))
    }

    @Test fun missingOrTerminalDoseCannotNotify() {
        assertFalse(eligible(occurrence = null))
        assertFalse(eligible(med = null))
        DoseStatus.entries.filterNot { it.isOperational() }.forEach { assertFalse(eligible(dose.copy(status = it))) }
    }

    @Test fun unassignedOrMismatchedPatientCannotNotify() {
        assertFalse(eligible(assigned = false))
        assertFalse(eligible(dose.copy(patientId = "other")))
        assertFalse(eligible(med = medication.copy(id = "other")))
        assertFalse(eligible(plans = listOf(plan.copy(patientId = "other"))))
    }

    @Test fun missingInactiveOrExpiredPlanCannotNotify() {
        assertFalse(eligible(plans = emptyList()))
        assertFalse(eligible(plans = listOf(plan.copy(active = false))))
        assertFalse(eligible(plans = listOf(plan.copy(endDate = date.minusDays(1).toString()))))
        assertFalse(eligible(plans = listOf(plan.copy(startDate = date.plusDays(1).toString()))))
        assertFalse(eligible(plans = listOf(plan.copy(zoneId = "invalid"))))
    }

    @Test fun invalidPhaseCannotNotify() {
        listOf(phase.copy(planId = "other"), phase.copy(timesCsv = "09:00"), phase.copy(daysMask = 0),
            phase.copy(endDate = date.minusDays(1).toString()), phase.copy(doseAmount = "2"),
            phase.copy(doseUnit = StockUnit.DROP), phase.copy(timesCsv = "invalid")).forEach {
            assertFalse(eligible(phases = listOf(it)))
        }
        assertFalse(eligible(phases = emptyList()))
    }

    @Test fun overlappingActivePlanCannotReplaceTheOriginalInactivePlan() {
        val otherPlan = plan.copy(id = "other")
        val otherPhase = phase.copy(id = "other-phase", planId = otherPlan.id)
        assertFalse(eligible(plans = listOf(plan.copy(active = false), otherPlan), phases = listOf(phase, otherPhase)))
    }

    @Test fun snoozeUsesOriginalScheduleAndRetainsPhaseIdentity() {
        assertTrue(eligible(dose.copy(status = DoseStatus.SNOOZED,
            scheduledAtEpochMillis = dose.scheduledAtEpochMillis + 15 * 60_000)))
        assertFalse(eligible(dose.copy(id = "unrelated")))
    }
}
