package it.miaterapia.family.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import it.miaterapia.family.MiaTerapiaApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DoseAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra("occurrenceId") ?: return
        val action = intent.getStringExtra("action") ?: ReminderIdentity.ALARM
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                (context.applicationContext as MiaTerapiaApplication).repository.handleReminder(id, action)
            } finally {
                pending.finish()
            }
        }
    }
}
