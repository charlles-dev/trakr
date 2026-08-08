package app.trakr.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.trakr.model.AlertEvent
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
}