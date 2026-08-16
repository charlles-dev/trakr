package app.trakr.testutil

import app.trakr.data.ToolDao
import app.trakr.model.AlertEvent
import app.trakr.model.DayCount
import app.trakr.model.MostForgotten
import app.trakr.model.RssiSample
import app.trakr.model.ScanSession
import app.trakr.model.Tool
import app.trakr.model.ToolAlertSetting
import app.trakr.model.TrackerMute
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** ImplementaÃ§Ã£o em memÃ³ria da [ToolDao] para testes JVM puros. */
class FakeToolDao : ToolDao {
    private val tools = MutableStateFlow<List<Tool>>(emptyList())
    private val alerts = MutableStateFlow<List<AlertEvent>>(emptyList())
    private val rssiSamples = MutableStateFlow<List<RssiSample>>(emptyList())
    private val toolSettings = MutableStateFlow<List<ToolAlertSetting>>(emptyList())
    private val trackerMutes = MutableStateFlow<List<TrackerMute>>(emptyList())
    private val scanSessions = MutableStateFlow<List<ScanSession>>(emptyList())

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

    override fun observeToolSetting(id: String): Flow<ToolAlertSetting?> = toolSettings.map { list -> list.find { it.toolId == id } }
    override fun observeAllToolSettings(): Flow<List<ToolAlertSetting>> = toolSettings
    override suspend fun upsertToolAlertSetting(setting: ToolAlertSetting) {
        toolSettings.value = toolSettings.value.filter { it.toolId != setting.toolId } + setting
    }
    override fun observeTrackerMutes(): Flow<List<TrackerMute>> = trackerMutes
    override fun observeTrackerMute(address: String): Flow<TrackerMute?> = trackerMutes.map { list -> list.find { it.address == address } }
    override suspend fun setTrackerMute(mute: TrackerMute) {
        trackerMutes.value = trackerMutes.value.filter { it.address != mute.address } + mute
    }
    override suspend fun insertScanSession(session: ScanSession) {
        scanSessions.value = listOf(session) + scanSessions.value
    }
    override fun observeScanSessions(limit: Int): Flow<List<ScanSession>> = scanSessions.map { it.take(limit) }
    override fun observeScansPerDay(days: Int): Flow<List<DayCount>> = scanSessions.map { sessions ->
        sessions.groupBy { java.text.SimpleDateFormat("yyyy-MM-dd").format(java.util.Date(it.ts)) }.map { (day, list) -> DayCount(day, list.size) }.sortedByDescending { it.day }.take(days)
    }
    override fun observeMostForgotten(limit: Int): Flow<List<MostForgotten>> = alerts.map { list ->
        list.groupBy { it.toolId }.map { (id, items) -> MostForgotten(id, items.firstOrNull()?.toolName ?: id, items.size) }.sortedByDescending { it.cnt }.take(limit)
    }
    override fun observeAlertsPerDay(): Flow<List<DayCount>> = alerts.map { list ->
        list.groupBy { java.text.SimpleDateFormat("yyyy-MM-dd").format(java.util.Date(it.createdAt)) }.map { (day, items) -> DayCount(day, items.size) }.sortedByDescending { it.day }
    }
    override fun observeLongestAbsent(limit: Int): Flow<List<Tool>> = tools.map { list -> list.filter { !it.present && it.lastSeenAt != null }.sortedBy { it.lastSeenAt }.take(limit) }
    override fun observePresenceRate(): Flow<Float?> = tools.map { list ->
        if (list.isEmpty()) null else list.count { it.present }.toFloat() / list.size
    }

    override suspend fun getAllTools(): List<app.trakr.model.Tool> = tools.value
    override suspend fun getAllAlerts(): List<app.trakr.model.AlertEvent> = alerts.value
    override suspend fun getAllRssiSamples(): List<app.trakr.model.RssiSample> = rssiSamples.value
    override suspend fun getAllToolSettings(): List<app.trakr.model.ToolAlertSetting> = toolSettings.value
    override suspend fun getAllTrackerMutes(): List<app.trakr.model.TrackerMute> = trackerMutes.value
    override suspend fun getAllScanSessions(): List<app.trakr.model.ScanSession> = scanSessions.value
    override suspend fun clearTools() { tools.value = emptyList() }
    override suspend fun clearRssiSamples() { rssiSamples.value = emptyList() }
    override suspend fun clearToolSettings() { toolSettings.value = emptyList() }
    override suspend fun clearTrackerMutes() { trackerMutes.value = emptyList() }
    override suspend fun clearScanSessions() { scanSessions.value = emptyList() }
}
