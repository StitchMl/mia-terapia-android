package it.miaterapia.family

import android.app.Activity
import android.app.NotificationManager
import android.app.Notification
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import it.miaterapia.family.data.*
import it.miaterapia.family.domain.ScheduleEngine
import it.miaterapia.family.domain.ScheduleRule
import it.miaterapia.family.notifications.*
import it.miaterapia.family.settings.DeviceSettings
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class ReminderAndroidTest {
    private val context = ApplicationProvider.getApplicationContext<MiaTerapiaApplication>()
    private val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    private fun pending(id: String, action: String = ReminderIdentity.ALARM) =
        PendingIntent.getBroadcast(context, 0, AlarmScheduler.intent(context, id, action), flags)

    @Test fun collidingDoseHashesKeepSeparatePendingIntentsAndCancellation() {
        val prefix = UUID.randomUUID().toString()
        val a = "$prefix-Aa"
        val b = "$prefix-BB"
        assertEquals(a.hashCode(), b.hashCode())
        val alarmA = pending(a)
        val alarmB = pending(b)
        val snoozeA = pending(a, "snooze5")
        val snoozeB = pending(b, "snooze5")
        try {
            assertNotEquals(alarmA, alarmB)
            assertNotEquals(alarmA, snoozeA)
            assertNotEquals(snoozeA, snoozeB)
            AlarmScheduler(context).cancel(a)
            assertNull(PendingIntent.getBroadcast(context, 0, AlarmScheduler.intent(context, a),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE))
            assertNull(PendingIntent.getBroadcast(context, 0, AlarmScheduler.intent(context, a, "snooze5"),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE))
            assertEquals(alarmB, PendingIntent.getBroadcast(context, 0, AlarmScheduler.intent(context, b),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE))
            assertEquals(snoozeB, PendingIntent.getBroadcast(context, 0, AlarmScheduler.intent(context, b, "snooze5"),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE))
        } finally {
            alarmA.cancel(); alarmB.cancel(); snoozeA.cancel(); snoozeB.cancel()
        }
    }

    @Test fun t08AndroidCleanupRemovesLegacyPendingIntentAndSnoozePreference() {
        val id = "legacy-${UUID.randomUUID()}"
        val preferences = context.getSharedPreferences("snooze", Context.MODE_PRIVATE)
        val legacy = PendingIntent.getBroadcast(context, id.hashCode(),
            Intent(context, DoseAlarmReceiver::class.java).putExtra("occurrenceId", id), flags)
        preferences.edit().putInt(id, 15).commit()
        try {
            AndroidReminderDelivery(context).clear(id, ReminderIdentity.safeLegacyCodes(id, listOf(id)), true)
            assertNull(PendingIntent.getBroadcast(context, id.hashCode(), Intent(context, DoseAlarmReceiver::class.java),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE))
            assertFalse(preferences.contains(id))
        } finally {
            legacy.cancel()
            preferences.edit().remove(id).commit()
        }
    }

    @Test fun legacyHashCollisionPreservesTheOtherPendingIntent() {
        val prefix = UUID.randomUUID().toString()
        val archivedId = "$prefix-Aa"
        val otherId = "$prefix-BB"
        val legacyOther = PendingIntent.getBroadcast(context, otherId.hashCode(),
            Intent(context, DoseAlarmReceiver::class.java).putExtra("occurrenceId", otherId), flags)
        val newOther = pending(otherId)
        try {
            val codes = ReminderIdentity.safeLegacyCodes(archivedId, listOf(archivedId, otherId))
            AndroidReminderDelivery(context).clear(archivedId, codes,
                ReminderIdentity.safeLegacyNotification(archivedId, listOf(archivedId, otherId)))
            assertEquals(legacyOther, PendingIntent.getBroadcast(context, otherId.hashCode(),
                Intent(context, DoseAlarmReceiver::class.java), PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE))
            assertEquals(newOther, PendingIntent.getBroadcast(context, 0, AlarmScheduler.intent(context, otherId),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE))
        } finally {
            legacyOther.cancel()
            newOther.cancel()
        }
    }

    @Test fun legacyAndTaggedNotificationsAreRemovedTogether() {
        val manager = context.getSystemService(NotificationManager::class.java)
        assumeTrue(manager.areNotificationsEnabled())
        val id = "notification-${UUID.randomUUID()}"
        NotificationHelper.createChannel(context)
        try {
            manager.notify(id.hashCode(), Notification.Builder(context, NotificationHelper.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_app).setContentTitle("Promemoria precedente").build())
            NotificationHelper.show(context, id)
            eventually {
                manager.activeNotifications.any { it.tag == id } &&
                    manager.activeNotifications.any { it.tag == null && it.id == id.hashCode() }
            }
            AndroidReminderDelivery(context).clear(id, ReminderIdentity.safeLegacyCodes(id, listOf(id)), true)
            eventually {
                manager.activeNotifications.none { it.tag == id || (it.tag == null && it.id == id.hashCode()) }
            }
        } finally {
            AndroidReminderDelivery(context).clear(id, emptySet(), true)
        }
    }

    @Test fun t08NotificationCancellationKeepsAnotherDoseWithTheSameHash() {
        val manager = context.getSystemService(NotificationManager::class.java)
        // Il dispositivo di test deve avere il permesso notifiche gia' concesso.
        assumeTrue(manager.areNotificationsEnabled())
        val prefix = UUID.randomUUID().toString()
        val a = "$prefix-Aa"
        val b = "$prefix-BB"
        NotificationHelper.createChannel(context)
        try {
            NotificationHelper.show(context, a)
            NotificationHelper.show(context, b)
            eventually { manager.activeNotifications.any { it.tag == a } && manager.activeNotifications.any { it.tag == b } }
            AndroidReminderDelivery(context).clear(a, emptySet(), false)
            eventually { manager.activeNotifications.none { it.tag == a } }
            assertTrue(manager.activeNotifications.any { it.tag == b })
        } finally {
            AndroidReminderDelivery(context).clear(a, emptySet(), false)
            AndroidReminderDelivery(context).clear(b, emptySet(), false)
        }
    }

    @Test fun t05ActualResidualBroadcastLeavesArchivedDoseUntouched() = runBlocking {
        val dao = context.database.therapyDao()
        val suffix = UUID.randomUUID().toString()
        val patient = PatientEntity("receiver-p-$suffix", "Test receiver", PatientType.HUMAN)
        val medication = MedicationEntity("receiver-m-$suffix", patient.id, "Test", "Compressa", null)
        val dose = DoseOccurrenceEntity("receiver-d-$suffix", patient.id, medication.id,
            System.currentTimeMillis() + 60_000, doseAmount = "1", doseUnit = StockUnit.TABLET)
        dao.insertPatient(patient)
        dao.insertMedication(medication)
        dao.insertDose(dose)
        try {
            context.repository.archiveMedication(medication.id)
            val archived = dao.dose(dose.id)
            for (action in listOf(ReminderIdentity.ALARM, "taken", "snooze15")) {
                val completed = CountDownLatch(1)
                context.sendOrderedBroadcast(AlarmScheduler.intent(context, dose.id, action), null,
                    object : BroadcastReceiver() {
                        override fun onReceive(context: Context, intent: Intent) { completed.countDown() }
                    }, null, Activity.RESULT_OK, null, null)
                assertTrue("Broadcast non completato", completed.await(5, TimeUnit.SECONDS))
                assertEquals(archived, dao.dose(dose.id))
            }
            assertEquals(0, dao.movementCount(dose.id, MovementType.TAKE))
            assertTrue(context.getSystemService(NotificationManager::class.java).activeNotifications.none { it.tag == dose.id })
        } finally {
            // Rimuovere esclusivamente i dati UUID creati da questo test.
            context.database.openHelper.writableDatabase.apply {
                execSQL("DELETE FROM dose_events WHERE occurrenceId = ?", arrayOf(dose.id))
                execSQL("DELETE FROM stock_movements WHERE medicationId = ?", arrayOf(medication.id))
                execSQL("DELETE FROM dose_occurrences WHERE id = ?", arrayOf(dose.id))
                execSQL("DELETE FROM medications WHERE id = ?", arrayOf(medication.id))
                execSQL("DELETE FROM patients WHERE id = ?", arrayOf(patient.id))
            }
        }
    }

    @Test fun convergingSpringDosesKeepSeparateRemindersAndConfirmations() = runBlocking {
        val manager = context.getSystemService(NotificationManager::class.java)
        assumeTrue(manager.areNotificationsEnabled())
        val settings = DeviceSettings(context)
        val patient = PatientEntity(alias = "Test DST", type = PatientType.HUMAN)
        val medication = MedicationEntity(patientId = patient.id, name = "Test", form = "Compressa", strength = null)
        val date = LocalDate.of(2026, 3, 29)
        val zone = ZoneId.of("Europe/Rome")
        val plan = TreatmentPlanEntity(patientId = patient.id, medicationId = medication.id,
            startDate = date.toString(), endDate = date.toString(), zoneId = zone.id)
        val phase = SchedulePhaseEntity(planId = plan.id, startDate = date.toString(),
            endDate = date.toString(), timesCsv = "02:30,03:30", doseAmount = "1", doseUnit = StockUnit.TABLET)
        val times = listOf(LocalTime.of(2, 30), LocalTime.of(3, 30))
        val (first, second) = ScheduleEngine.generate(
            ScheduleRule(date, date, times, zoneId = zone, doseAmount = BigDecimal.ONE,
                doseUnit = phase.doseUnit, scheduleId = phase.id), date, windowDays = 1
        ).map {
            DoseOccurrenceEntity(it.occurrenceId, patient.id, medication.id,
                it.scheduledAt.toInstant().toEpochMilli(), doseAmount = "1", doseUnit = phase.doseUnit)
        }

        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val dao = db.therapyDao()
        val repository = TherapyRepository(context, db)
        NotificationHelper.createChannel(context)
        try {
            settings.setAlarmAssigned(patient.id, true)
            dao.insertPatient(patient)
            dao.insertMedication(medication)
            dao.insertPlan(plan)
            dao.insertPhase(phase)
            dao.insertDose(first)
            dao.insertDose(second)

            assertEquals(first.scheduledAtEpochMillis, second.scheduledAtEpochMillis)
            assertNotEquals(pending(first.id), pending(second.id))
            assertNotEquals(pending(first.id, "taken"), pending(second.id, "taken"))
            assertTrue(repository.handleReminder(first.id))
            assertTrue(repository.handleReminder(second.id))
            eventually { manager.activeNotifications.map { it.tag }.containsAll(listOf(first.id, second.id)) }

            assertTrue(repository.handleReminder(first.id, "taken"))
            assertFalse(repository.handleReminder(first.id, "taken"))
            assertEquals(DoseStatus.TAKEN, dao.dose(first.id)?.status)
            assertEquals(DoseStatus.NOTIFIED, dao.dose(second.id)?.status)
            assertEquals(1, dao.movementCount(first.id, MovementType.TAKE))
            assertEquals(0, dao.movementCount(second.id, MovementType.TAKE))
            eventually { manager.activeNotifications.none { it.tag == first.id } }
            assertTrue(manager.activeNotifications.any { it.tag == second.id })

            assertTrue(repository.handleReminder(second.id, "taken"))
            assertFalse(repository.handleReminder(second.id, "taken"))
            assertEquals(DoseStatus.TAKEN, dao.dose(second.id)?.status)
            assertEquals(1, dao.movementCount(second.id, MovementType.TAKE))
        } finally {
            for (dose in listOf(first, second)) {
                AndroidReminderDelivery(context).clear(dose.id, emptySet(), false)
            }
            settings.setAlarmAssigned(patient.id, false)
            db.close()
        }
    }

    private fun eventually(condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
        while (!condition() && System.nanoTime() < deadline) Thread.sleep(20)
        assertTrue(condition())
    }
}
