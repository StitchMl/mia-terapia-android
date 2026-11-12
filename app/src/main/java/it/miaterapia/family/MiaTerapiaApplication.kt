package it.miaterapia.family

import android.app.Application
import it.miaterapia.family.data.AppDatabase
import it.miaterapia.family.data.TherapyRepository
import it.miaterapia.family.notifications.NotificationHelper

class MiaTerapiaApplication : Application() {
    val database by lazy { AppDatabase.create(this) }
    val repository by lazy { TherapyRepository(this, database) }
    override fun onCreate() { super.onCreate(); NotificationHelper.createChannel(this) }
}
