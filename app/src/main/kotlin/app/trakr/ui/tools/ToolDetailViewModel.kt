package app.trakr.ui.tools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.trakr.data.AppContainer
import app.trakr.model.RssiSample
import app.trakr.repository.ToolRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

class ToolDetailViewModel(
    private val repository: ToolRepository,
) : ViewModel() {
    private val epc = MutableStateFlow<String?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val samples: StateFlow<List<RssiSample>> =
        epc.flatMapLatest { value ->
            if (value.isNullOrBlank()) {
                flowOf(emptyList())
            } else {
                repository.observeRssiSamples(value, limit = 50)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setEpc(value: String) {
        epc.value = value
    }

    companion object {
        val Factory: ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    ToolDetailViewModel(ToolRepository(AppContainer.database.toolDao()))
                }
            }
    }
}
