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
    @Query("SELECT * FROM dose_occurrences WHERE id = :id") suspend fun dose(id: String): DoseOccurrenceEntity?
    @Query("SELECT * FROM dose_occurrences WHERE status IN ('PLANNED','NOTIFIED','SNOOZED') AND scheduledAtEpochMillis >= :from AND scheduledAtEpochMillis <= :until ORDER BY scheduledAtEpochMillis") suspend fun upcomingDoses(from: Long, until: Long): List<DoseOccurrenceEntity>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertPatients(items: List<PatientEntity>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertMedications(items: List<MedicationEntity>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertDoses(items: List<DoseOccurrenceEntity>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertMovement(item: StockMovementEntity): Long
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertEvent(item: DoseEventEntity): Long
    @Update suspend fun updateDose(item: DoseOccurrenceEntity)

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
}
