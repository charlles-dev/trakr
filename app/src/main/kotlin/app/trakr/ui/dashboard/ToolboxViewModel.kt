package app.trakr.ui.dashboard

import androidx.lifecycle.ViewModel
import app.trakr.data.AppContainer
import app.trakr.model.Tool
import app.trakr.repository.ToolboxRepository
import kotlinx.coroutines.flow.Flow

/** Maleta principal (a primeira conectada). TODO: resolver via BLE. */
const val MAIN_TOOLBOX_ID = "main"

class ToolboxViewModel : ViewModel() {

    private val repository = ToolboxRepository(AppContainer.database.toolboxDao())

    val tools: Flow<List<Tool>> = repository.observeTools(MAIN_TOOLBOX_ID)
}