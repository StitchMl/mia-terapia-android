package it.miaterapia.family.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import it.miaterapia.family.MiaTerapiaApplication
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
            "snooze15" -> AlarmScheduler(context).schedule(id, System.currentTimeMillis() + 15 * 60_000L)
            else -> NotificationHelper.show(context, id)
        }
    }
}
