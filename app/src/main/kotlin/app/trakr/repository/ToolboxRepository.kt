package app.trakr.repository

import app.trakr.data.ToolboxDao
import app.trakr.model.Tool
import app.trakr.model.Toolbox
import kotlinx.coroutines.flow.Flow

class ToolboxRepository(private val dao: ToolboxDao) {

    fun observeToolboxes(): Flow<List<Toolbox>> = dao.observeToolboxes()

    fun observeTools(toolboxId: String): Flow<List<Tool>> = dao.observeTools(toolboxId)

    /** Salva um inventário completo recebido do firmware via BLE. */
    suspend fun saveInventory(toolbox: Toolbox, tools: List<Tool>) {
        dao.upsertToolbox(toolbox)
        dao.upsertTools(tools)
    }
}