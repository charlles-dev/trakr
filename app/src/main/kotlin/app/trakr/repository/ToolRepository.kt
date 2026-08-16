package app.trakr.repository

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

class ToolRepository(private val dao: ToolDao) {
    fun observeTools(): Flow<List<Tool>> = dao.observeTools()

    fun observeAlerts(): Flow<List<AlertEvent>> = dao.observeAlerts()

    // Alertas configuráveis
    fun observeAllToolSettings(): Flow<List<ToolAlertSetting>> = dao.observeAllToolSettings()
    fun observeToolSetting(id: String): Flow<ToolAlertSetting?> = dao.observeToolSetting(id)
    suspend fun upsertToolSetting(setting: ToolAlertSetting) = dao.upsertToolAlertSetting(setting)
    fun observeTrackerMutes(): Flow<List<TrackerMute>> = dao.observeTrackerMutes()
    fun observeTrackerMute(address: String): Flow<TrackerMute?> = dao.observeTrackerMute(address)
    suspend fun setTrackerMute(mute: TrackerMute) = dao.setTrackerMute(mute)

    // Estatísticas
    fun observeMostForgotten(limit: Int = 5): Flow<List<MostForgotten>> = dao.observeMostForgotten(limit)
    fun observeAlertsPerDay(): Flow<List<DayCount>> = dao.observeAlertsPerDay()
    fun observeScansPerDay(days: Int = 7): Flow<List<DayCount>> = dao.observeScansPerDay(days)
    fun observeScanSessions(limit: Int = 100): Flow<List<ScanSession>> = dao.observeScanSessions(limit)
    fun observeLongestAbsent(limit: Int = 5): Flow<List<Tool>> = dao.observeLongestAbsent(limit)
    fun observePresenceRate(): Flow<Float?> = dao.observePresenceRate()
    suspend fun insertScanSession(session: ScanSession) = dao.insertScanSession(session)

    // Backup/restore
    suspend fun exportBackupJson(): String {
        val tools = dao.getAllTools()
        val alerts = dao.getAllAlerts()
        val rssi = dao.getAllRssiSamples()
        val settings = dao.getAllToolSettings()
        val mutes = dao.getAllTrackerMutes()
        val sessions = dao.getAllScanSessions()
        val json = org.json.JSONObject()
        json.put("version", 1)
        json.put("exported_at", System.currentTimeMillis())
        json.put("tools", org.json.JSONArray().apply { tools.forEach { t -> put(org.json.JSONObject().apply { put("id", t.id); put("name", t.name); put("epc", t.epc); put("present", t.present); put("rssi", t.rssi); put("lastSeenAt", t.lastSeenAt) }) } })
        json.put("alerts", org.json.JSONArray().apply { alerts.forEach { a -> put(org.json.JSONObject().apply { put("toolId", a.toolId); put("toolName", a.toolName); put("createdAt", a.createdAt); put("read", a.read) }) } })
        json.put("tool_settings", org.json.JSONArray().apply { settings.forEach { s -> put(org.json.JSONObject().apply { put("toolId", s.toolId); put("muted", s.muted); put("sound", s.sound); put("vibration", s.vibration); put("importance", s.importance) }) } })
        json.put("tracker_mutes", org.json.JSONArray().apply { mutes.forEach { m -> put(org.json.JSONObject().apply { put("address", m.address); put("muted", m.muted) }) } })
        json.put("scan_sessions", org.json.JSONArray().apply { sessions.forEach { ss -> put(org.json.JSONObject().apply { put("ts", ss.ts); put("connectedTrackers", ss.connectedTrackers); put("toolsSeen", ss.toolsSeen); put("toolsTotal", ss.toolsTotal); put("triggeredBy", ss.triggeredBy) }) } })
        return json.toString(2)
    }

    suspend fun importBackupJson(jsonStr: String): Boolean {
        return try {
            val root = org.json.JSONObject(jsonStr)
            val toolsArr = root.optJSONArray("tools") ?: org.json.JSONArray()
            val alertsArr = root.optJSONArray("alerts") ?: org.json.JSONArray()
            val settingsArr = root.optJSONArray("tool_settings") ?: org.json.JSONArray()
            val mutesArr = root.optJSONArray("tracker_mutes") ?: org.json.JSONArray()
            val sessionsArr = root.optJSONArray("scan_sessions") ?: org.json.JSONArray()

            dao.clearTools()
            dao.clearAlerts()
            dao.clearToolSettings()
            dao.clearTrackerMutes()
            dao.clearScanSessions()
            dao.clearRssiSamples()

            val tools = mutableListOf<Tool>()
            for (i in 0 until toolsArr.length()) {
                val o = toolsArr.getJSONObject(i)
                tools += Tool(id = o.optString("id"), name = o.optString("name"), epc = o.optString("epc"), present = o.optBoolean("present"), rssi = if (o.has("rssi")) o.optInt("rssi") else null, lastSeenAt = if (o.has("lastSeenAt")) o.optLong("lastSeenAt") else null)
            }
            dao.upsertTools(tools)

            for (i in 0 until alertsArr.length()) {
                val o = alertsArr.getJSONObject(i)
                dao.insertAlert(app.trakr.model.AlertEvent(toolId = o.optString("toolId"), toolName = o.optString("toolName"), createdAt = o.optLong("createdAt"), read = o.optBoolean("read")))
            }
            for (i in 0 until settingsArr.length()) {
                val o = settingsArr.getJSONObject(i)
                dao.upsertToolAlertSetting(ToolAlertSetting(toolId = o.optString("toolId"), muted = o.optBoolean("muted"), sound = o.optString("sound", "default"), vibration = o.optBoolean("vibration", true), importance = o.optInt("importance", 4)))
            }
            for (i in 0 until mutesArr.length()) {
                val o = mutesArr.getJSONObject(i)
                dao.setTrackerMute(TrackerMute(address = o.optString("address"), muted = o.optBoolean("muted")))
            }
            for (i in 0 until sessionsArr.length()) {
                val o = sessionsArr.getJSONObject(i)
                dao.insertScanSession(ScanSession(ts = o.optLong("ts"), connectedTrackers = o.optInt("connectedTrackers"), toolsSeen = o.optInt("toolsSeen"), toolsTotal = o.optInt("toolsTotal"), triggeredBy = o.optString("triggeredBy", "import")))
            }
            true
        } catch (e: Exception) {
            false
        }
    }

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
