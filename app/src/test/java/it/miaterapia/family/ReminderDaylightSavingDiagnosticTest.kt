package it.miaterapia.family

import it.miaterapia.family.data.DoseOccurrenceEntity
import it.miaterapia.family.data.DoseStatus
import it.miaterapia.family.data.MedicationEntity
import it.miaterapia.family.data.SchedulePhaseEntity
import it.miaterapia.family.data.StockUnit
import it.miaterapia.family.data.TreatmentPlanEntity
import it.miaterapia.family.domain.PlannedOccurrence
import it.miaterapia.family.domain.ReminderEligibility
import it.miaterapia.family.domain.ScheduleEngine
import it.miaterapia.family.domain.ScheduleRule
import it.miaterapia.family.notifications.ReminderIdentity
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

class ReminderDaylightSavingDiagnosticTest {
    private val zone = ZoneId.of("Europe/Rome")
    private val spring = LocalDate.of(2026, 3, 29)
    private val autumn = LocalDate.of(2026, 10, 25)
    private val normalDay = LocalDate.of(2026, 3, 28)
    private val nominalTime = LocalTime.of(2, 30)
    private val shiftedTime = LocalTime.of(3, 30)
    private val medication = MedicationEntity("med", "patient", "Test", "Compressa", null)
    private val plan = TreatmentPlanEntity("plan", medication.patientId, medication.id, "2026-01-01", null, zone.id)
    private val phase = SchedulePhaseEntity(
        "phase", plan.id, plan.startDate, null, "02:30", doseAmount = "1", doseUnit = StockUnit.TABLET
    )

    @Test fun springGapShiftsTheInstantAndPreservesTheNominalIdentity() {
        assertTrue(zone.rules.getValidOffsets(spring.atTime(nominalTime)).isEmpty())
        val planned = generate(spring).single()
        val dose = stored(planned)

        assertEquals(shiftedTime, planned.scheduledAt.toLocalTime())
        assertEquals(Instant.parse("2026-03-29T01:30:00Z"), planned.scheduledAt.toInstant())
        assertEquals(id(spring, nominalTime), dose.id)
        assertNotEquals(id(spring, shiftedTime), dose.id)
        assertEquals(dose.id, generate(spring).single().occurrenceId)
        assertTrue(eligible(dose))
    }

    @Test fun springGapAndAnExplicit0330RetainDistinctIdentitiesAtTheSameInstant() {
        val twoTimes = phase.copy(timesCsv = "02:30,03:30")
        val (shiftedDose, explicitDose) = generate(spring, listOf(nominalTime, shiftedTime)).map(::stored)

        assertEquals(shiftedDose.scheduledAtEpochMillis, explicitDose.scheduledAtEpochMillis)
        assertNotEquals(shiftedDose.id, explicitDose.id)
        assertTrue(eligible(shiftedDose, twoTimes))
        assertTrue(eligible(explicitDose, twoTimes))
        assertFalse(eligible(shiftedDose, twoTimes.copy(timesCsv = "03:30")))
        assertFalse(eligible(explicitDose, twoTimes.copy(timesCsv = "02:30")))
        for (action in listOf(ReminderIdentity.ALARM, "snooze15", "taken")) {
            assertNotEquals(ReminderIdentity.uri(shiftedDose.id, action), ReminderIdentity.uri(explicitDose.id, action))
        }
    }

    @Test fun autumnOverlapGeneratesOneDoseWithTheEarlierOffset() {
        assertEquals(listOf(ZoneOffset.ofHours(2), ZoneOffset.ofHours(1)),
            zone.rules.getValidOffsets(autumn.atTime(nominalTime)))
        val planned = generate(autumn).single()
        val dose = stored(planned)

        assertEquals(ZoneOffset.ofHours(2), planned.scheduledAt.offset)
        assertEquals(Instant.parse("2026-10-25T00:30:00Z"), planned.scheduledAt.toInstant())
        assertEquals(id(autumn, nominalTime), dose.id)
        assertEquals(planned, generate(autumn).single())
        assertTrue(eligible(dose))

        // Il secondo offset non e' l'istante scelto dal generatore.
        val laterMillis = planned.scheduledAt.withLaterOffsetAtOverlap().toInstant().toEpochMilli()
        assertFalse(eligible(dose.copy(
            scheduledAtEpochMillis = laterMillis, originalScheduledAtEpochMillis = laterMillis
        )))
    }

