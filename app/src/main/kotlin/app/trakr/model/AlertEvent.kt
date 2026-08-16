package app.trakr.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** Evento de alerta (ex: ferramenta não encontrada). */
@Entity(tableName = "alerts")
data class AlertEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val toolId: String,
    val toolName: String,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
)
