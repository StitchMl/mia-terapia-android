package it.miaterapia.family.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

class AlarmScheduler(private val context: Context) {
    private val manager = context.getSystemService(AlarmManager::class.java)
    fun canScheduleExact() = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()
    fun schedule(occurrenceId: String, atMillis: Long): Boolean {
        if (!canScheduleExact()) return false
        manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending(occurrenceId))
        return true
    }
    fun cancel(occurrenceId: String) = manager.cancel(pending(occurrenceId))
    private fun pending(id: String) = PendingIntent.getBroadcast(context, id.hashCode(), Intent(context, DoseAlarmReceiver::class.java).putExtra("occurrenceId", id), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
}
