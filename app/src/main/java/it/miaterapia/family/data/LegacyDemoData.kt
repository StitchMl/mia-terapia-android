package it.miaterapia.family.data

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

enum class LegacyDemoCleanupResult { ABSENT, REMOVED, PRESERVED }

/** Fotografia transazionale del campione legacy e dei possibili indizi di utilizzo. */
data class LegacyDemoData(
    val patient: PatientEntity?,
    val medication: MedicationEntity?,
    val dose: DoseOccurrenceEntity?,
    val movements: List<StockMovementEntity>,
    val events: List<DoseEventEntity>,
    val plans: List<TreatmentPlanEntity>,
    val phases: List<SchedulePhaseEntity>,
    val otherReferenceCount: Int
) {
    fun cleanupDecision(): LegacyDemoCleanupResult {
        // Un evento, un rifornimento o un riferimento aggiuntivo possono essere
        // dati reali, anche se il relativo identificativo contiene ancora "demo".
        if (otherReferenceCount != 0 || events.isNotEmpty()) return LegacyDemoCleanupResult.PRESERVED
        val absent = patient == null && medication == null && dose == null &&
            movements.isEmpty() && plans.isEmpty() && phases.isEmpty()
        if (absent) return LegacyDemoCleanupResult.ABSENT

        val matchesOriginalSample = runCatching {
            (patient == null || matchesOriginalPatient(patient)) &&
                (medication == null || matchesOriginalMedication(medication)) &&
                (dose == null || matchesOriginalDose(dose)) &&
                movements.all(::matchesOriginalMovement) && movements.size <= 1 &&
                plans.all(::matchesOriginalPlan) && plans.size <= 1 &&
                phases.all(::matchesOriginalPhase) && phases.size <= 1
        }.getOrDefault(false)
        // Per frammenti incompleti mancano elementi sufficienti a riconoscere il
        // campione. Sono riconoscibili anche piano e fase tecnici rimasti orfani.
        val hasRecognizableSample = (patient != null && medication != null && dose != null) ||
            (plans.size == 1 && phases.size == 1)
        return if (matchesOriginalSample && hasRecognizableSample) LegacyDemoCleanupResult.REMOVED
            else LegacyDemoCleanupResult.PRESERVED
    }

    private fun matchesOriginalPatient(item: PatientEntity) =
        item.id == "patient-demo" && item.alias == "Matteo" && item.type == PatientType.HUMAN && item.active

    private fun matchesOriginalMedication(item: MedicationEntity) =
        item.id == "med-demo" && item.patientId == "patient-demo" && item.name == "Farmaco dimostrativo" &&
            item.form == "Compressa" && item.strength == "10 mg" && item.active

    private fun matchesOriginalDose(item: DoseOccurrenceEntity): Boolean {
        val zone = ZoneId.of(plans.singleOrNull()?.zoneId ?: ZoneId.systemDefault().id)
        val originalTime = Instant.ofEpochMilli(item.originalScheduledAtEpochMillis).atZone(zone).toLocalTime()
        return item.id == "dose-demo" && item.patientId == "patient-demo" && item.medicationId == "med-demo" &&
            item.status == DoseStatus.PLANNED && item.confirmedAtEpochMillis == null &&
            item.scheduledAtEpochMillis == item.originalScheduledAtEpochMillis &&
            item.doseAmount == "1" && item.doseUnit == StockUnit.TABLET && originalTime == LocalTime.of(20, 0)
    }

    private fun matchesOriginalMovement(item: StockMovementEntity) =
        item.id == "stock-demo" && item.medicationId == "med-demo" && item.causeOccurrenceId == null &&
            item.amountDecimal == "30" && item.unit == StockUnit.TABLET && item.type == MovementType.INITIAL

    private fun matchesOriginalPlan(item: TreatmentPlanEntity): Boolean {
        ZoneId.of(item.zoneId)
        return item.id == "plan-demo" && item.patientId == "patient-demo" && item.medicationId == "med-demo" &&
            item.startDate == "1970-01-01" && item.endDate == null && item.instructions == null
    }

    private fun matchesOriginalPhase(item: SchedulePhaseEntity) =
        item.id == "phase-demo" && item.planId == "plan-demo" && item.startDate == "1970-01-01" &&
            item.endDate == null && item.timesCsv == "20:00" && item.daysMask == 127 &&
            item.doseAmount == "1" && item.doseUnit == StockUnit.TABLET
}
