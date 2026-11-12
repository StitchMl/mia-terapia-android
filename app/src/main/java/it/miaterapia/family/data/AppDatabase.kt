package it.miaterapia.family.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [PatientEntity::class, MedicationEntity::class, DoseOccurrenceEntity::class, StockMovementEntity::class,
    TreatmentPlanEntity::class, SchedulePhaseEntity::class, DoseEventEntity::class, RefillRequestEntity::class,
    SyncOutboxEntity::class, DeviceEntity::class, CareGrantEntity::class], version = 2, exportSchema = true)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun therapyDao(): TherapyDao
    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `treatment_plans` (`id` TEXT NOT NULL, `patientId` TEXT NOT NULL, `medicationId` TEXT NOT NULL, `startDate` TEXT NOT NULL, `endDate` TEXT, `zoneId` TEXT NOT NULL, `active` INTEGER NOT NULL, `instructions` TEXT, PRIMARY KEY(`id`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_treatment_plans_patientId` ON `treatment_plans` (`patientId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_treatment_plans_medicationId` ON `treatment_plans` (`medicationId`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `schedule_phases` (`id` TEXT NOT NULL, `planId` TEXT NOT NULL, `startDate` TEXT NOT NULL, `endDate` TEXT, `timesCsv` TEXT NOT NULL, `daysMask` INTEGER NOT NULL, `doseAmount` TEXT NOT NULL, `doseUnit` TEXT NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_schedule_phases_planId` ON `schedule_phases` (`planId`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `dose_events` (`eventId` TEXT NOT NULL, `occurrenceId` TEXT NOT NULL, `type` TEXT NOT NULL, `occurredAtEpochMillis` INTEGER NOT NULL, `actorDeviceId` TEXT NOT NULL, `note` TEXT, `supersedesEventId` TEXT, PRIMARY KEY(`eventId`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_dose_events_occurrenceId` ON `dose_events` (`occurrenceId`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_dose_events_eventId` ON `dose_events` (`eventId`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `refill_requests` (`id` TEXT NOT NULL, `patientId` TEXT NOT NULL, `medicationIdsCsv` TEXT NOT NULL, `note` TEXT, `status` TEXT NOT NULL, `createdAtEpochMillis` INTEGER NOT NULL, `fulfilledAtEpochMillis` INTEGER, PRIMARY KEY(`id`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_refill_requests_patientId` ON `refill_requests` (`patientId`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `sync_outbox` (`id` TEXT NOT NULL, `eventId` TEXT NOT NULL, `encryptedPayload` TEXT NOT NULL, `status` TEXT NOT NULL, `attempts` INTEGER NOT NULL, `nextAttemptAtEpochMillis` INTEGER NOT NULL, `lastError` TEXT, PRIMARY KEY(`id`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_outbox_status` ON `sync_outbox` (`status`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_sync_outbox_eventId` ON `sync_outbox` (`eventId`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `devices` (`id` TEXT NOT NULL, `displayName` TEXT NOT NULL, `publicKey` TEXT NOT NULL, `createdAtEpochMillis` INTEGER NOT NULL, `revokedAtEpochMillis` INTEGER, PRIMARY KEY(`id`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `care_grants` (`id` TEXT NOT NULL, `patientId` TEXT NOT NULL, `deviceId` TEXT NOT NULL, `role` TEXT NOT NULL, `wrappedPatientKey` TEXT NOT NULL, `createdAtEpochMillis` INTEGER NOT NULL, `revokedAtEpochMillis` INTEGER, PRIMARY KEY(`id`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_care_grants_patientId` ON `care_grants` (`patientId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_care_grants_deviceId` ON `care_grants` (`deviceId`)")
            }
        }
        fun create(context: Context) = Room.databaseBuilder(context, AppDatabase::class.java, "miaterapia.db").addMigrations(MIGRATION_1_2).build()
    }
}
