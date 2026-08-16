package app.trakr.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tools")
data class Tool(
    @PrimaryKey val id: String,
    val name: String,
    val icon: String = "wrench",
    /** Visto pelo rastreador na última varredura (radar_report). */
    val present: Boolean = false,
    val epc: String = "",
    /** Último RSSI medido pelo rastreador, ou null se nunca visto. */
    val rssi: Int? = null,
    /** Millis da última vez que o rastreador viu a tag, ou null. */
    val lastSeenAt: Long? = null,
)
