package it.miaterapia.family

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import it.miaterapia.family.data.TherapySnapshot
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as MiaTerapiaApplication).repository
    val snapshot = repository.snapshot.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TherapySnapshot())
    init { viewModelScope.launch { repository.seedIfEmpty() } }
    fun confirm(id: String) { viewModelScope.launch { repository.confirmDose(id) } }
}
