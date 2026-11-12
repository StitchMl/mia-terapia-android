package it.miaterapia.family.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import it.miaterapia.family.MiaTerapiaApplication
import it.miaterapia.family.data.DoseStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DoseAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra("occurrenceId") ?: return
        when (intent.getStringExtra("action")) {
            "taken" -> {
                val pending = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    (context.applicationContext as MiaTerapiaApplication).repository.confirmDose(id)
                    AlarmScheduler(context).cancel(id)
                    NotificationManagerCompat.from(context).cancel(id.hashCode())
                    pending.finish()
                }
            }
            "skip" -> update(context, id, DoseStatus.SKIPPED, cancel = true)
            "snooze5" -> snooze(context, id, 5)
            "snooze15" -> snooze(context, id, 15)
            "snooze30" -> snooze(context, id, 30)
            "continue" -> snooze(context, id, context.getSharedPreferences("snooze", Context.MODE_PRIVATE).getInt(id, 15))
            else -> {
                update(context, id, DoseStatus.NOTIFIED)
                NotificationHelper.show(context, id)
            }
        }
    }

    private fun snooze(context: Context, id: String, minutes: Int) {
        context.getSharedPreferences("snooze", Context.MODE_PRIVATE).edit().putInt(id, minutes).apply()
        update(context, id, DoseStatus.SNOOZED)
        AlarmScheduler(context).schedule(id, System.currentTimeMillis() + minutes * 60_000L)
        NotificationManagerCompat.from(context).cancel(id.hashCode())
    }

    private fun update(context: Context, id: String, status: DoseStatus, cancel: Boolean = false) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            (context.applicationContext as MiaTerapiaApplication).repository.changeDoseStatus(id, status)
            if (cancel) {
                AlarmScheduler(context).cancel(id)
                NotificationManagerCompat.from(context).cancel(id.hashCode())
            }
            pending.finish()
        }
    }
}
