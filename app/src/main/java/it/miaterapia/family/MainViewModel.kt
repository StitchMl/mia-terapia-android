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

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as MiaTerapiaApplication).repository
    val snapshot = repository.snapshot.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TherapySnapshot())
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages = _messages.asSharedFlow()
    init { viewModelScope.launch { repository.seedIfEmpty() } }
    fun confirm(id: String) { viewModelScope.launch { repository.confirmDose(id) } }
    fun addPatient(alias: String, type: PatientType) = runAction { repository.addPatient(alias, type) }
    fun addMedication(patientId: String, name: String, form: String, strength: String?, amount: String, unit: StockUnit, hour: Int, minute: Int) = runAction { repository.addMedication(patientId, name, form, strength, amount, unit, hour, minute) }
    fun addStock(medicationId: String, amount: String, unit: StockUnit) = runAction { repository.addStock(medicationId, amount, unit) }
    fun archiveMedication(id: String) = runAction { repository.archiveMedication(id) }
    private fun runAction(action: suspend () -> Unit) = viewModelScope.launch {
        runCatching { action() }.onFailure { _messages.emit(it.message ?: "Operazione non riuscita") }
    }
}
