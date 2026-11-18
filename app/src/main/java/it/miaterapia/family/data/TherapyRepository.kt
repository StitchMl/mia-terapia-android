package it.miaterapia.family.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.math.BigDecimal
import it.miaterapia.family.domain.TherapyLimits
import android.content.Context
import it.miaterapia.family.notifications.AndroidReminderDelivery
import it.miaterapia.family.notifications.ReminderDelivery
import it.miaterapia.family.notifications.ReminderIdentity
import it.miaterapia.family.domain.ReminderEligibility
import it.miaterapia.family.settings.DeviceSettings
import it.miaterapia.family.domain.ScheduleEngine
import it.miaterapia.family.domain.ScheduleRule

data class TherapySnapshot(val patients: List<PatientEntity> = emptyList(), val medications: List<MedicationEntity> = emptyList(), val doses: List<DoseOccurrenceEntity> = emptyList(), val movements: List<StockMovementEntity> = emptyList(), val requests: List<RefillRequestEntity> = emptyList())

/** Punto di ingresso applicativo: coordina transazioni Room, regole e allarmi locali. */
class TherapyRepository(private val context: Context, private val db: AppDatabase,
    private val reminders: ReminderDelivery = AndroidReminderDelivery(context)) {
    private val dao = db.therapyDao()
    val snapshot = combine(dao.patients(), dao.medications(), dao.doses(), dao.stockMovements(), dao.refillRequests()) { p, m, d, s, r -> TherapySnapshot(p, m, d, s, r) }

    suspend fun initializeLocalData(): LegacyDemoCleanupResult {
        val demoCleanup = removeLegacyDemoData()
        cleanupArchivedReminders()
        return demoCleanup
    }

    suspend fun cleanupArchivedReminders() = db.withTransaction {
        clearReminders(dao.archivedOccurrenceIds())
    }

    /** Bonifica ripetibile del solo campione riconosciuto come mai utilizzato. */
    suspend fun removeLegacyDemoData(): LegacyDemoCleanupResult = db.withTransaction {
        val result = dao.deleteLegacyDemoData()
        if (result != LegacyDemoCleanupResult.PRESERVED) {
            // Eseguire anche senza righe demo: una precedente chiusura del processo
            // potrebbe avere lasciato allarmi, azioni o notifiche in entrambi i formati.
            clearReminders(listOf("dose-demo"))
            DeviceSettings(context).setAlarmAssigned("patient-demo", false)
        }
        result
    }
    suspend fun confirmDose(id: String) = dao.confirmDose(id, System.currentTimeMillis())
    suspend fun changeDoseStatus(id: String, status: DoseStatus) = dao.changeDoseStatus(id, status, System.currentTimeMillis())
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
            if (scheduled > System.currentTimeMillis() && canDeliver(occurrence)) reminders.schedule(occurrence.id, scheduled)
        }
    }

    suspend fun addStock(medicationId: String, amount: String, unit: StockUnit) {
        val decimal = amount.toBigDecimal()
        require(decimal > BigDecimal.ZERO) { "La quantità deve essere positiva" }
        dao.insertMovement(StockMovementEntity(medicationId = medicationId, causeOccurrenceId = null, amountDecimal = decimal.toPlainString(), unit = unit, type = MovementType.REFILL))
    }

    suspend fun archiveMedication(id: String) {
        val ids = dao.archiveMedication(id, System.currentTimeMillis())
        // Room e AlarmManager non condividono una transazione: prima rendere le
        // dosi inoperative, poi pulire Android. Un broadcast gia' in coda sara' respinto.
        clearReminders(ids)
    }

    private suspend fun clearReminders(ids: List<String>) {
        if (ids.isEmpty()) return
        val clearedIds = ids.toSet()
        val otherIds = dao.allOccurrenceIds().filterNot { it in clearedIds }
        withContext(Dispatchers.IO) {
            ids.forEach { clearReminder(it, otherIds) }
        }
    }

    private fun clearReminder(id: String, allIds: List<String>) = reminders.clear(id,
        ReminderIdentity.safeLegacyCodes(id, allIds), ReminderIdentity.safeLegacyNotification(id, allIds))

    private suspend fun canDeliver(dose: DoseOccurrenceEntity): Boolean = ReminderEligibility.canNotify(
        dose, dao.medication(dose.medicationId), dao.plansForMedication(dose.medicationId),
        dao.phasesForMedication(dose.medicationId), DeviceSettings(context).isAlarmAssigned(dose.patientId))

    suspend fun restoreAlarms(patientId: String? = null) = db.withTransaction {
        // Riprendere la pulizia se il processo si era interrotto dopo il commit Room.
        clearReminders(dao.archivedOccurrenceIds())
        val now = System.currentTimeMillis()
        dao.upcomingDoses(now, now + 31L * 24 * 60 * 60_000).filter { patientId == null || it.patientId == patientId }.forEach { dose ->
            // La verifica e la programmazione nella stessa transazione impediscono
            // a una lista letta prima dell'archiviazione di ricreare sveglie obsolete.
            if (canDeliver(dose)) reminders.schedule(dose.id, dose.scheduledAtEpochMillis)
            else clearReminder(dose.id, dao.allOccurrenceIds())
        }
    }

    suspend fun handleReminder(id: String, action: String = ReminderIdentity.ALARM): Boolean = db.withTransaction {
        val dose = dao.dose(id) ?: return@withTransaction false
        if (!canDeliver(dose)) return@withTransaction false
        val now = System.currentTimeMillis()
        if (action == ReminderIdentity.ALARM && dose.scheduledAtEpochMillis > now) return@withTransaction false
        // Effetti sincroni dentro la transazione: se l'archiviazione segue questo
        // broadcast, la sua pulizia rimuove anche la notifica o il rinvio appena creati.
        when (action) {
            ReminderIdentity.ALARM -> {
                if (!dao.changeDoseStatus(id, DoseStatus.NOTIFIED, now)) return@withTransaction false
                reminders.show(id)
            }
            "taken", "skip" -> {
                val changed = if (action == "taken") dao.confirmDose(id, now)
                    else dao.changeDoseStatus(id, DoseStatus.SKIPPED, now)
                if (!changed) return@withTransaction false
                clearReminder(id, dao.allOccurrenceIds())
            }
            "snooze5", "snooze15", "snooze30", "continue" -> {
                if (dose.status != DoseStatus.NOTIFIED) return@withTransaction false
                val preferences = context.getSharedPreferences("snooze", Context.MODE_PRIVATE)
                val minutes = when (action) {
                    "snooze5" -> 5
                    "snooze30" -> 30
                    "continue" -> preferences.getInt(id, 15).takeIf { it in setOf(5, 15, 30) } ?: 15
                    else -> 15
                }
                if (!dao.changeDoseStatus(id, DoseStatus.SNOOZED, now)) return@withTransaction false
                val snoozedUntil = now + minutes * 60_000L
                // Persistenza dell'orario effettivo senza cambiare quello originale:
                // il riavvio puo' ripristinare anche un rinvio della dose gia' scaduta.
                dao.updateDose(dose.copy(status = DoseStatus.SNOOZED, scheduledAtEpochMillis = snoozedUntil))
                preferences.edit().putInt(id, minutes).apply()
                reminders.schedule(id, snoozedUntil)
                reminders.hide(id)
            }
            else -> return@withTransaction false
        }
        true
    }
    suspend fun reverseDose(id: String) = dao.reverseDose(id, System.currentTimeMillis())
    suspend fun recordAsNeeded(patientId: String, medicationId: String, amount: String, unit: StockUnit): Boolean = db.withTransaction {
        val decimal = amount.toBigDecimal()
        require(decimal > BigDecimal.ZERO) { "La quantità deve essere positiva" }
        // Anche un'azione dell'interfaccia rimasta in coda deve rispettare l'archiviazione.
        require(dao.medication(medicationId)?.let { it.active && it.patientId == patientId } == true) {
            "Il farmaco non è attivo per questo profilo"
        }
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
