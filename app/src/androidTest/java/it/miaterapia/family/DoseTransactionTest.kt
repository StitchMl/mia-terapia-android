package it.miaterapia.family

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import it.miaterapia.family.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

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
}
