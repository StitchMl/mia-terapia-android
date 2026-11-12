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
        fun snooze(minutes: Int) = PendingIntent.getBroadcast(context, occurrenceId.hashCode() + minutes, Intent(context, DoseAlarmReceiver::class.java).putExtra("occurrenceId", occurrenceId).putExtra("action", "snooze$minutes"), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val previous = context.getSharedPreferences("snooze", Context.MODE_PRIVATE).getInt(occurrenceId, 0)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID).setSmallIcon(R.drawable.ic_app).setContentTitle("Promemoria terapia").setContentText(if (previous > 0) "Rinvio precedente: $previous min. Apri per altre scelte" else "Apri MiaTerapia per controllare la dose").setContentIntent(open).setAutoCancel(false).setPriority(NotificationCompat.PRIORITY_HIGH).setCategory(NotificationCompat.CATEGORY_ALARM).addAction(0, "Preso", taken).addAction(0, "5 min", snooze(5)).addAction(0, "15 min", snooze(15)).addAction(0, "30 min", snooze(30))
        if (previous > 0) {
            val keep = PendingIntent.getBroadcast(context, occurrenceId.hashCode() + 99, Intent(context, DoseAlarmReceiver::class.java).putExtra("occurrenceId", occurrenceId).putExtra("action", "continue"), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            builder.addAction(0, "Continua $previous min", keep)
        }
        val notification = builder.build()
        NotificationManagerCompat.from(context).notify(occurrenceId.hashCode(), notification)
    }
}
