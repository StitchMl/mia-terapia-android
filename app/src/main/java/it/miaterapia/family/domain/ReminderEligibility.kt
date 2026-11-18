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
        val zone = ZoneId.of(plan.zoneId)
        val originalInstant = Instant.ofEpochMilli(occurrence.originalScheduledAtEpochMillis)
        val original = originalInstant.atZone(zone)
        val resolvedDate = original.toLocalDate()
        val transition = zone.rules.previousTransition(originalInstant.plusNanos(1))
        // Un salto a cavallo della mezzanotte puo' cambiare anche la data nominale.
        val nominalDate = if (transition?.isGap == true &&
            originalInstant.isBefore(transition.instant.plus(transition.duration))
        ) original.toLocalDateTime().minus(transition.duration).toLocalDate() else resolvedDate

        setOf(resolvedDate, nominalDate).any { date ->
            within(date, plan.startDate, plan.endDate) && phases.any phaseMatches@{ phase ->
                if (phase.planId != plan.id) return@phaseMatches false
                // Una fase estranea malformata non deve invalidare la fase della dose.
                runCatching {
                    within(date, phase.startDate, phase.endDate) &&
                        (phase.daysMask and (1 shl (date.dayOfWeek.value - 1))) != 0 &&
                        phase.doseUnit == occurrence.doseUnit &&
                        phase.doseAmount.toBigDecimal().compareTo(occurrence.doseAmount.toBigDecimal()) == 0 &&
                        phase.timesCsv.split(',').any { configuredTime ->
                            val time = runCatching { LocalTime.parse(configuredTime.trim()) }.getOrNull()
                            time != null &&
                                ScheduleEngine.stableOccurrenceId(phase.id, date, time, zone) == occurrence.id &&
                                ScheduleEngine.resolveScheduledTime(date, time, zone).toInstant().toEpochMilli() ==
                                occurrence.originalScheduledAtEpochMillis
                        }
                }.getOrDefault(false)
            }
        }
    }.getOrDefault(false)

    private fun within(date: LocalDate, start: String, end: String?) =
        !date.isBefore(LocalDate.parse(start)) && (end == null || !date.isAfter(LocalDate.parse(end)))
}
