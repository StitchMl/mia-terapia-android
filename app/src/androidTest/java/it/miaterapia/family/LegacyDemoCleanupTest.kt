package it.miaterapia.family

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import it.miaterapia.family.data.*
import it.miaterapia.family.notifications.ReminderDelivery
import it.miaterapia.family.notifications.ReminderIdentity
import it.miaterapia.family.settings.DeviceSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class LegacyDemoCleanupTest {
    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var dao: TherapyDao
    private lateinit var repository: TherapyRepository
    private lateinit var delivery: RecordingDelivery
    private var demoWasAssigned = false

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        demoWasAssigned = DeviceSettings(context).isAlarmAssigned("patient-demo")
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        dao = db.therapyDao()
        delivery = RecordingDelivery()
        repository = TherapyRepository(context, db, delivery)
    }

    @After fun tearDown() {
        DeviceSettings(context).setAlarmAssigned("patient-demo", demoWasAssigned)
        db.close()
    }

    private suspend fun insertUnusedSample(withPlans: Boolean = false) {
        val scheduledAt = LocalDate.now().atTime(LocalTime.of(20, 0)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        dao.insertPatient(PatientEntity("patient-demo", "Matteo", PatientType.HUMAN))
        dao.insertMedication(MedicationEntity("med-demo", "patient-demo", "Farmaco dimostrativo", "Compressa", "10 mg"))
        dao.insertDose(DoseOccurrenceEntity("dose-demo", "patient-demo", "med-demo", scheduledAt,
            doseAmount = "1", doseUnit = StockUnit.TABLET))
        dao.insertMovement(StockMovementEntity("stock-demo", "med-demo", null, "30", StockUnit.TABLET, MovementType.INITIAL))
        if (withPlans) insertTechnicalPlans()
    }

    private suspend fun insertTechnicalPlans() {
        dao.insertPlan(TreatmentPlanEntity("plan-demo", "patient-demo", "med-demo", "1970-01-01", null, ZoneId.systemDefault().id))
        dao.insertPhase(SchedulePhaseEntity("phase-demo", "plan-demo", "1970-01-01", null,
            "20:00", doseAmount = "1", doseUnit = StockUnit.TABLET))
    }

    private suspend fun insertRealDose(id: String = "real-dose"): DoseOccurrenceEntity {
        dao.insertPatient(PatientEntity("real-patient", "Profilo reale", PatientType.HUMAN))
        dao.insertMedication(MedicationEntity("real-medication", "real-patient", "Terapia reale", "Compressa", "5 mg"))
        val dose = DoseOccurrenceEntity(id, "real-patient", "real-medication", System.currentTimeMillis() + 60_000,
            doseAmount = "1", doseUnit = StockUnit.TABLET)
        dao.insertDose(dose)
        return dose
    }

    private fun databaseRows(): Map<String, List<List<String?>>> =
        listOf("patients", "medications", "dose_occurrences", "dose_events", "stock_movements",
            "treatment_plans", "schedule_phases", "refill_requests", "care_grants").associateWith { table ->
            val rows = mutableListOf<List<String?>>()
            db.openHelper.readableDatabase.query("SELECT * FROM $table ORDER BY 1").use { cursor ->
                while (cursor.moveToNext()) rows += (0 until cursor.columnCount).map {
                    if (cursor.isNull(it)) null else cursor.getString(it)
                }
            }
            rows
        }

    @Test fun cleanInstallationStaysEmptyAfterRepeatedInitialization() = runBlocking {
        repeat(2) { assertEquals(LegacyDemoCleanupResult.ABSENT, repository.initializeLocalData()) }
        assertTrue(dao.patients().first().isEmpty())
        assertTrue(dao.medications().first().isEmpty())
        assertTrue(databaseRows().values.all { it.isEmpty() })
        assertTrue(delivery.scheduled.isEmpty())
    }

    @Test fun unusedAlphaSampleIsRemovedAndCleanupIsRepeatable() = runBlocking {
        insertUnusedSample()
        DeviceSettings(context).setAlarmAssigned("patient-demo", true)
        assertEquals(LegacyDemoCleanupResult.REMOVED, repository.initializeLocalData())
        assertTrue(databaseRows().values.all { it.isEmpty() })
        assertFalse(DeviceSettings(context).isAlarmAssigned("patient-demo"))
        assertEquals(ReminderIdentity.safeLegacyCodes("dose-demo", emptyList()), delivery.cleared.single().legacyCodes)
        assertTrue(delivery.cleared.single().legacyNotification)
        assertEquals(LegacyDemoCleanupResult.ABSENT, repository.initializeLocalData())
        assertEquals(2, delivery.cleared.size)
    }

    @Test fun interventionBuildTechnicalPlansAreRemovedWithUnusedSample() = runBlocking {
        insertUnusedSample(withPlans = true)
        assertEquals(LegacyDemoCleanupResult.REMOVED, repository.initializeLocalData())
        assertTrue(databaseRows().values.all { it.isEmpty() })
    }

    @Test fun confirmedLegacyDoseAndItsStockRemainIdentical() = runBlocking {
        insertUnusedSample(withPlans = true)
        assertTrue(dao.confirmDose("dose-demo", 1234L))
        val before = databaseRows()
        DeviceSettings(context).setAlarmAssigned("patient-demo", true)
        assertEquals(LegacyDemoCleanupResult.PRESERVED, repository.initializeLocalData())
        assertEquals(before, databaseRows())
        assertTrue(DeviceSettings(context).isAlarmAssigned("patient-demo"))
        assertTrue(delivery.cleared.isEmpty())
    }

    @Test fun renamedLegacyMedicationIsPreserved() = runBlocking {
        insertUnusedSample()
        db.openHelper.writableDatabase.execSQL("UPDATE medications SET name = 'Terapia personale' WHERE id = 'med-demo'")
        val before = databaseRows()
        assertEquals(LegacyDemoCleanupResult.PRESERVED, repository.initializeLocalData())
        assertEquals(before, databaseRows())
    }

    @Test fun additionalStockMovementPreventsDeletion() = runBlocking {
        insertUnusedSample()
        dao.insertMovement(StockMovementEntity(medicationId = "med-demo", causeOccurrenceId = null,
            amountDecimal = "10", unit = StockUnit.TABLET, type = MovementType.REFILL))
        val before = databaseRows()
        assertEquals(LegacyDemoCleanupResult.PRESERVED, repository.initializeLocalData())
        assertEquals(before, databaseRows())
    }

    @Test fun realMedicationOnLegacyPatientProtectsTheProfileAndAllReferences() = runBlocking {
        insertUnusedSample()
        dao.insertMedication(MedicationEntity("user-medication", "patient-demo", "Farmaco personale", "Compressa", null))
        val before = databaseRows()
        assertEquals(LegacyDemoCleanupResult.PRESERVED, repository.initializeLocalData())
        assertEquals(before, databaseRows())
    }

    @Test fun requestReferencingLegacyMedicationFromAnotherPatientIsPreserved() = runBlocking {
        insertUnusedSample()
        dao.insertRefillRequest(RefillRequestEntity(patientId = "other-patient", medicationIdsCsv = "other,med-demo",
            note = "Richiesta personale"))
        val before = databaseRows()
        assertEquals(LegacyDemoCleanupResult.PRESERVED, repository.initializeLocalData())
        assertEquals(before, databaseRows())
    }

    @Test fun customizedSchedulePhaseProtectsTheLegacyDataset() = runBlocking {
        insertUnusedSample(withPlans = true)
        dao.insertPhase(SchedulePhaseEntity("user-phase", "plan-demo", "2026-11-01", null,
            "08:00", doseAmount = "2", doseUnit = StockUnit.TABLET))
        val before = databaseRows()
        assertEquals(LegacyDemoCleanupResult.PRESERVED, repository.initializeLocalData())
        assertEquals(before, databaseRows())
    }

    @Test fun orphanTechnicalPlansCanBeCleanedWithoutChangingRealTherapy() = runBlocking {
        insertTechnicalPlans()
        val realDose = insertRealDose()
        assertEquals(LegacyDemoCleanupResult.REMOVED, repository.initializeLocalData())
        assertEquals(realDose, dao.dose(realDose.id))
        assertEquals(1, dao.patientCount(realDose.patientId))
        assertEquals(1, dao.medicationCount(realDose.medicationId))
        assertTrue(dao.legacyDemoPlans().isEmpty())
        assertTrue(dao.legacyDemoPhases().isEmpty())
    }

    @Test fun legacyCollisionDoesNotCancelARealDose() = runBlocking {
        insertUnusedSample()
        val realDose = insertRealDose("ePse-demo")
        assertEquals("dose-demo".hashCode(), realDose.id.hashCode())
        delivery.scheduled += realDose.id
        assertEquals(LegacyDemoCleanupResult.REMOVED, repository.initializeLocalData())
        val cleanup = delivery.cleared.single()
        assertTrue(cleanup.legacyCodes.isEmpty())
        assertFalse(cleanup.legacyNotification)
        assertTrue(realDose.id in delivery.scheduled)
        assertEquals(realDose, dao.dose(realDose.id))
    }

    @Test fun cleanupFailureRollsBackAllDemoRows() = runBlocking {
        insertUnusedSample(withPlans = true)
        val before = databaseRows()
        db.openHelper.writableDatabase.execSQL("""
            CREATE TRIGGER reject_demo_cleanup BEFORE DELETE ON medications
            WHEN OLD.id = 'med-demo'
            BEGIN SELECT RAISE(ABORT, 'Test rollback bonifica'); END
        """.trimIndent())
        try {
            repository.initializeLocalData()
            fail("Era atteso il rifiuto della bonifica")
        } catch (expected: android.database.sqlite.SQLiteConstraintException) {
            assertEquals(before, databaseRows())
            assertTrue(delivery.cleared.isEmpty())
        }
    }

    @Test fun preservingLegacyHistoryDoesNotPreventArchivedReminderRecovery() = runBlocking {
        insertUnusedSample()
        assertTrue(dao.confirmDose("dose-demo", 1234L))
        val realDose = insertRealDose()
        repository.archiveMedication(realDose.medicationId)
        delivery.scheduled += realDose.id
        delivery.cleared.clear()
        assertEquals(LegacyDemoCleanupResult.PRESERVED, repository.initializeLocalData())
        assertFalse(realDose.id in delivery.scheduled)
        assertEquals(listOf(realDose.id), delivery.cleared.map { it.id })
        assertEquals(DoseStatus.TAKEN, dao.dose("dose-demo")?.status)
    }

    private data class Cleanup(val id: String, val legacyCodes: Set<Int>, val legacyNotification: Boolean)

    private class RecordingDelivery : ReminderDelivery {
        val cleared = mutableListOf<Cleanup>()
        val scheduled = mutableSetOf<String>()
        override fun schedule(id: String, atMillis: Long): Boolean { scheduled += id; return true }
        override fun show(id: String) = Unit
        override fun hide(id: String) = Unit
        override fun clear(id: String, legacyCodes: Set<Int>, legacyNotification: Boolean) {
            cleared += Cleanup(id, legacyCodes, legacyNotification)
            scheduled -= id
        }
    }
}
