package app.trakr.repository

import app.trakr.data.ToolboxDao
import app.trakr.model.AlertEvent
import app.trakr.model.EventRecord
import app.trakr.model.Tool
import app.trakr.model.Toolbox
import kotlinx.coroutines.flow.Flow

class ToolboxRepository(private val dao: ToolboxDao) {

    fun observeToolboxes(): Flow<List<Toolbox>> = dao.observeToolboxes()

    fun observeTools(toolboxId: String): Flow<List<Tool>> = dao.observeTools(toolboxId)

    fun observeAlerts(): Flow<List<AlertEvent>> = dao.observeAlerts()

    fun observeEvents(toolboxId: String): Flow<List<EventRecord>> = dao.observeEvents(toolboxId)

    suspend fun getToolboxes(): List<Toolbox> = dao.getToolboxes()

    /** Grava o inventário recebido do firmware como fonte de verdade. */
    suspend fun saveInventory(toolbox: Toolbox, tools: List<Tool>) {
        dao.upsertToolbox(toolbox)
        dao.clearTools(toolbox.id)
        dao.upsertTools(tools)
    }

    /** Registra a maleta no catálogo local (cache de seleção). */
    suspend fun upsertToolboxLocal(toolbox: Toolbox) = dao.upsertToolbox(toolbox)

    /** Substitui o histórico local da maleta pelo recebido do firmware. */
    suspend fun saveHistory(toolboxId: String, events: List<EventRecord>) {
        dao.clearEvents(toolboxId)
        dao.upsertEvents(events)
    }

    /** Insere um único evento (recebido "ao vivo" via notify). */
    suspend fun upsertEvent(event: EventRecord) = dao.upsertEvent(event)

    suspend fun insertAlert(event: AlertEvent) = dao.insertAlert(event)

    /** Fallback local quando a maleta está desconectada (edição offline). */
    suspend fun addToolLocal(tool: Tool) = dao.upsertTool(tool)

    suspend fun removeToolLocal(id: String) = dao.deleteTool(id)
}