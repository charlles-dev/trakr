package app.trakr.repository

import app.trakr.data.ToolboxDao
import app.trakr.model.AlertEvent
import app.trakr.model.Tool
import app.trakr.model.Toolbox
import kotlinx.coroutines.flow.Flow

class ToolboxRepository(private val dao: ToolboxDao) {

    fun observeToolboxes(): Flow<List<Toolbox>> = dao.observeToolboxes()

    fun observeTools(toolboxId: String): Flow<List<Tool>> = dao.observeTools(toolboxId)

    fun observeAlerts(): Flow<List<AlertEvent>> = dao.observeAlerts()

    /** Grava o inventário recebido do firmware como fonte de verdade. */
    suspend fun saveInventory(toolbox: Toolbox, tools: List<Tool>) {
        dao.upsertToolbox(toolbox)
        dao.clearTools(toolbox.id)
        dao.upsertTools(tools)
    }

    suspend fun insertAlert(event: AlertEvent) = dao.insertAlert(event)
}