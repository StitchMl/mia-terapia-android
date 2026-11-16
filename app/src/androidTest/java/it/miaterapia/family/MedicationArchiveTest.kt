package it.miaterapia.family

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import it.miaterapia.family.data.*
import it.miaterapia.family.domain.ScheduleEngine
import it.miaterapia.family.notifications.ReminderDelivery
import it.miaterapia.family.settings.DeviceSettings
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class MedicationArchiveTest {
    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var dao: TherapyDao
    private lateinit var repository: TherapyRepository
    private lateinit var delivery: RecordingDelivery
    private val patientId = "archive-test-${UUID.randomUUID()}"
    private val zone = ZoneId.of("UTC")
    private val tomorrow = LocalDate.now(zone).plusDays(1)

    @Before fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        dao = db.therapyDao()
        delivery = RecordingDelivery()
        repository = TherapyRepository(context, db, delivery)
        dao.insertPatient(PatientEntity(patientId, "Test archiviazione", PatientType.HUMAN))
        DeviceSettings(context).setAlarmAssigned(patientId, true)
    }

    @After fun tearDown() = runBlocking {
        dao.allOccurrenceIds().forEach {
            context.getSharedPreferences("snooze", Context.MODE_PRIVATE).edit().remove(it).commit()
        }
        DeviceSettings(context).setAlarmAssigned(patientId, false)
        db.close()
    }

    private suspend fun medication(suffix: String): MedicationEntity {
        val med = MedicationEntity("$patientId-$suffix", patientId, suffix, "Compressa", null)
        dao.insertMedication(med)
        dao.insertPlan(TreatmentPlanEntity("plan-${med.id}", patientId, med.id, "2000-01-01", null, zone.id))
        dao.insertPhase(SchedulePhaseEntity("phase-${med.id}", "plan-${med.id}", "2000-01-01", null,
            "08:00", doseAmount = "1", doseUnit = StockUnit.TABLET))
        return med
    }

    private suspend fun occurrence(med: MedicationEntity, day: LocalDate = tomorrow,
        status: DoseStatus = DoseStatus.PLANNED): DoseOccurrenceEntity {
        val time = LocalTime.of(8, 0)
        val dose = DoseOccurrenceEntity(ScheduleEngine.stableOccurrenceId("phase-${med.id}", day, time, zone),
            patientId, med.id, day.atTime(time).atZone(zone).toInstant().toEpochMilli(),
            doseAmount = "1", doseUnit = StockUnit.TABLET, status = status)
        dao.insertDose(dose)
        return dose
    }

    private fun rows(table: String): List<List<String?>> {
        val result = mutableListOf<List<String?>>()
        db.openHelper.readableDatabase.query("SELECT * FROM $table ORDER BY 1").use { cursor ->
            while (cursor.moveToNext()) result += (0 until cursor.columnCount).map { if (cursor.isNull(it)) null else cursor.getString(it) }
        }
        return result
    }

    @Test fun t01ArchiveCancelsFutureOccurrencesAndDeactivatesPlans() = runBlocking {
        val med = medication("a")
        val doses = listOf(occurrence(med), occurrence(med, tomorrow.plusDays(1)))
        repository.archiveMedication(med.id)
        assertEquals(false, dao.medication(med.id)?.active)
        assertTrue(dao.plansForMedication(med.id).all { !it.active })
        doses.forEach { assertEquals(DoseStatus.CANCELLED, dao.dose(it.id)?.status) }
        assertEquals(2, dao.dosesForMedication(med.id).size)
        assertEquals(2, rows("dose_events").size)
        assertTrue(dao.upcomingDoses(0, Long.MAX_VALUE).isEmpty())
        assertFalse(dao.confirmDose(doses.first().id, System.currentTimeMillis()))
    }

    @Test fun t02ConfirmedDoseRemainsExactlyAsRecorded() = runBlocking {
        val med = medication("a")
        val dose = occurrence(med, tomorrow.minusDays(2))
        assertTrue(dao.confirmDose(dose.id, 1234L))
        val confirmed = dao.dose(dose.id)
        val events = rows("dose_events")
        val movements = rows("stock_movements")
        repository.archiveMedication(med.id)
        assertEquals(confirmed, dao.dose(dose.id))
        assertEquals(events, rows("dose_events"))
        assertEquals(movements, rows("stock_movements"))
    }

    @Test fun t03RepeatedArchiveIsIdempotentAndRepeatsAndroidCleanup() = runBlocking {
        val med = medication("a")
        val dose = occurrence(med)
        repository.archiveMedication(med.id)
        val events = rows("dose_events")
        val occurrences = rows("dose_occurrences")
        repository.archiveMedication(med.id)
        assertEquals(events, rows("dose_events"))
        assertEquals(occurrences, rows("dose_occurrences"))
        assertEquals(listOf(dose.id, dose.id), delivery.cleared)
        repository.archiveMedication("missing")
        assertEquals(2, delivery.cleared.size)
    }

    @Test fun t04ArchivingADoesNotChangeBOrItsReminders() = runBlocking {
        val a = medication("a")
        val b = medication("b")
        val doseA = occurrence(a)
        val doseB = occurrence(b)
        repository.restoreAlarms()
        delivery.shown += doseB.id
        val alarmsB = delivery.alarms[doseB.id]
        val plansB = dao.plansForMedication(b.id)
        repository.archiveMedication(a.id)
        assertEquals(doseB, dao.dose(doseB.id))
        assertEquals(true, dao.medication(b.id)?.active)
        assertEquals(plansB, dao.plansForMedication(b.id))
        assertEquals(alarmsB, delivery.alarms[doseB.id])
        assertTrue(doseB.id in delivery.shown)
        assertFalse(doseA.id in delivery.alarms)
        assertEquals(listOf(doseA.id), delivery.cleared)
    }

    @Test fun t05ResidualBroadcastAndActionsCannotChangeArchivedDose() = runBlocking {
        val med = medication("a")
        val dose = occurrence(med)
        repository.archiveMedication(med.id)
        val events = rows("dose_events")
        val occurrences = rows("dose_occurrences")
        listOf("alarm", "taken", "skip", "snooze5", "snooze15", "snooze30", "continue").forEach {
            assertFalse(repository.handleReminder(dose.id, it))
        }
        assertFalse(repository.handleReminder("missing"))
        assertEquals(events, rows("dose_events"))
        assertEquals(occurrences, rows("dose_occurrences"))
        assertTrue(delivery.shown.isEmpty())
        assertTrue(delivery.alarms.isEmpty())
        assertTrue(rows("stock_movements").isEmpty())
    }

    @Test fun t06RestoreExcludesArchivedInactivePlanAndUnassignedPatients() = runBlocking {
        val a = medication("a")
        val b = medication("b")
        val c = medication("c")
        val doseA = occurrence(a)
        val doseB = occurrence(b)
        val doseC = occurrence(c)
        repository.archiveMedication(a.id)
        dao.deactivatePlans(c.id)
        // Ricreare il repository simula la perdita dello stato in memoria dopo il riavvio.
        delivery = RecordingDelivery()
        repository = TherapyRepository(context, db, delivery)
        repository.restoreAlarms()
        assertEquals(setOf(doseB.id), delivery.alarms.keys)
        assertFalse(doseA.id in delivery.alarms)
        assertFalse(doseC.id in delivery.alarms)
        DeviceSettings(context).setAlarmAssigned(patientId, false)
        repository.restoreAlarms()
        assertTrue(delivery.alarms.isEmpty())
    }

    @Test fun t07HistoricalEventsAndAllStockMovementsArePreserved() = runBlocking {
        val med = medication("a")
        val taken = occurrence(med, tomorrow.minusDays(3))
        val skipped = occurrence(med, tomorrow.minusDays(2), DoseStatus.SKIPPED)
        val unconfirmed = occurrence(med, tomorrow.minusDays(1), DoseStatus.UNCONFIRMED)
        val future = occurrence(med)
        dao.insertMovement(StockMovementEntity(medicationId = med.id, causeOccurrenceId = null,
            amountDecimal = "30", unit = StockUnit.TABLET, type = MovementType.INITIAL))
        assertTrue(dao.confirmDose(taken.id, 1234L))
        dao.insertEvent(DoseEventEntity(occurrenceId = skipped.id, type = EventType.SKIPPED,
            occurredAtEpochMillis = 2345L, actorDeviceId = "test"))
        val oldEvents = rows("dose_events")
        val movements = rows("stock_movements")
        val confirmed = dao.dose(taken.id)
        repository.archiveMedication(med.id)
        assertTrue(rows("dose_events").containsAll(oldEvents))
        assertEquals(oldEvents.size + 1, rows("dose_events").size)
        assertEquals(movements, rows("stock_movements"))
        assertEquals(confirmed, dao.dose(taken.id))
        assertEquals(skipped, dao.dose(skipped.id))
        assertEquals(unconfirmed, dao.dose(unconfirmed.id))
        assertEquals(DoseStatus.CANCELLED, dao.dose(future.id)?.status)
    }

    @Test fun t08ArchiveClearsDisplayedNotificationAndOverdueSnooze() = runBlocking {
        val med = medication("a")
        val dose = occurrence(med, tomorrow.minusDays(2))
        assertTrue(repository.handleReminder(dose.id))
        assertTrue(dose.id in delivery.shown)
        assertTrue(repository.handleReminder(dose.id, "snooze5"))
        val snoozed = dao.dose(dose.id)!!
        assertEquals(dose.originalScheduledAtEpochMillis, snoozed.originalScheduledAtEpochMillis)
        assertTrue(snoozed.scheduledAtEpochMillis > System.currentTimeMillis())
        delivery.alarms.clear()
        repository.restoreAlarms()
        assertEquals(snoozed.scheduledAtEpochMillis, delivery.alarms[dose.id])
        // Simulare anche una notifica residua visibile durante il rinvio.
        delivery.shown += dose.id
        repository.archiveMedication(med.id)
        assertFalse(dose.id in delivery.shown)
        assertFalse(dose.id in delivery.alarms)
        assertEquals(DoseStatus.CANCELLED, dao.dose(dose.id)?.status)
        assertFalse(repository.handleReminder(dose.id, "continue"))
    }

    @Test fun receiverRejectsInvalidPlanAndUnassignedDeviceWithoutWritingEvents() = runBlocking {
        val med = medication("a")
        val dose = occurrence(med)
        DeviceSettings(context).setAlarmAssigned(patientId, false)
        assertFalse(repository.handleReminder(dose.id))
        DeviceSettings(context).setAlarmAssigned(patientId, true)
        dao.deactivatePlans(med.id)
        assertFalse(repository.handleReminder(dose.id))
        assertEquals(dose, dao.dose(dose.id))
        assertTrue(rows("dose_events").isEmpty())
    }

    @Test fun earlyBroadcastCannotInterruptAnActiveSnooze() = runBlocking {
        val med = medication("a")
        val dose = occurrence(med, tomorrow.minusDays(2))
        assertTrue(repository.handleReminder(dose.id))
        assertTrue(repository.handleReminder(dose.id, "snooze15"))
        val snoozed = dao.dose(dose.id)
        val events = rows("dose_events")
        assertFalse(repository.handleReminder(dose.id))
        assertEquals(snoozed, dao.dose(dose.id))
        assertEquals(events, rows("dose_events"))
        assertFalse(dose.id in delivery.shown)
    }

    @Test fun databaseFailureRollsBackTheWholeArchive() = runBlocking {
        val med = medication("a")
        val dose = occurrence(med)
        db.openHelper.writableDatabase.execSQL("""
            CREATE TRIGGER reject_archive BEFORE INSERT ON dose_events
            WHEN NEW.type = 'CANCELLED'
            BEGIN SELECT RAISE(ABORT, 'Test rollback archiviazione'); END
        """.trimIndent())
        try {
            repository.archiveMedication(med.id)
            fail("Era atteso il rifiuto della transazione")
        } catch (expected: android.database.sqlite.SQLiteConstraintException) {
            assertEquals(true, dao.medication(med.id)?.active)
            assertTrue(dao.plansForMedication(med.id).all { it.active })
            assertEquals(dose, dao.dose(dose.id))
            assertTrue(rows("dose_events").isEmpty())
            assertTrue(delivery.cleared.isEmpty())
        }
    }

    @Test fun restartingDemoInitializationDoesNotReactivateArchivedReminders() = runBlocking {
        repository.seedIfEmpty()
        repository.archiveMedication("med-demo")
        val events = rows("dose_events")
        repository.seedIfEmpty()
        assertEquals(false, dao.medication("med-demo")?.active)
        assertTrue(dao.plansForMedication("med-demo").all { !it.active })
        assertEquals(DoseStatus.CANCELLED, dao.dose("dose-demo")?.status)
        assertFalse("dose-demo" in delivery.alarms)
        assertEquals(events, rows("dose_events"))
    }

    @Test fun staleAsNeededActionCannotCreateAnOperationalDoseAfterArchive() = runBlocking {
        val med = medication("a")
        repository.archiveMedication(med.id)
        try {
            repository.recordAsNeeded(patientId, med.id, "1", StockUnit.TABLET)
            fail("Era atteso il rifiuto del farmaco archiviato")
        } catch (expected: IllegalArgumentException) {
            assertTrue(dao.dosesForMedication(med.id).isEmpty())
            assertTrue(rows("stock_movements").isEmpty())
            assertTrue(rows("dose_events").isEmpty())
        }
    }

    private class RecordingDelivery : ReminderDelivery {
        val alarms = mutableMapOf<String, Long>()
        val shown = mutableSetOf<String>()
        val cleared = mutableListOf<String>()
        override fun schedule(id: String, atMillis: Long): Boolean { alarms[id] = atMillis; return true }
        override fun show(id: String) { shown += id }
        override fun hide(id: String) { shown -= id }
        override fun clear(id: String, legacyCodes: Set<Int>, legacyNotification: Boolean) {
            cleared += id
            alarms.remove(id)
            shown.remove(id)
        }
    }
}
