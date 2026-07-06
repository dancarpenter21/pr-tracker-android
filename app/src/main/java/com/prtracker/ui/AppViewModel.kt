package com.prtracker.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.prtracker.core.Sex
import com.prtracker.core.WeightUnit
import com.prtracker.data.AppState
import com.prtracker.data.LiftEntity
import com.prtracker.data.Repository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppViewModel(private val repository: Repository) : ViewModel() {
    val state: StateFlow<AppState> = repository.appState.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppState(),
    )

    var lastErrors: List<String> = emptyList()
        private set

    init {
        viewModelScope.launch { repository.seedDefaults() }
    }

    fun saveProfile(sex: Sex, preferredUnit: WeightUnit, bodyweight: Double?) {
        viewModelScope.launch { repository.saveProfile(sex, preferredUnit, bodyweight) }
    }

    fun addLift(name: String) {
        viewModelScope.launch { repository.addLift(name) }
    }

    fun toggleMajor(lift: LiftEntity) {
        viewModelScope.launch { repository.toggleMajor(lift) }
    }

    fun toggleArchived(lift: LiftEntity) {
        viewModelScope.launch {
            if (lift.archived) repository.restoreLift(lift) else repository.archiveLift(lift)
        }
    }

    fun deleteEntry(id: Long) {
        viewModelScope.launch { repository.deleteEntry(id) }
    }

    fun addEntry(
        liftId: Long,
        weight: Double,
        unit: WeightUnit,
        sets: Int,
        reps: Int,
        bodyweight: Double?,
        notes: String,
    ) {
        viewModelScope.launch {
            lastErrors = repository.addEntry(liftId, weight, unit, sets, reps, bodyweight, notes)
        }
    }
}

class AppViewModelFactory(private val repository: Repository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return AppViewModel(repository) as T
    }
}
