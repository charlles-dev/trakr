package app.trakr.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.trakr.model.AlertEvent
import app.trakr.model.EventRecord
import app.trakr.model.Tool
import app.trakr.model.Toolbox
import kotlinx.coroutines.flow.Flow

@Dao
interface ToolboxDao {

    @Query("SELECT * FROM toolboxes ORDER BY name")
    fun observeToolboxes(): Flow<List<Toolbox>>

    @Query("SELECT * FROM tools WHERE toolboxId = :toolboxId ORDER BY name")
    fun observeTools(toolboxId: String): Flow<List<Tool>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertToolbox(toolbox: Toolbox)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTools(tools: List<Tool>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTool(tool: Tool)

    @Query("DELETE FROM tools WHERE id = :id")
    suspend fun deleteTool(id: String)

    @Query("DELETE FROM tools WHERE toolboxId = :toolboxId")
    suspend fun clearTools(toolboxId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlert(alert: AlertEvent)

    @Query("SELECT * FROM alerts ORDER BY created_at DESC")
    fun observeAlerts(): Flow<List<AlertEvent>>

    @Query("SELECT * FROM events WHERE toolboxId = :toolboxId ORDER BY ts DESC")
    fun observeEvents(toolboxId: String): Flow<List<EventRecord>>

    @Query("SELECT * FROM events WHERE toolboxId = :toolboxId ORDER BY ts DESC")
    suspend fun getEvents(toolboxId: String): List<EventRecord>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEvents(events: List<EventRecord>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEvent(event: EventRecord)

    @Query("DELETE FROM events WHERE toolboxId = :toolboxId")
    suspend fun clearEvents(toolboxId: String)

    @Query("SELECT * FROM toolboxes ORDER BY name")
    suspend fun getToolboxes(): List<Toolbox>
}