package it.miaterapia.family

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import it.miaterapia.family.data.*
import it.miaterapia.family.notifications.ReminderDelivery
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class LegacyDatabaseUpgradeTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val noNotifications = object : ReminderDelivery {
        override fun schedule(id: String, atMillis: Long) = true
        override fun show(id: String) = Unit
        override fun hide(id: String) = Unit
        override fun clear(id: String, legacyCodes: Set<Int>, legacyNotification: Boolean) = Unit
    }

    @Test fun versionOneMigrationPreservesConfirmedLegacyDoseAndMovements() = runBlocking {
        val name = "upgrade-v1-${UUID.randomUUID()}.db"
        val path = context.getDatabasePath(name)
        path.parentFile?.mkdirs()
        val at = LocalDate.now().atTime(LocalTime.of(20, 0)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        try {
            SQLiteDatabase.openOrCreateDatabase(path, null).use { legacy ->
                legacySchemaVersionOne.forEach(legacy::execSQL)
                legacy.execSQL("INSERT INTO patients VALUES ('patient-demo','Matteo','HUMAN',1)")
                legacy.execSQL("INSERT INTO medications VALUES ('med-demo','patient-demo','Farmaco dimostrativo','Compressa','10 mg',1)")
                legacy.execSQL("INSERT INTO dose_occurrences VALUES ('dose-demo','patient-demo','med-demo',?,?,'1','TABLET','TAKEN',1234)", arrayOf(at, at))
                legacy.execSQL("INSERT INTO stock_movements VALUES ('stock-demo','med-demo',NULL,'30','TABLET','INITIAL',10)")
                legacy.execSQL("INSERT INTO stock_movements VALUES ('take-demo','med-demo','dose-demo','-1','TABLET','TAKE',20)")
                legacy.version = 1
            }
            val migrated = Room.databaseBuilder(context, AppDatabase::class.java, name)
                .addMigrations(AppDatabase.MIGRATION_1_2).build()
            try {
                val dao = migrated.therapyDao()
                val expected = DoseOccurrenceEntity("dose-demo", "patient-demo", "med-demo", at,
                    doseAmount = "1", doseUnit = StockUnit.TABLET, status = DoseStatus.TAKEN, confirmedAtEpochMillis = 1234)
                assertEquals(expected, dao.dose("dose-demo"))
                val before = dao.stockMovements().first()
                assertEquals(2, before.size)
                assertEquals(LegacyDemoCleanupResult.PRESERVED,
                    TherapyRepository(context, migrated, noNotifications).initializeLocalData())
                assertEquals(expected, dao.dose("dose-demo"))
                assertEquals(before, dao.stockMovements().first())
                assertEquals(2, migrated.openHelper.readableDatabase.version)
                assertFalse(dao.confirmDose("dose-demo", 5678L))
            } finally {
                migrated.close()
            }
        } finally {
            context.deleteDatabase(name)
        }
    }

    @Test fun existingVersionTwoFileCleansTechnicalPlansAndKeepsRealHistoryOnReopen() = runBlocking {
        val name = "upgrade-v2-${UUID.randomUUID()}.db"
        val at = LocalDate.now().atTime(LocalTime.of(20, 0)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        try {
            val previousBuild = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
            try {
                val dao = previousBuild.therapyDao()
                dao.insertPatient(PatientEntity("patient-demo", "Matteo", PatientType.HUMAN))
                dao.insertMedication(MedicationEntity("med-demo", "patient-demo", "Farmaco dimostrativo", "Compressa", "10 mg"))
                dao.insertDose(DoseOccurrenceEntity("dose-demo", "patient-demo", "med-demo", at, doseAmount = "1", doseUnit = StockUnit.TABLET))
                dao.insertMovement(StockMovementEntity("stock-demo", "med-demo", null, "30", StockUnit.TABLET, MovementType.INITIAL))
                dao.insertPlan(TreatmentPlanEntity("plan-demo", "patient-demo", "med-demo", "1970-01-01", null, ZoneId.systemDefault().id))
                dao.insertPhase(SchedulePhaseEntity("phase-demo", "plan-demo", "1970-01-01", null, "20:00", doseAmount = "1", doseUnit = StockUnit.TABLET))
                dao.insertPatient(PatientEntity("real-patient", "Profilo reale", PatientType.HUMAN))
                dao.insertMedication(MedicationEntity("real-med", "real-patient", "Farmaco personale", "Compressa", null))
                dao.insertDose(DoseOccurrenceEntity("real-dose", "real-patient", "real-med", at, doseAmount = "1", doseUnit = StockUnit.TABLET))
                assertTrue(dao.confirmDose("real-dose", 1234L))
            } finally {
                previousBuild.close()
            }

            val upgraded = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
            try {
                val dao = upgraded.therapyDao()
                val beforeDose = dao.dose("real-dose")
                val beforeMovements = dao.stockMovements().first().filter { it.medicationId == "real-med" }
                assertEquals(LegacyDemoCleanupResult.REMOVED,
                    TherapyRepository(context, upgraded, noNotifications).initializeLocalData())
                assertEquals(0, dao.patientCount("patient-demo"))
                assertEquals(0, dao.medicationCount("med-demo"))
                assertTrue(dao.legacyDemoPlans().isEmpty())
                assertTrue(dao.legacyDemoPhases().isEmpty())
                assertEquals(beforeDose, dao.dose("real-dose"))
                assertEquals(beforeMovements, dao.stockMovements().first())
                assertEquals(2, upgraded.openHelper.readableDatabase.version)
            } finally {
                upgraded.close()
            }
        } finally {
            context.deleteDatabase(name)
        }
    }

    // Lo schema v1 aveva queste quattro entita'. La migrazione di produzione
    // aggiunge le restanti tabelle e Room ne verifica la struttura all'apertura.
    private val legacySchemaVersionOne = listOf(
        "CREATE TABLE patients (id TEXT NOT NULL, alias TEXT NOT NULL, type TEXT NOT NULL, active INTEGER NOT NULL, PRIMARY KEY(id))",
        "CREATE TABLE medications (id TEXT NOT NULL, patientId TEXT NOT NULL, name TEXT NOT NULL, form TEXT NOT NULL, strength TEXT, active INTEGER NOT NULL, PRIMARY KEY(id))",
        "CREATE INDEX index_medications_patientId ON medications(patientId)",
        "CREATE TABLE dose_occurrences (id TEXT NOT NULL, patientId TEXT NOT NULL, medicationId TEXT NOT NULL, scheduledAtEpochMillis INTEGER NOT NULL, originalScheduledAtEpochMillis INTEGER NOT NULL, doseAmount TEXT NOT NULL, doseUnit TEXT NOT NULL, status TEXT NOT NULL, confirmedAtEpochMillis INTEGER, PRIMARY KEY(id))",
        "CREATE INDEX index_dose_occurrences_patientId ON dose_occurrences(patientId)",
        "CREATE INDEX index_dose_occurrences_medicationId ON dose_occurrences(medicationId)",
        "CREATE TABLE stock_movements (id TEXT NOT NULL, medicationId TEXT NOT NULL, causeOccurrenceId TEXT, amountDecimal TEXT NOT NULL, unit TEXT NOT NULL, type TEXT NOT NULL, createdAtEpochMillis INTEGER NOT NULL, PRIMARY KEY(id))",
        "CREATE UNIQUE INDEX index_stock_movements_causeOccurrenceId_type ON stock_movements(causeOccurrenceId,type)"
    )
}
