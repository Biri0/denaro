package it.rfmariano.denaro.data.migration

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MigrationViewModel(application: Application) : AndroidViewModel(application) {
    private val coordinator = LegacyMigrationCoordinator(application)
    private val _result = MutableStateFlow<MigrationResult?>(null)
    private var migrationJob: Job? = null

    val result: StateFlow<MigrationResult?> = _result.asStateFlow()

    init {
        migrate()
    }

    fun retry() {
        migrate()
    }

    private fun migrate() {
        if (migrationJob?.isActive == true) return
        migrationJob = viewModelScope.launch {
            _result.value = null
            _result.value = coordinator.migrateIfNeeded()
        }
    }
}
