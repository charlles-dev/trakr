@file:Suppress("ktlint:standard:max-line-length", "MaxLineLength")

package app.trakr.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.trakr.model.AlertEvent
import app.trakr.model.DayCount
import app.trakr.model.MostForgotten
import app.trakr.model.RssiSample
import app.trakr.model.ScanSession
import app.trakr.model.Tool
import app.trakr.model.ToolAlertSetting
import app.trakr.model.TrackerMute
import kotlinx.coroutines.flow.Flow

@Dao
interface ToolDao {
    @Query("SELECT * FROM tools ORDER BY name")
    fun observeTools(): Flow<List<Tool>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTools(tools: List<Tool>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTool(tool: Tool)

    @Query("DELETE FROM tools WHERE id = :id")
    suspend fun deleteTool(id: String)

    /** Atualiza o estado da tag conforme o Ãºltimo radar_report do rastreador. */
    @Query(
        "UPDATE tools SET present = :present, rssi = :rssi, lastSeenAt = :lastSeenAt " +
            "WHERE epc = :epc",
    )
    suspend fun updateToolState(
        epc: String,
        present: Boolean,
        rssi: Int,
        lastSeenAt: Long,
    )

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlert(alert: AlertEvent)

    @Query("SELECT * FROM alerts ORDER BY created_at DESC")
    fun observeAlerts(): Flow<List<AlertEvent>>

    @Query("UPDATE alerts SET read = 1 WHERE id = :id")
    suspend fun markAlertRead(id: Long)

    @Query("DELETE FROM alerts")
    suspend fun clearAlerts()

    @Insert
    suspend fun insertRssiSample(sample: RssiSample)

    @Query("SELECT * FROM rssi_samples WHERE epc = :epc ORDER BY ts DESC LIMIT :limit")
    fun observeRssiSamples(
        epc: String,
        limit: Int = 50,
    ): Flow<List<RssiSample>>

    // ---- Alertas configuráveis ----

    @Query("SELECT * FROM tool_alert_settings WHERE toolId = :id")
    fun observeToolSetting(id: String): Flow<ToolAlertSetting?>

    @Query("SELECT * FROM tool_alert_settings")
    fun observeAllToolSettings(): Flow<List<ToolAlertSetting>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertToolAlertSetting(setting: ToolAlertSetting)

    @Query("SELECT * FROM tracker_mute")
    fun observeTrackerMutes(): Flow<List<TrackerMute>>

    @Query("SELECT * FROM tracker_mute WHERE address = :address")
    fun observeTrackerMute(address: String): Flow<TrackerMute?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setTrackerMute(mute: TrackerMute)

    // ---- Sessões de varredura (estatísticas) ----

    @Insert
    suspend fun insertScanSession(session: ScanSession)

    @Query("SELECT * FROM scan_sessions ORDER BY ts DESC LIMIT :limit")
    fun observeScanSessions(limit: Int = 100): Flow<List<ScanSession>>

    @Query(
        "SELECT date(ts/1000, 'unixepoch', 'localtime') as day, COUNT(*) as cnt FROM scan_sessions GROUP BY day ORDER BY day DESC LIMIT :days",
    )
    fun observeScansPerDay(days: Int = 7): Flow<List<DayCount>>

    // ---- Estatísticas de alertas ----

    @Query("SELECT toolId, toolName, COUNT(*) as cnt FROM alerts GROUP BY toolId ORDER BY cnt DESC LIMIT :limit")
    fun observeMostForgotten(limit: Int = 5): Flow<List<MostForgotten>>

    @Query("SELECT date(created_at/1000, 'unixepoch', 'localtime') as day, COUNT(*) as cnt FROM alerts GROUP BY day ORDER BY day DESC")
    fun observeAlertsPerDay(): Flow<List<DayCount>>

    @Query("SELECT * FROM tools WHERE present = 0 AND lastSeenAt IS NOT NULL ORDER BY lastSeenAt ASC LIMIT :limit")
    fun observeLongestAbsent(limit: Int = 5): Flow<List<Tool>>

    @Query("SELECT AVG(CASE WHEN present THEN 1 ELSE 0 END) FROM tools")
    fun observePresenceRate(): Flow<Float?>

    // ---- Backup/restore ----

    @Query("SELECT * FROM tools")
    suspend fun getAllTools(): List<Tool>

    @Query("SELECT * FROM alerts")
    suspend fun getAllAlerts(): List<AlertEvent>

    @Query("SELECT * FROM rssi_samples")
    suspend fun getAllRssiSamples(): List<RssiSample>

    @Query("SELECT * FROM tool_alert_settings")
    suspend fun getAllToolSettings(): List<ToolAlertSetting>

    @Query("SELECT * FROM tracker_mute")
    suspend fun getAllTrackerMutes(): List<TrackerMute>

    @Query("SELECT * FROM scan_sessions")
    suspend fun getAllScanSessions(): List<ScanSession>

    @Query("DELETE FROM tools")
    suspend fun clearTools()

    @Query("DELETE FROM rssi_samples")
    suspend fun clearRssiSamples()

    @Query("DELETE FROM tool_alert_settings")
    suspend fun clearToolSettings()

    @Query("DELETE FROM tracker_mute")
    suspend fun clearTrackerMutes()

    @Query("DELETE FROM scan_sessions")
    suspend fun clearScanSessions()
}
