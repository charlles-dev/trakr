package app.trakr.repository

import app.trakr.data.ToolboxDao
import app.trakr.model.AlertEvent
import app.trakr.model.RssiSample
import app.trakr.model.Tool
import kotlinx.coroutines.flow.Flow

class ToolboxRepository(private val dao: ToolboxDao) {
    fun observeTools(): Flow<List<Tool>> = dao.observeTools()

    fun observeAlerts(): Flow<List<AlertEvent>> = dao.observeAlerts()

    /** Substitui o inventário local pelo recebido do rastreador (fonte da verdade). */
    suspend fun saveInventory(tools: List<Tool>) {
        dao.upsertTools(tools)
    }

    suspend fun addToolLocal(tool: Tool) = dao.upsertTool(tool)

    suspend fun removeToolLocal(id: String) = dao.deleteTool(id)

    /** Atualiza o estado da tag conforme o último radar_report. */
    suspend fun updateToolState(
        epc: String,
        present: Boolean,
        rssi: Int,
        lastSeenAt: Long,
    ) = dao.updateToolState(epc, present, rssi, lastSeenAt)

    suspend fun insertAlert(alert: AlertEvent) = dao.insertAlert(alert)

    /** Registra uma amostra de RSSI do modo radar para o histórico da tag. */
    suspend fun recordRssi(
        epc: String,
        rssi: Int,
    ) = dao.insertRssiSample(RssiSample(epc = epc, rssi = rssi))

    fun observeRssiSamples(
        epc: String,
        limit: Int = 50,
    ): Flow<List<RssiSample>> = dao.observeRssiSamples(epc, limit)
}
