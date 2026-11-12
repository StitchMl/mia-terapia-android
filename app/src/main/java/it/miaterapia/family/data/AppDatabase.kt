package it.miaterapia.family.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [PatientEntity::class, MedicationEntity::class, DoseOccurrenceEntity::class, StockMovementEntity::class], version = 1, exportSchema = true)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun therapyDao(): TherapyDao
    companion object { fun create(context: Context) = Room.databaseBuilder(context, AppDatabase::class.java, "miaterapia.db").build() }
}
