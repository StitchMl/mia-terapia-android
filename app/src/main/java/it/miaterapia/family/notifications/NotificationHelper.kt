package it.miaterapia.family.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import it.miaterapia.family.MainActivity
import it.miaterapia.family.R

object NotificationHelper {
    const val CHANNEL_ID = "dose_reminders"
    fun createChannel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL_ID, "Promemoria terapia", NotificationManager.IMPORTANCE_HIGH).apply { description = "Avvisi locali per le dosi programmate" })
    }
    fun show(context: Context, occurrenceId: String) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val open = PendingIntent.getActivity(context, occurrenceId.hashCode(), Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val taken = PendingIntent.getBroadcast(context, occurrenceId.hashCode() + 1, Intent(context, DoseAlarmReceiver::class.java).putExtra("occurrenceId", occurrenceId).putExtra("action", "taken"), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val snooze = PendingIntent.getBroadcast(context, occurrenceId.hashCode() + 2, Intent(context, DoseAlarmReceiver::class.java).putExtra("occurrenceId", occurrenceId).putExtra("action", "snooze15"), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID).setSmallIcon(R.drawable.ic_app).setContentTitle("Promemoria terapia").setContentText("Apri MiaTerapia per controllare la dose").setContentIntent(open).setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_HIGH).addAction(0, "Preso", taken).addAction(0, "Posponi 15 min", snooze).build()
        NotificationManagerCompat.from(context).notify(occurrenceId.hashCode(), notification)
    }
}
