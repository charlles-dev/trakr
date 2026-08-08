package app.trakr.ui.tools

import androidx.lifecycle.ViewModel
import app.trakr.data.AppContainer
import app.trakr.model.MAIN_TOOLBOX_ID
import app.trakr.model.Tool
import app.trakr.repository.ToolboxRepository
import kotlinx.coroutines.flow.Flow

class ToolListViewModel : ViewModel() {

    private val repository = ToolboxRepository(AppContainer.database.toolboxDao())

    val tools: Flow<List<Tool>> = repository.observeTools(MAIN_TOOLBOX_ID)
}