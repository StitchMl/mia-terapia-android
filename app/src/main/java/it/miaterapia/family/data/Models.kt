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
enum class EventType { PLANNED, NOTIFIED, SNOOZED, TAKEN, SKIPPED, UNCONFIRMED, CANCELLED, CORRECTION }
enum class RequestStatus { DRAFT, READY, SHARED, FULFILLED, CANCELLED }
enum class OutboxStatus { PENDING, UPLOADING, SENT, FAILED }
enum class GrantRole { PATIENT, CAREGIVER }

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

@Entity(tableName = "treatment_plans", indices = [Index("patientId"), Index("medicationId")])
data class TreatmentPlanEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val patientId: String,
    val medicationId: String,
    val startDate: String,
    val endDate: String?,
    val zoneId: String,
    val active: Boolean = true,
    val instructions: String? = null
)

@Entity(tableName = "schedule_phases", indices = [Index("planId")])
data class SchedulePhaseEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val planId: String,
    val startDate: String,
    val endDate: String?,
    val timesCsv: String,
    val daysMask: Int = 127,
    val doseAmount: String,
    val doseUnit: StockUnit
)

@Entity(tableName = "dose_events", indices = [Index("occurrenceId"), Index(value = ["eventId"], unique = true)])
data class DoseEventEntity(
    @PrimaryKey val eventId: String = UUID.randomUUID().toString(),
    val occurrenceId: String,
    val type: EventType,
    val occurredAtEpochMillis: Long,
    val actorDeviceId: String,
    val note: String? = null,
    val supersedesEventId: String? = null
)

@Entity(tableName = "refill_requests", indices = [Index("patientId")])
data class RefillRequestEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val patientId: String,
    val medicationIdsCsv: String,
    val note: String?,
    val status: RequestStatus = RequestStatus.DRAFT,
    val createdAtEpochMillis: Long = Instant.now().toEpochMilli(),
    val fulfilledAtEpochMillis: Long? = null
)

@Entity(tableName = "sync_outbox", indices = [Index("status"), Index(value = ["eventId"], unique = true)])
data class SyncOutboxEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val eventId: String,
    val encryptedPayload: String,
    val status: OutboxStatus = OutboxStatus.PENDING,
    val attempts: Int = 0,
    val nextAttemptAtEpochMillis: Long = 0,
    val lastError: String? = null
)

@Entity(tableName = "devices")
data class DeviceEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val publicKey: String,
    val createdAtEpochMillis: Long = Instant.now().toEpochMilli(),
    val revokedAtEpochMillis: Long? = null
)

@Entity(tableName = "care_grants", indices = [Index("patientId"), Index("deviceId")])
data class CareGrantEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val patientId: String,
    val deviceId: String,
    val role: GrantRole,
    val wrappedPatientKey: String,
    val createdAtEpochMillis: Long = Instant.now().toEpochMilli(),
    val revokedAtEpochMillis: Long? = null
)
