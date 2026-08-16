package app.trakr.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.trakr.model.AlertEvent
import app.trakr.model.RssiSample
import app.trakr.model.Tool
import kotlinx.coroutines.flow.Flow

@Dao
interface ToolboxDao {
    @Query("SELECT * FROM tools ORDER BY name")
    fun observeTools(): Flow<List<Tool>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTools(tools: List<Tool>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTool(tool: Tool)

    @Query("DELETE FROM tools WHERE id = :id")
    suspend fun deleteTool(id: String)

    /** Atualiza o estado da tag conforme o último radar_report do rastreador. */
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
}
