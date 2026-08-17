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
import org.json.JSONArray
import org.json.JSONObject

class ToolRepository(private val dao: ToolDao) {
    fun observeTools(): Flow<List<Tool>> = dao.observeTools()

    fun observeAlerts(): Flow<List<AlertEvent>> = dao.observeAlerts()

    // Alertas configuraveis
    fun observeAllToolSettings(): Flow<List<ToolAlertSetting>> = dao.observeAllToolSettings()

    fun observeToolSetting(id: String): Flow<ToolAlertSetting?> = dao.observeToolSetting(id)

    suspend fun upsertToolSetting(setting: ToolAlertSetting) = dao.upsertToolAlertSetting(setting)

    fun observeTrackerMutes(): Flow<List<TrackerMute>> = dao.observeTrackerMutes()

    fun observeTrackerMute(address: String): Flow<TrackerMute?> = dao.observeTrackerMute(address)

    suspend fun setTrackerMute(mute: TrackerMute) = dao.setTrackerMute(mute)

    // Estatisticas
    fun observeMostForgotten(limit: Int = 5): Flow<List<MostForgotten>> = dao.observeMostForgotten(limit)

    fun observeAlertsPerDay(): Flow<List<DayCount>> = dao.observeAlertsPerDay()

    fun observeScansPerDay(days: Int = 7): Flow<List<DayCount>> = dao.observeScansPerDay(days)

    fun observeScanSessions(limit: Int = 100): Flow<List<ScanSession>> = dao.observeScanSessions(limit)

    fun observeLongestAbsent(limit: Int = 5): Flow<List<Tool>> = dao.observeLongestAbsent(limit)

    fun observePresenceRate(): Flow<Float?> = dao.observePresenceRate()

    suspend fun insertScanSession(session: ScanSession) = dao.insertScanSession(session)

    suspend fun saveInventory(tools: List<Tool>) {
        dao.upsertTools(tools)
    }

    suspend fun addToolLocal(tool: Tool) = dao.upsertTool(tool)

    suspend fun removeToolLocal(id: String) = dao.deleteTool(id)

    suspend fun updateToolState(
        epc: String,
        present: Boolean,
        rssi: Int,
        lastSeenAt: Long,
    ) = dao.updateToolState(epc, present, rssi, lastSeenAt)

    suspend fun insertAlert(alert: AlertEvent) = dao.insertAlert(alert)

    suspend fun markAlertRead(id: Long) = dao.markAlertRead(id)

    suspend fun clearAlerts() = dao.clearAlerts()

    suspend fun recordRssi(
        epc: String,
        rssi: Int,
    ) = dao.insertRssiSample(RssiSample(epc = epc, rssi = rssi))

    fun observeRssiSamples(
        epc: String,
        limit: Int = 50,
    ): Flow<List<RssiSample>> = dao.observeRssiSamples(epc, limit)

    // Job Kits (Missões de Serviço)
    fun observeJobKits(): Flow<List<app.trakr.model.JobKit>> = dao.observeAllJobKits()

    suspend fun getJobKit(id: String): app.trakr.model.JobKit? = dao.getJobKit(id)

    suspend fun saveJobKit(kit: app.trakr.model.JobKit) = dao.upsertJobKit(kit)

    suspend fun deleteJobKit(id: String) = dao.deleteJobKit(id)

    // Eventos & Linha do Tempo
    suspend fun recordToolEvent(
        toolId: String,
        eventType: String,
        details: String = "",
        rssi: Int? = null,
    ) {
        dao.insertToolEvent(
            app.trakr.model.ToolEvent(
                toolId = toolId,
                timestamp = System.currentTimeMillis(),
                eventType = eventType,
                details = details,
                rssi = rssi,
            ),
        )
    }

    fun observeToolEvents(
        toolId: String,
        limit: Int = 50,
    ): Flow<List<app.trakr.model.ToolEvent>> = dao.observeToolEvents(toolId, limit)

    fun observeRecentEvents(limit: Int = 100): Flow<List<app.trakr.model.ToolEvent>> = dao.observeRecentEvents(limit)

    suspend fun importBatchTools(tools: List<Tool>) {
        dao.upsertTools(tools)
        tools.forEach { tool ->
            recordToolEvent(tool.id, "CREATED", "Importação em lote")
        }
    }

