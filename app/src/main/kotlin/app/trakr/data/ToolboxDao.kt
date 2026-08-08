package app.trakr.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
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
}