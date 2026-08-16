package app.trakr.testutil

import app.trakr.data.ToolDao
import app.trakr.model.AlertEvent
import app.trakr.model.RssiSample
import app.trakr.model.Tool
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** ImplementaÃ§Ã£o em memÃ³ria da [ToolDao] para testes JVM puros. */
class FakeToolDao : ToolDao {
    private val tools = MutableStateFlow<List<Tool>>(emptyList())
    private val alerts = MutableStateFlow<List<AlertEvent>>(emptyList())
    private val rssiSamples = MutableStateFlow<List<RssiSample>>(emptyList())

    override fun observeTools(): Flow<List<Tool>> = tools

    override suspend fun upsertTools(tools: List<Tool>) {
        this.tools.value = tools
    }

    override suspend fun upsertTool(tool: Tool) {
        tools.value = tools.value.filter { it.id != tool.id } + tool
    }

    override suspend fun deleteTool(id: String) {
        tools.value = tools.value.filter { it.id != id }
    }

    override suspend fun updateToolState(
        epc: String,
        present: Boolean,
        rssi: Int,
        lastSeenAt: Long,
    ) {
        tools.value =
            tools.value.map {
                if (it.epc == epc) {
                    it.copy(present = present, rssi = rssi, lastSeenAt = lastSeenAt)
                } else {
                    it
                }
            }
    }

    override suspend fun insertAlert(alert: AlertEvent) {
        alerts.value = listOf(alert) + alerts.value
    }

    override fun observeAlerts(): Flow<List<AlertEvent>> = alerts

    override suspend fun markAlertRead(id: Long) {
        alerts.value =
            alerts.value.map {
                if (it.id == id) it.copy(read = true) else it
            }
    }

    override suspend fun clearAlerts() {
        alerts.value = emptyList()
    }

    override suspend fun insertRssiSample(sample: RssiSample) {
        rssiSamples.value = listOf(sample) + rssiSamples.value
    }

    override fun observeRssiSamples(
        epc: String,
        limit: Int,
    ): Flow<List<RssiSample>> = rssiSamples.map { samples -> samples.filter { it.epc == epc }.take(limit) }
}
