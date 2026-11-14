package it.miaterapia.family.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.math.BigDecimal
import java.util.UUID
import it.miaterapia.family.domain.TherapyLimits
import android.content.Context
import it.miaterapia.family.notifications.AlarmScheduler
import it.miaterapia.family.settings.DeviceSettings
import it.miaterapia.family.domain.ScheduleEngine
import it.miaterapia.family.domain.ScheduleRule

data class TherapySnapshot(val patients: List<PatientEntity> = emptyList(), val medications: List<MedicationEntity> = emptyList(), val doses: List<DoseOccurrenceEntity> = emptyList(), val movements: List<StockMovementEntity> = emptyList(), val requests: List<RefillRequestEntity> = emptyList())

/** Punto di ingresso applicativo: coordina transazioni Room, regole e allarmi locali. */
class TherapyRepository(private val context: Context, private val db: AppDatabase) {
    private val dao = db.therapyDao()
    val snapshot = combine(dao.patients(), dao.medications(), dao.doses(), dao.stockMovements(), dao.refillRequests()) { p, m, d, s, r -> TherapySnapshot(p, m, d, s, r) }
    /** Bonifica una tantum delle sole chiavi tecniche usate dalla prima alpha. */
    suspend fun removeLegacyDemoData() = db.withTransaction {
        AlarmScheduler(context).cancel("dose-demo")
        dao.deleteLegacyDemoData()
        DeviceSettings(context).setAlarmAssigned("patient-demo", false)
    }
    suspend fun confirmDose(id: String) = dao.confirmDose(id, System.currentTimeMillis())
    suspend fun changeDoseStatus(id: String, status: DoseStatus) = dao.changeDoseStatus(id, status, System.currentTimeMillis())
    suspend fun upcomingDoses(from: Long, until: Long) = dao.upcomingDoses(from, until)
    suspend fun addPatient(alias: String, type: PatientType) {
        require(alias.isNotBlank()) { "Inserisci il nome del profilo" }
        dao.insertPatient(PatientEntity(alias = alias.trim(), type = type))
    }

    suspend fun addMedication(patientId: String, name: String, form: String, strength: String?, amount: String, unit: StockUnit, times: List<LocalTime>) = db.withTransaction {
        require(name.isNotBlank() && form.isNotBlank()) { "Nome e forma sono obbligatori" }
        val decimal = amount.toBigDecimal()
        require(decimal > BigDecimal.ZERO) { "La dose deve essere positiva" }
        require(times.isNotEmpty() && times.size <= 5 && times.distinct().size == times.size) { "Inserisci da 1 a 5 orari diversi" }
        require(TherapyLimits.canActivateMedication(dao.activeMedicationCount(patientId))) { "Limite di 30 farmaci attivi raggiunto" }
        val medication = MedicationEntity(patientId = patientId, name = name.trim(), form = form.trim(), strength = strength?.trim()?.ifBlank { null })
        val today = LocalDate.now()
        val zone = ZoneId.systemDefault()
        val plan = TreatmentPlanEntity(patientId = patientId, medicationId = medication.id, startDate = today.toString(), endDate = null, zoneId = zone.id)
        val phase = SchedulePhaseEntity(planId = plan.id, startDate = today.toString(), endDate = null, timesCsv = times.sorted().joinToString(","), doseAmount = decimal.toPlainString(), doseUnit = unit)
        dao.insertMedication(medication)
        dao.insertPlan(plan)
        dao.insertPhase(phase)
        // Materializziamo 14 giorni: abbastanza per gli allarmi, senza far crescere
        // indefinitamente il database. Una rigenerazione mantiene gli stessi ID.
        ScheduleEngine.generate(ScheduleRule(today, null, times, zoneId = zone, doseAmount = decimal, doseUnit = unit, scheduleId = phase.id), today, 14).forEach { planned ->
            val scheduled = planned.scheduledAt.toInstant().toEpochMilli()
            val occurrence = DoseOccurrenceEntity(id = planned.occurrenceId, patientId = patientId, medicationId = medication.id, scheduledAtEpochMillis = scheduled, doseAmount = decimal.toPlainString(), doseUnit = unit)
            dao.insertDose(occurrence)
            if (DeviceSettings(context).isAlarmAssigned(patientId) && scheduled > System.currentTimeMillis()) AlarmScheduler(context).schedule(occurrence.id, scheduled)
        }
    }

    suspend fun addStock(medicationId: String, amount: String, unit: StockUnit) {
        val decimal = amount.toBigDecimal()
        require(decimal > BigDecimal.ZERO) { "La quantità deve essere positiva" }
        dao.insertMovement(StockMovementEntity(medicationId = medicationId, causeOccurrenceId = null, amountDecimal = decimal.toPlainString(), unit = unit, type = MovementType.REFILL))
    }

    suspend fun archiveMedication(id: String) = dao.archiveMedication(id)
    suspend fun reverseDose(id: String) = dao.reverseDose(id, System.currentTimeMillis())
    suspend fun recordAsNeeded(patientId: String, medicationId: String, amount: String, unit: StockUnit): Boolean = db.withTransaction {
        val decimal = amount.toBigDecimal()
        require(decimal > BigDecimal.ZERO) { "La quantità deve essere positiva" }
        val now = System.currentTimeMillis()
        val occurrence = DoseOccurrenceEntity(patientId = patientId, medicationId = medicationId, scheduledAtEpochMillis = now, doseAmount = decimal.toPlainString(), doseUnit = unit)
        dao.insertDose(occurrence)
        dao.confirmDose(occurrence.id, now)
    }
    suspend fun createRefillRequest(patientId: String, medicationIds: List<String>, extraText: String) {
        require(medicationIds.isNotEmpty() || extraText.isNotBlank()) { "Seleziona un farmaco o inserisci una richiesta manuale" }
        dao.insertRefillRequest(RefillRequestEntity(patientId = patientId, medicationIdsCsv = medicationIds.distinct().joinToString(","), note = extraText.trim().ifBlank { null }, status = RequestStatus.READY))
    }
    suspend fun markRequestFulfilled(id: String) = dao.updateRequestStatus(id, RequestStatus.FULFILLED, System.currentTimeMillis())
}
