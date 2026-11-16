package it.miaterapia.family.domain

import it.miaterapia.family.data.*
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

object ReminderEligibility {
    fun canNotify(
        occurrence: DoseOccurrenceEntity?,
        medication: MedicationEntity?,
        plans: List<TreatmentPlanEntity>,
        phases: List<SchedulePhaseEntity>,
        assignedToDevice: Boolean
    ): Boolean {
        if (occurrence == null || medication == null || !assignedToDevice) return false
        if (!occurrence.status.isOperational() || !medication.active ||
            occurrence.medicationId != medication.id || occurrence.patientId != medication.patientId) return false
        // Lo schema non contiene un planId sulla dose: l'ID deterministico
        // identifica la fase. Usare sempre l'orario originale anche dopo un rinvio.
        return plans.any { plan ->
            plan.active && plan.medicationId == medication.id && plan.patientId == occurrence.patientId &&
                matchesOriginalSchedule(occurrence, plan, phases)
        }
    }

    private fun matchesOriginalSchedule(
        occurrence: DoseOccurrenceEntity,
        plan: TreatmentPlanEntity,
        phases: List<SchedulePhaseEntity>
    ): Boolean = runCatching {
        val original = Instant.ofEpochMilli(occurrence.originalScheduledAtEpochMillis).atZone(ZoneId.of(plan.zoneId))
        val date = original.toLocalDate()
        within(date, plan.startDate, plan.endDate) && phases.any { phase ->
            if (phase.planId != plan.id) return@any false
            // Una fase estranea malformata non deve invalidare la fase della dose.
            runCatching {
                val expectedId = ScheduleEngine.stableOccurrenceId(phase.id, date, original.toLocalTime(), original.zone)
                occurrence.id == expectedId &&
                    within(date, phase.startDate, phase.endDate) &&
                    (phase.daysMask and (1 shl (date.dayOfWeek.value - 1))) != 0 &&
                    phase.timesCsv.split(',').any { LocalTime.parse(it.trim()) == original.toLocalTime() } &&
                    phase.doseUnit == occurrence.doseUnit &&
                    phase.doseAmount.toBigDecimal().compareTo(occurrence.doseAmount.toBigDecimal()) == 0
            }.getOrDefault(false)
        }
    }.getOrDefault(false)

    private fun within(date: LocalDate, start: String, end: String?) =
        !date.isBefore(LocalDate.parse(start)) && (end == null || !date.isAfter(LocalDate.parse(end)))
}
