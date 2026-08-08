package app.trakr.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Evento do histórico (persistido localmente a partir do History da maleta).
 * O firmware é a fonte da verdade; o Room é o cache offline.
 */
@Entity(
    tableName = "events",
    indices = [Index("toolboxId")],
)
data class EventRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val toolboxId: String,
    val type: String,
    val toolId: String = "",
    val toolName: String = "",
    /** millis() do firmware (relativo ao boot) — usado só para ordenar. */
    val ts: Long = 0L,
    /** timestamp local de quando o evento foi sincronizado. */
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
)