    // Backup/restore
    suspend fun exportBackupJson(): String {
        val tools = dao.getAllTools()
        val alerts = dao.getAllAlerts()
        val settings = dao.getAllToolSettings()
        val mutes = dao.getAllTrackerMutes()
        val sessions = dao.getAllScanSessions()
        val root = JSONObject()
        root.put("version", 1)
        root.put("exported_at", System.currentTimeMillis())

        val toolsArr = JSONArray()
        tools.forEach { t ->
            val o = JSONObject()
            o.put("id", t.id)
            o.put("name", t.name)
            o.put("epc", t.epc)
            o.put("present", t.present)
            o.put("rssi", t.rssi)
            o.put("lastSeenAt", t.lastSeenAt)
            toolsArr.put(o)
        }
        root.put("tools", toolsArr)

        val alertsArr = JSONArray()
        alerts.forEach { a ->
            val o = JSONObject()
            o.put("toolId", a.toolId)
            o.put("toolName", a.toolName)
            o.put("createdAt", a.createdAt)
            o.put("read", a.read)
            alertsArr.put(o)
        }
        root.put("alerts", alertsArr)

        val settingsArr = JSONArray()
        settings.forEach { s ->
            val o = JSONObject()
            o.put("toolId", s.toolId)
            o.put("muted", s.muted)
            o.put("sound", s.sound)
            o.put("vibration", s.vibration)
            o.put("importance", s.importance)
            settingsArr.put(o)
        }
        root.put("tool_settings", settingsArr)

        val mutesArr = JSONArray()
        mutes.forEach { m ->
            val o = JSONObject()
            o.put("address", m.address)
            o.put("muted", m.muted)
            mutesArr.put(o)
        }
        root.put("tracker_mutes", mutesArr)

        val sessionsArr = JSONArray()
        sessions.forEach { ss ->
            val o = JSONObject()
            o.put("ts", ss.ts)
            o.put("connectedTrackers", ss.connectedTrackers)
            o.put("toolsSeen", ss.toolsSeen)
            o.put("toolsTotal", ss.toolsTotal)
            o.put("triggeredBy", ss.triggeredBy)
            sessionsArr.put(o)
        }
        root.put("scan_sessions", sessionsArr)

        return root.toString(2)
    }

    suspend fun importBackupJson(jsonStr: String): Boolean {
        return try {
            val root = JSONObject(jsonStr)
            val toolsArr = root.optJSONArray("tools") ?: JSONArray()
            val alertsArr = root.optJSONArray("alerts") ?: JSONArray()
            val settingsArr = root.optJSONArray("tool_settings") ?: JSONArray()
            val mutesArr = root.optJSONArray("tracker_mutes") ?: JSONArray()
            val sessionsArr = root.optJSONArray("scan_sessions") ?: JSONArray()

            dao.clearTools()
            dao.clearAlerts()
            dao.clearToolSettings()
            dao.clearTrackerMutes()
            dao.clearScanSessions()
            dao.clearRssiSamples()

            val tools = mutableListOf<Tool>()
            for (i in 0 until toolsArr.length()) {
                val o = toolsArr.getJSONObject(i)
                tools +=
                    Tool(
                        id = o.optString("id"),
                        name = o.optString("name"),
                        epc = o.optString("epc"),
                        present = o.optBoolean("present"),
                        rssi = if (o.has("rssi")) o.optInt("rssi") else null,
                        lastSeenAt = if (o.has("lastSeenAt")) o.optLong("lastSeenAt") else null,
                    )
            }
            dao.upsertTools(tools)

            for (i in 0 until alertsArr.length()) {
                val o = alertsArr.getJSONObject(i)
                dao.insertAlert(
                    AlertEvent(
                        toolId = o.optString("toolId"),
                        toolName = o.optString("toolName"),
                        createdAt = o.optLong("createdAt"),
                        read = o.optBoolean("read"),
                    ),
                )
            }
            for (i in 0 until settingsArr.length()) {
                val o = settingsArr.getJSONObject(i)
                dao.upsertToolAlertSetting(
                    ToolAlertSetting(
                        toolId = o.optString("toolId"),
                        muted = o.optBoolean("muted"),
                        sound = o.optString("sound", "default"),
                        vibration = o.optBoolean("vibration", true),
                        importance = o.optInt("importance", 4),
                    ),
                )
            }
            for (i in 0 until mutesArr.length()) {
                val o = mutesArr.getJSONObject(i)
                dao.setTrackerMute(
                    TrackerMute(
                        address = o.optString("address"),
                        muted = o.optBoolean("muted"),
                    ),
                )
            }
            for (i in 0 until sessionsArr.length()) {
                val o = sessionsArr.getJSONObject(i)
                dao.insertScanSession(
                    ScanSession(
                        ts = o.optLong("ts"),
                        connectedTrackers = o.optInt("connectedTrackers"),
                        toolsSeen = o.optInt("toolsSeen"),
                        toolsTotal = o.optInt("toolsTotal"),
                        triggeredBy = o.optString("triggeredBy", "import"),
                    ),
                )
            }
            true
        } catch (e: Exception) {
            false
        }
    }
}
