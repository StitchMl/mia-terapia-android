package it.miaterapia.family

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import it.miaterapia.family.data.TherapySnapshot
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import it.miaterapia.family.data.PatientType
import it.miaterapia.family.data.StockUnit
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import it.miaterapia.family.settings.DeviceSettings
import it.miaterapia.family.settings.UserRole
import it.miaterapia.family.notifications.AlarmScheduler
import java.time.LocalTime

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as MiaTerapiaApplication).repository
    val snapshot = repository.snapshot.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TherapySnapshot())
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages = _messages.asSharedFlow()
    init { viewModelScope.launch { repository.seedIfEmpty() } }
    fun confirm(id: String) { viewModelScope.launch { if (repository.confirmDose(id)) AlarmScheduler(getApplication()).cancel(id) } }
    fun addPatient(alias: String, type: PatientType) = runAction { repository.addPatient(alias, type) }
    fun addMedication(patientId: String, name: String, form: String, strength: String?, amount: String, unit: StockUnit, times: List<String>) = runAction { repository.addMedication(patientId, name, form, strength, amount, unit, times.map(LocalTime::parse)) }
    fun addStock(medicationId: String, amount: String, unit: StockUnit) = runAction { repository.addStock(medicationId, amount, unit) }
    fun archiveMedication(id: String) = runAction { repository.archiveMedication(id) }
    fun reverseDose(id: String) = runAction { repository.reverseDose(id) }
    fun recordAsNeeded(patientId: String, medicationId: String, amount: String, unit: StockUnit) = runAction { repository.recordAsNeeded(patientId, medicationId, amount, unit) }
    fun createRefillRequest(patientId: String, medicationIds: List<String>, extraText: String) = runAction { repository.createRefillRequest(patientId, medicationIds, extraText) }
    fun markRequestFulfilled(id: String) = runAction { repository.markRequestFulfilled(id) }
    fun setRole(role: UserRole) { DeviceSettings(getApplication()).role = role }
    fun setAlarmAssignment(patientId: String, assigned: Boolean) {
        DeviceSettings(getApplication()).setAlarmAssigned(patientId, assigned)
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            repository.upcomingDoses(now, now + 31L * 24 * 60 * 60_000).filter { it.patientId == patientId }.forEach {
                if (assigned) AlarmScheduler(getApplication()).schedule(it.id, it.scheduledAtEpochMillis) else AlarmScheduler(getApplication()).cancel(it.id)
            }
        }
    }
    private fun runAction(action: suspend () -> Unit) = viewModelScope.launch {
        runCatching { action() }.onFailure { _messages.emit(it.message ?: "Operazione non riuscita") }
    }
}
