package app.trakr.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import app.trakr.R

enum class ToolCategory(
    val id: String,
    val labelRes: Int,
) {
    MANUAL("manual", R.string.category_manual),
    ELETRICA("eletrica", R.string.category_eletrica),
    MEDICAO("medicao", R.string.category_medicao),
    EPI("epi", R.string.category_epi),
    OUTRO("outro", R.string.category_outro),
    ;

    companion object {
        fun fromId(id: String?): ToolCategory {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: MANUAL
        }
    }
}

@Entity(tableName = "tools")
data class Tool(
    @PrimaryKey val id: String,
    val name: String,
    val icon: String = "wrench",
    val category: String = "manual",
    /** Visto pelo rastreador na última varredura (radar_report). */
    val present: Boolean = false,
    val epc: String = "",
    /** Último RSSI medido pelo rastreador, ou null se nunca visto. */
    val rssi: Int? = null,
    /** Millis da última vez que o rastreador viu a tag, ou null. */
    val lastSeenAt: Long? = null,
)
