package it.miaterapia.family

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import it.miaterapia.family.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class DoseTransactionTest {
    @Test fun t06DoubleConfirmationDeductsOnceAndT07ReversalRestores() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<MiaTerapiaApplication>()
        val dao = app.database.therapyDao()
        val suffix = UUID.randomUUID().toString()
        val patient = PatientEntity("patient-$suffix", "Test", PatientType.HUMAN)
        val medication = MedicationEntity("med-$suffix", patient.id, "Farmaco test", "Compressa", null)
        val dose = DoseOccurrenceEntity("dose-$suffix", patient.id, medication.id, System.currentTimeMillis(), doseAmount = "0.5", doseUnit = StockUnit.TABLET)
        dao.insertPatient(patient); dao.insertMedication(medication); dao.insertDose(dose)
        assertTrue(dao.confirmDose(dose.id, System.currentTimeMillis()))
        assertFalse(dao.confirmDose(dose.id, System.currentTimeMillis()))
        assertEquals(1, dao.movementCount(dose.id, MovementType.TAKE))
        assertTrue(dao.reverseDose(dose.id, System.currentTimeMillis()))
        assertFalse(dao.reverseDose(dose.id, System.currentTimeMillis()))
        assertEquals(1, dao.movementCount(dose.id, MovementType.REVERSAL))
        assertEquals(DoseStatus.UNCONFIRMED, dao.dose(dose.id)?.status)
    }

    @Test fun publishedBuildRemovesLegacyDemoRows() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<MiaTerapiaApplication>()
        val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).build()
        try {
            val dao = db.therapyDao()
            // Il solo ID non autorizza la cancellazione: riprodurre il campione originale.
            val patient = PatientEntity("patient-demo", "Matteo", PatientType.HUMAN)
            val medication = MedicationEntity("med-demo", patient.id, "Farmaco dimostrativo", "Compressa", "10 mg")
            val scheduledAt = LocalDate.now().atTime(LocalTime.of(20, 0)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val dose = DoseOccurrenceEntity("dose-demo", patient.id, medication.id, scheduledAt, doseAmount = "1", doseUnit = StockUnit.TABLET)
            dao.insertPatients(listOf(patient))
            dao.insertMedications(listOf(medication))
            dao.insertDoses(listOf(dose))
            dao.insertMovement(StockMovementEntity("stock-demo", medication.id, null, "30", StockUnit.TABLET, MovementType.INITIAL))

            assertEquals(LegacyDemoCleanupResult.REMOVED, dao.deleteLegacyDemoData())

            assertEquals(0, dao.patientCount(patient.id))
            assertEquals(0, dao.medicationCount(medication.id))
            assertNull(dao.dose(dose.id))
        } finally {
            db.close()
        }
    }
}
