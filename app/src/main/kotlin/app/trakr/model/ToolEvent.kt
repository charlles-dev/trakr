package app.trakr.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tool_events")
data class ToolEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val toolId: String,
    val timestamp: Long = System.currentTimeMillis(),
    val eventType: String,
    val details: String = "",
    val rssi: Int? = null,
)
