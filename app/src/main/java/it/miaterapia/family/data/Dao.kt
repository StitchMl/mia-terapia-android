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
    @Query("SELECT * FROM dose_occurrences WHERE status IN ('PLANNED','NOTIFIED','SNOOZED') AND scheduledAtEpochMillis >= :from AND scheduledAtEpochMillis <= :until ORDER BY scheduledAtEpochMillis") suspend fun upcomingDoses(from: Long, until: Long): List<DoseOccurrenceEntity>
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
    @Query("UPDATE medications SET active = 0 WHERE id = :id") suspend fun archiveMedication(id: String)
    @Query("SELECT * FROM sync_outbox WHERE status IN ('PENDING','FAILED') AND nextAttemptAtEpochMillis <= :now ORDER BY nextAttemptAtEpochMillis LIMIT :limit") suspend fun pendingOutbox(now: Long, limit: Int = 20): List<SyncOutboxEntity>
    @Query("UPDATE sync_outbox SET status = :status, attempts = attempts + :attemptIncrement, nextAttemptAtEpochMillis = :nextAttempt, lastError = :error WHERE id = :id") suspend fun updateOutbox(id: String, status: OutboxStatus, attemptIncrement: Int, nextAttempt: Long, error: String?)
    @Query("SELECT COUNT(*) FROM stock_movements WHERE causeOccurrenceId = :occurrenceId AND type = :type") suspend fun movementCount(occurrenceId: String, type: MovementType): Int
    @Query("UPDATE refill_requests SET status = :status, fulfilledAtEpochMillis = :fulfilledAt WHERE id = :id") suspend fun updateRequestStatus(id: String, status: RequestStatus, fulfilledAt: Long?)

    @Transaction
    suspend fun confirmDose(id: String, confirmedAt: Long): Boolean {
        val current = dose(id) ?: return false
        if (current.status == DoseStatus.TAKEN) return false
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
