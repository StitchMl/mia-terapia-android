package it.miaterapia.family.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TherapyDao {
    @Query("SELECT * FROM patients WHERE active = 1 ORDER BY alias") fun patients(): Flow<List<PatientEntity>>
    @Query("SELECT * FROM medications WHERE active = 1 ORDER BY name") fun medications(): Flow<List<MedicationEntity>>
    @Query("SELECT * FROM dose_occurrences WHERE status NOT IN ('CANCELLED') ORDER BY scheduledAtEpochMillis") fun doses(): Flow<List<DoseOccurrenceEntity>>
    @Query("SELECT * FROM stock_movements ORDER BY createdAtEpochMillis DESC") fun stockMovements(): Flow<List<StockMovementEntity>>
    @Query("SELECT * FROM refill_requests ORDER BY createdAtEpochMillis DESC") fun refillRequests(): Flow<List<RefillRequestEntity>>
    @Query("SELECT * FROM dose_occurrences WHERE id = :id") suspend fun dose(id: String): DoseOccurrenceEntity?
    @Query("SELECT d.* FROM dose_occurrences d INNER JOIN medications m ON m.id = d.medicationId WHERE m.active = 1 AND d.status IN ('PLANNED','NOTIFIED','SNOOZED') AND d.scheduledAtEpochMillis >= :from AND d.scheduledAtEpochMillis <= :until ORDER BY d.scheduledAtEpochMillis") suspend fun upcomingDoses(from: Long, until: Long): List<DoseOccurrenceEntity>
    @Query("SELECT * FROM medications WHERE id = :id") suspend fun medication(id: String): MedicationEntity?
    @Query("SELECT * FROM treatment_plans WHERE medicationId = :medicationId") suspend fun plansForMedication(medicationId: String): List<TreatmentPlanEntity>
    @Query("SELECT s.* FROM schedule_phases s INNER JOIN treatment_plans p ON p.id = s.planId WHERE p.medicationId = :medicationId") suspend fun phasesForMedication(medicationId: String): List<SchedulePhaseEntity>
    @Query("SELECT * FROM dose_occurrences WHERE medicationId = :medicationId") suspend fun dosesForMedication(medicationId: String): List<DoseOccurrenceEntity>
    @Query("SELECT id FROM dose_occurrences") suspend fun allOccurrenceIds(): List<String>
    @Query("SELECT d.id FROM dose_occurrences d INNER JOIN medications m ON m.id = d.medicationId WHERE m.active = 0") suspend fun archivedOccurrenceIds(): List<String>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertPatients(items: List<PatientEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertPatient(item: PatientEntity)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertMedications(items: List<MedicationEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertMedication(item: MedicationEntity)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertDoses(items: List<DoseOccurrenceEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertDose(item: DoseOccurrenceEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertPlan(item: TreatmentPlanEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertPhase(item: SchedulePhaseEntity)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertMovement(item: StockMovementEntity): Long
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertEvent(item: DoseEventEntity): Long
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertRefillRequest(item: RefillRequestEntity)
    @Update suspend fun updateDose(item: DoseOccurrenceEntity)
    @Query("SELECT COUNT(*) FROM medications WHERE patientId = :patientId AND active = 1") suspend fun activeMedicationCount(patientId: String): Int
    @Query("UPDATE medications SET active = 0 WHERE id = :id") suspend fun deactivateMedication(id: String)
    @Query("UPDATE treatment_plans SET active = 0 WHERE medicationId = :medicationId") suspend fun deactivatePlans(medicationId: String)
    @Query("SELECT COUNT(*) FROM patients WHERE id = :id") suspend fun patientCount(id: String): Int
    @Query("SELECT COUNT(*) FROM medications WHERE id = :id") suspend fun medicationCount(id: String): Int
    @Query("SELECT * FROM sync_outbox WHERE status IN ('PENDING','FAILED') AND nextAttemptAtEpochMillis <= :now ORDER BY nextAttemptAtEpochMillis LIMIT :limit") suspend fun pendingOutbox(now: Long, limit: Int = 20): List<SyncOutboxEntity>
    @Query("UPDATE sync_outbox SET status = :status, attempts = attempts + :attemptIncrement, nextAttemptAtEpochMillis = :nextAttempt, lastError = :error WHERE id = :id") suspend fun updateOutbox(id: String, status: OutboxStatus, attemptIncrement: Int, nextAttempt: Long, error: String?)
    @Query("SELECT COUNT(*) FROM stock_movements WHERE causeOccurrenceId = :occurrenceId AND type = :type") suspend fun movementCount(occurrenceId: String, type: MovementType): Int
    @Query("UPDATE refill_requests SET status = :status, fulfilledAtEpochMillis = :fulfilledAt WHERE id = :id") suspend fun updateRequestStatus(id: String, status: RequestStatus, fulfilledAt: Long?)

    @Query("SELECT * FROM patients WHERE id = 'patient-demo'") suspend fun legacyDemoPatient(): PatientEntity?
    @Query("SELECT * FROM stock_movements WHERE medicationId = 'med-demo' OR id = 'stock-demo'") suspend fun legacyDemoMovements(): List<StockMovementEntity>
    @Query("SELECT * FROM dose_events WHERE occurrenceId = 'dose-demo'") suspend fun legacyDemoEvents(): List<DoseEventEntity>
    @Query("SELECT * FROM treatment_plans WHERE medicationId = 'med-demo' OR id = 'plan-demo'") suspend fun legacyDemoPlans(): List<TreatmentPlanEntity>
    @Query("SELECT * FROM schedule_phases WHERE id = 'phase-demo' OR planId = 'plan-demo' OR planId IN (SELECT id FROM treatment_plans WHERE medicationId = 'med-demo')") suspend fun legacyDemoPhases(): List<SchedulePhaseEntity>
    @Query("""SELECT
        (SELECT COUNT(*) FROM medications WHERE patientId = 'patient-demo' AND id != 'med-demo') +
        (SELECT COUNT(*) FROM dose_occurrences WHERE (patientId = 'patient-demo' OR medicationId = 'med-demo') AND id != 'dose-demo') +
        (SELECT COUNT(*) FROM treatment_plans WHERE patientId = 'patient-demo' AND id != 'plan-demo') +
        (SELECT COUNT(*) FROM refill_requests WHERE patientId = 'patient-demo' OR INSTR(',' || medicationIdsCsv || ',', ',med-demo,') > 0) +
        (SELECT COUNT(*) FROM care_grants WHERE patientId = 'patient-demo')
    """) suspend fun legacyDemoOtherReferenceCount(): Int

    @Query("DELETE FROM stock_movements WHERE id = 'stock-demo'") suspend fun deleteLegacyDemoMovements()
    @Query("DELETE FROM schedule_phases WHERE id = 'phase-demo'") suspend fun deleteLegacyDemoPhase()
    @Query("DELETE FROM treatment_plans WHERE id = 'plan-demo'") suspend fun deleteLegacyDemoPlan()
    @Query("DELETE FROM dose_occurrences WHERE id = 'dose-demo'") suspend fun deleteLegacyDemoDose()
    @Query("DELETE FROM medications WHERE id = 'med-demo'") suspend fun deleteLegacyDemoMedication()
    @Query("DELETE FROM patients WHERE id = 'patient-demo'") suspend fun deleteLegacyDemoPatient()

    @Transaction
    suspend fun deleteLegacyDemoData(): LegacyDemoCleanupResult {
        // La verifica e le cancellazioni condividono la transazione: una conferma
        // concorrente non puo' trasformare un campione inutilizzato in storico perso.
        val result = LegacyDemoData(
            patient = legacyDemoPatient(), medication = medication("med-demo"), dose = dose("dose-demo"),
            movements = legacyDemoMovements(), events = legacyDemoEvents(), plans = legacyDemoPlans(),
            phases = legacyDemoPhases(), otherReferenceCount = legacyDemoOtherReferenceCount()
        ).cleanupDecision()
        if (result != LegacyDemoCleanupResult.REMOVED) return result
        // Nessun evento viene cancellato: anche un solo evento impedisce la bonifica.
        deleteLegacyDemoPhase()
        deleteLegacyDemoPlan()
        deleteLegacyDemoMovements()
        deleteLegacyDemoDose()
        deleteLegacyDemoMedication()
        deleteLegacyDemoPatient()
        return result
    }

    @Transaction
    suspend fun archiveMedication(id: String, at: Long): List<String> {
        if (medication(id) == null) return emptyList()
        val occurrences = dosesForMedication(id)
        deactivateMedication(id)
        deactivatePlans(id)
        // Anche i rinvii con orario originario passato devono perdere operativita'.
        // Gli eventi preesistenti e tutti i movimenti delle scorte restano immutati.
        occurrences.filter { it.status.isOperational() }.forEach {
            updateDose(it.copy(status = DoseStatus.CANCELLED))
            insertEvent(DoseEventEntity(occurrenceId = it.id, type = EventType.CANCELLED,
                occurredAtEpochMillis = at, actorDeviceId = "local", note = "Farmaco archiviato"))
        }
        // Restituire anche le dosi terminali permette di ripetere la pulizia Android.
        return occurrences.map { it.id }
    }

    @Transaction
    suspend fun confirmDose(id: String, confirmedAt: Long): Boolean {
        val current = dose(id) ?: return false
        if (!current.status.isOperational() || medication(current.medicationId)?.active != true) return false
        // L'indice univoco (occurrenceId, TAKE) rende la conferma idempotente
        // anche con doppio tap o due dispositivi che convergono sullo stesso evento.
        val inserted = insertMovement(StockMovementEntity(
            medicationId = current.medicationId,
            causeOccurrenceId = current.id,
            amountDecimal = current.doseAmount.toBigDecimal().negate().toPlainString(),
            unit = current.doseUnit,
            type = MovementType.TAKE
        ))
        if (inserted == -1L) return false
        updateDose(current.copy(status = DoseStatus.TAKEN, confirmedAtEpochMillis = confirmedAt))
        insertEvent(DoseEventEntity(occurrenceId = id, type = EventType.TAKEN, occurredAtEpochMillis = confirmedAt, actorDeviceId = "local"))
        return true
    }

    @Transaction
    suspend fun changeDoseStatus(id: String, status: DoseStatus, at: Long): Boolean {
        val current = dose(id) ?: return false
        if (current.status in setOf(DoseStatus.TAKEN, DoseStatus.SKIPPED, DoseStatus.CANCELLED)) return false
        updateDose(current.copy(status = status))
        val type = when (status) {
            DoseStatus.NOTIFIED -> EventType.NOTIFIED
            DoseStatus.SNOOZED -> EventType.SNOOZED
            DoseStatus.SKIPPED -> EventType.SKIPPED
            DoseStatus.UNCONFIRMED -> EventType.UNCONFIRMED
            DoseStatus.CANCELLED -> EventType.CANCELLED
            else -> return true
        }
        insertEvent(DoseEventEntity(occurrenceId = id, type = type, occurredAtEpochMillis = at, actorDeviceId = "local"))
        return true
    }

    @Transaction
    suspend fun reverseDose(id: String, at: Long): Boolean {
        val current = dose(id) ?: return false
        if (current.status != DoseStatus.TAKEN) return false
        val inserted = insertMovement(StockMovementEntity(
            medicationId = current.medicationId,
            causeOccurrenceId = current.id,
            amountDecimal = current.doseAmount,
            unit = current.doseUnit,
            type = MovementType.REVERSAL
        ))
        if (inserted == -1L) return false
        updateDose(current.copy(status = DoseStatus.UNCONFIRMED, confirmedAtEpochMillis = null))
        insertEvent(DoseEventEntity(occurrenceId = id, type = EventType.CORRECTION, occurredAtEpochMillis = at, actorDeviceId = "local", note = "Conferma rettificata"))
        return true
    }
}
