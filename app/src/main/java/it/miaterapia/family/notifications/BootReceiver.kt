package it.miaterapia.family.notifications
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Rebuild is intentionally deferred until schedules persist beyond the prototype.
    }
}
