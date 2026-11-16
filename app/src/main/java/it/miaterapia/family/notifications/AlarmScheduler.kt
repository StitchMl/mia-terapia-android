package it.miaterapia.family.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.net.Uri

/** Adattatore di [AlarmManager] che non promette puntualita' quando manca il permesso esatto. */
class AlarmScheduler(private val context: Context) {
    private val manager = context.getSystemService(AlarmManager::class.java)
    fun canScheduleExact() = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()
    fun schedule(occurrenceId: String, atMillis: Long): Boolean {
        if (!canScheduleExact()) return false
        manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending(occurrenceId))
        return true
    }
    // Dose e azione distinguono i nuovi PendingIntent; i codici precedenti sono
    // cancellabili solo dopo aver escluso collisioni con altre dosi nel repository.
    fun cancel(occurrenceId: String, legacyRequestCodes: Set<Int> = emptySet()) {
        (listOf(ReminderIdentity.ALARM) + ReminderIdentity.actions).forEach { action ->
            PendingIntent.getBroadcast(context, 0, intent(context, occurrenceId, action),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)?.let {
                manager.cancel(it)
                it.cancel()
            }
        }
        legacyRequestCodes.forEach { code ->
            PendingIntent.getBroadcast(context, code, Intent(context, DoseAlarmReceiver::class.java),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)?.let {
                manager.cancel(it)
                it.cancel()
            }
        }
    }

    private fun pending(id: String) = PendingIntent.getBroadcast(context, 0, intent(context, id), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    companion object {
        fun intent(context: Context, id: String, action: String = ReminderIdentity.ALARM) =
            Intent(context, DoseAlarmReceiver::class.java)
                .setData(Uri.parse(ReminderIdentity.uri(id, action)))
                .putExtra("occurrenceId", id).putExtra("action", action)
    }
}