    @Test fun normalDaysKeepDistinctIdsAndRejectADifferentPhase() {
        val first = stored(generate(normalDay).single())
        val second = stored(generate(normalDay.minusDays(1)).single())

        assertEquals(Instant.parse("2026-03-28T01:30:00Z").toEpochMilli(), first.originalScheduledAtEpochMillis)
        assertEquals(id(normalDay, nominalTime), first.id)
        assertNotEquals(first.id, second.id)
        assertTrue(eligible(first))
        assertTrue(eligible(second))
        assertFalse(eligible(first, phase.copy(id = "different-phase")))
        assertFalse(eligible(first.copy(status = DoseStatus.TAKEN)))
    }

    @Test fun persistedIdentityAndEffectiveTimeCanBeReconstructedForAValidSnooze() {
        val normal = stored(generate(normalDay).single())
        val springDose = stored(generate(spring).single())
        val autumnDose = stored(generate(autumn).single())

        for (dose in listOf(normal, springDose, autumnDose)) {
            val restored = stored(generate(Instant.ofEpochMilli(dose.originalScheduledAtEpochMillis)
                .atZone(zone).toLocalDate()).single())
            assertEquals(dose, restored)
            val snoozed = restored.copy(
                status = DoseStatus.SNOOZED,
                scheduledAtEpochMillis = restored.originalScheduledAtEpochMillis + 15 * 60_000
            )
            assertEquals(dose.originalScheduledAtEpochMillis, snoozed.originalScheduledAtEpochMillis)
            assertEquals(ReminderIdentity.uri(dose.id, ReminderIdentity.ALARM),
                ReminderIdentity.uri(snoozed.id, ReminderIdentity.ALARM))
            assertTrue(eligible(restored.copy(status = DoseStatus.NOTIFIED)))
            assertTrue(eligible(snoozed))
            assertFalse(eligible(snoozed.copy(originalScheduledAtEpochMillis = snoozed.scheduledAtEpochMillis)))
        }
    }

    @Test fun gapAcrossMidnightUsesTheNominalDateForPlanAndPhaseValidity() {
        val skippedDate = LocalDate.of(2011, 12, 30)
        val apia = ZoneId.of("Pacific/Apia")
        val apiaPlan = plan.copy(startDate = skippedDate.toString(), endDate = skippedDate.toString(), zoneId = apia.id)
        val apiaPhase = phase.copy(startDate = skippedDate.toString(), endDate = skippedDate.toString(),
            daysMask = 1 shl (skippedDate.dayOfWeek.value - 1))
        val planned = ScheduleEngine.generate(
            ScheduleRule(skippedDate, skippedDate, listOf(nominalTime), zoneId = apia,
                doseAmount = BigDecimal.ONE, doseUnit = phase.doseUnit, scheduleId = phase.id),
            skippedDate, windowDays = 1
        ).single()

        assertEquals(skippedDate.plusDays(1), planned.scheduledAt.toLocalDate())
        assertTrue(ReminderEligibility.canNotify(stored(planned), medication,
            listOf(apiaPlan), listOf(apiaPhase), assignedToDevice = true))
    }

    private fun id(date: LocalDate, time: LocalTime) =
        ScheduleEngine.stableOccurrenceId(phase.id, date, time, zone)

    private fun generate(date: LocalDate, times: List<LocalTime> = listOf(nominalTime)) =
        ScheduleEngine.generate(
            ScheduleRule(date, date, times, zoneId = zone, doseAmount = BigDecimal.ONE,
                doseUnit = phase.doseUnit, scheduleId = phase.id),
            date, windowDays = 1
        )

    private fun stored(planned: PlannedOccurrence) = DoseOccurrenceEntity(
        id = planned.occurrenceId,
        patientId = medication.patientId,
        medicationId = medication.id,
        scheduledAtEpochMillis = planned.scheduledAt.toInstant().toEpochMilli(),
        doseAmount = planned.doseAmount.toPlainString(),
        doseUnit = planned.doseUnit
    )

    private fun eligible(dose: DoseOccurrenceEntity, candidatePhase: SchedulePhaseEntity = phase) =
        ReminderEligibility.canNotify(dose, medication, listOf(plan), listOf(candidatePhase), assignedToDevice = true)
}
