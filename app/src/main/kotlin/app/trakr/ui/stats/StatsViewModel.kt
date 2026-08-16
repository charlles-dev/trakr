package app.trakr.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.trakr.data.AppContainer
import app.trakr.model.DayCount
import app.trakr.model.MostForgotten
import app.trakr.model.ScanSession
import app.trakr.model.Tool
import app.trakr.repository.ToolRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class StatsViewModel(
    private val repository: ToolRepository,
) : ViewModel() {
    val mostForgotten: StateFlow<List<MostForgotten>> =
        repository.observeMostForgotten(5).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val scansPerDay: StateFlow<List<DayCount>> =
        repository.observeScansPerDay(7).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val alertsPerDay: StateFlow<List<DayCount>> =
        repository.observeAlertsPerDay().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val longestAbsent: StateFlow<List<Tool>> =
        repository.observeLongestAbsent(5).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val presenceRate: StateFlow<Float?> =
        repository.observePresenceRate().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val recentScans: StateFlow<List<ScanSession>> =
        repository.observeScanSessions(20).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    companion object {
        val Factory: ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val dao = AppContainer.database.toolDao()
                    StatsViewModel(ToolRepository(dao))
                }
            }
    }
}
