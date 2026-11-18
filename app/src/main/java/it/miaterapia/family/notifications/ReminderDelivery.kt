package it.miaterapia.family.notifications

import android.content.Context

// Confine tra la transazione applicativa e gli effetti Android, sostituibile nei test Room.
interface ReminderDelivery {
    fun schedule(id: String, atMillis: Long): Boolean
    fun show(id: String)
    fun hide(id: String)
    fun clear(id: String, legacyCodes: Set<Int>, legacyNotification: Boolean)
}

class AndroidReminderDelivery(private val context: Context) : ReminderDelivery {
    override fun schedule(id: String, atMillis: Long) = AlarmScheduler(context).schedule(id, atMillis)
    override fun show(id: String) = NotificationHelper.show(context, id)
    override fun hide(id: String) = NotificationHelper.cancel(context, id)
    override fun clear(id: String, legacyCodes: Set<Int>, legacyNotification: Boolean) {
        AlarmScheduler(context).cancel(id, legacyCodes)
        NotificationHelper.cancel(context, id, legacyNotification)
        context.getSharedPreferences("snooze", Context.MODE_PRIVATE).edit().remove(id).apply()
    }
}
