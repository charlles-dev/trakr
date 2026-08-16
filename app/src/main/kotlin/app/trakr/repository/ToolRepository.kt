package app.trakr.repository

import app.trakr.data.ToolDao
import app.trakr.model.AlertEvent
import app.trakr.model.RssiSample
import app.trakr.model.Tool
import kotlinx.coroutines.flow.Flow

class ToolRepository(private val dao: ToolDao) {
    fun observeTools(): Flow<List<Tool>> = dao.observeTools()

    fun observeAlerts(): Flow<List<AlertEvent>> = dao.observeAlerts()

    /** Substitui o inventÃ¡rio local pelo recebido do rastreador (fonte da verdade). */
    suspend fun saveInventory(tools: List<Tool>) {
        dao.upsertTools(tools)
    }

    suspend fun addToolLocal(tool: Tool) = dao.upsertTool(tool)

    suspend fun removeToolLocal(id: String) = dao.deleteTool(id)

    /** Atualiza o estado da tag conforme o Ãºltimo radar_report. */
    suspend fun updateToolState(
        epc: String,
        present: Boolean,
        rssi: Int,
        lastSeenAt: Long,
    ) = dao.updateToolState(epc, present, rssi, lastSeenAt)

    suspend fun insertAlert(alert: AlertEvent) = dao.insertAlert(alert)

    suspend fun markAlertRead(id: Long) = dao.markAlertRead(id)

    suspend fun clearAlerts() = dao.clearAlerts()

    /** Registra uma amostra de RSSI do modo radar para o histÃ³rico da tag. */
    suspend fun recordRssi(
        epc: String,
        rssi: Int,
    ) = dao.insertRssiSample(RssiSample(epc = epc, rssi = rssi))

    fun observeRssiSamples(
        epc: String,
        limit: Int = 50,
    ): Flow<List<RssiSample>> = dao.observeRssiSamples(epc, limit)
}
