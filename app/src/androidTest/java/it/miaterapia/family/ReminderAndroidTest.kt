package it.miaterapia.family

import android.app.Activity
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import it.miaterapia.family.data.*
import it.miaterapia.family.notifications.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
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

    private fun eventually(condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
        while (!condition() && System.nanoTime() < deadline) Thread.sleep(20)
        assertTrue(condition())
    }
}
