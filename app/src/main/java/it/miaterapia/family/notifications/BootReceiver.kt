package it.miaterapia.family.notifications
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import it.miaterapia.family.MiaTerapiaApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in SUPPORTED_ACTIONS) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            val now = System.currentTimeMillis()
            val repository = (context.applicationContext as MiaTerapiaApplication).repository
            repository.upcomingDoses(now, now + 31L * 24 * 60 * 60_000).forEach {
                AlarmScheduler(context).schedule(it.id, it.scheduledAtEpochMillis)
            }
            pending.finish()
        }
    }

    private companion object {
        val SUPPORTED_ACTIONS = setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_TIME_CHANGED)
    }
}
