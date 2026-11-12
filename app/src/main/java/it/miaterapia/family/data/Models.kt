package it.miaterapia.family.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

enum class PatientType { HUMAN, ANIMAL }
enum class DoseStatus { PLANNED, NOTIFIED, SNOOZED, TAKEN, SKIPPED, UNCONFIRMED, CANCELLED }
enum class StockUnit { TABLET, CAPSULE, MILLILITER, DROP, DOSE, PUFF, GRAM, MANUAL }
enum class MovementType { INITIAL, REFILL, TAKE, MANUAL_ADJUST, REVERSAL, EXPIRED, DISCARDED }

@Entity(tableName = "patients")
data class PatientEntity(@PrimaryKey val id: String = UUID.randomUUID().toString(), val alias: String, val type: PatientType, val active: Boolean = true)

@Entity(tableName = "medications", indices = [Index("patientId")])
data class MedicationEntity(@PrimaryKey val id: String = UUID.randomUUID().toString(), val patientId: String, val name: String, val form: String, val strength: String?, val active: Boolean = true)

@Entity(tableName = "dose_occurrences", indices = [Index("patientId"), Index("medicationId")])
data class DoseOccurrenceEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val patientId: String,
    val medicationId: String,
    val scheduledAtEpochMillis: Long,
    val originalScheduledAtEpochMillis: Long = scheduledAtEpochMillis,
    val doseAmount: String,
    val doseUnit: StockUnit,
    val status: DoseStatus = DoseStatus.PLANNED,
    val confirmedAtEpochMillis: Long? = null
)

@Entity(tableName = "stock_movements", indices = [Index(value = ["causeOccurrenceId", "type"], unique = true)])
data class StockMovementEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val medicationId: String,
    val causeOccurrenceId: String?,
    val amountDecimal: String,
    val unit: StockUnit,
    val type: MovementType,
    val createdAtEpochMillis: Long = Instant.now().toEpochMilli()
) { fun amount(): BigDecimal = amountDecimal.toBigDecimal() }
