package it.miaterapia.family.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.math.BigDecimal
import java.util.UUID
import it.miaterapia.family.domain.TherapyLimits

data class TherapySnapshot(val patients: List<PatientEntity> = emptyList(), val medications: List<MedicationEntity> = emptyList(), val doses: List<DoseOccurrenceEntity> = emptyList(), val movements: List<StockMovementEntity> = emptyList())

class TherapyRepository(private val db: AppDatabase) {
    private val dao = db.therapyDao()
    val snapshot = combine(dao.patients(), dao.medications(), dao.doses(), dao.stockMovements()) { p, m, d, s -> TherapySnapshot(p, m, d, s) }
    suspend fun seedIfEmpty() = db.withTransaction {
        val patient = PatientEntity(id = "patient-demo", alias = "Matteo", type = PatientType.HUMAN)
        val medication = MedicationEntity(id = "med-demo", patientId = patient.id, name = "Farmaco dimostrativo", form = "Compressa", strength = "10 mg")
        val at = LocalDate.now().atTime(LocalTime.of(20, 0)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        dao.insertPatients(listOf(patient))
        dao.insertMedications(listOf(medication))
        dao.insertDoses(listOf(DoseOccurrenceEntity(id = "dose-demo", patientId = patient.id, medicationId = medication.id, scheduledAtEpochMillis = at, doseAmount = "1", doseUnit = StockUnit.TABLET)))
        dao.insertMovement(StockMovementEntity(id = "stock-demo", medicationId = medication.id, causeOccurrenceId = null, amountDecimal = "30", unit = StockUnit.TABLET, type = MovementType.INITIAL))
    }
    suspend fun confirmDose(id: String) = dao.confirmDose(id, System.currentTimeMillis())
    suspend fun changeDoseStatus(id: String, status: DoseStatus) = dao.changeDoseStatus(id, status, System.currentTimeMillis())
    suspend fun upcomingDoses(from: Long, until: Long) = dao.upcomingDoses(from, until)
    suspend fun addPatient(alias: String, type: PatientType) {
        require(alias.isNotBlank()) { "Inserisci il nome del profilo" }
        dao.insertPatient(PatientEntity(alias = alias.trim(), type = type))
    }

    suspend fun addMedication(patientId: String, name: String, form: String, strength: String?, amount: String, unit: StockUnit, hour: Int, minute: Int) = db.withTransaction {
        require(name.isNotBlank() && form.isNotBlank()) { "Nome e forma sono obbligatori" }
        val decimal = amount.toBigDecimal()
        require(decimal > BigDecimal.ZERO) { "La dose deve essere positiva" }
        require(TherapyLimits.canActivateMedication(dao.activeMedicationCount(patientId))) { "Limite di 30 farmaci attivi raggiunto" }
        val medication = MedicationEntity(patientId = patientId, name = name.trim(), form = form.trim(), strength = strength?.trim()?.ifBlank { null })
        val scheduled = LocalDate.now().atTime(hour, minute).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        dao.insertMedication(medication)
        dao.insertDose(DoseOccurrenceEntity(id = UUID.randomUUID().toString(), patientId = patientId, medicationId = medication.id, scheduledAtEpochMillis = scheduled, doseAmount = decimal.toPlainString(), doseUnit = unit))
    }

    suspend fun addStock(medicationId: String, amount: String, unit: StockUnit) {
        val decimal = amount.toBigDecimal()
        require(decimal > BigDecimal.ZERO) { "La quantità deve essere positiva" }
        dao.insertMovement(StockMovementEntity(medicationId = medicationId, causeOccurrenceId = null, amountDecimal = decimal.toPlainString(), unit = unit, type = MovementType.REFILL))
    }

    suspend fun archiveMedication(id: String) = dao.archiveMedication(id)
}
