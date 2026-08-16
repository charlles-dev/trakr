package app.trakr.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tool_alert_settings")
data class ToolAlertSetting(
    @PrimaryKey val toolId: String,
    val muted: Boolean = false,
    val sound: String = "default",
    val vibration: Boolean = true,
    val importance: Int = 4,
)

@Entity(tableName = "tracker_mute")
data class TrackerMute(
    @PrimaryKey val address: String,
    val muted: Boolean = false,
)

@Entity(tableName = "scan_sessions")
data class ScanSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val ts: Long = System.currentTimeMillis(),
    val connectedTrackers: Int = 0,
    val toolsSeen: Int = 0,
    val toolsTotal: Int = 0,
    val triggeredBy: String = "rescan",
)

data class MostForgotten(
    val toolId: String,
    val toolName: String,
    val cnt: Int,
)

data class DayCount(
    val day: String,
    val cnt: Int,
)

data class RssiStats(
    val avgRssi: Float,
    val minRssi: Int,
    val maxRssi: Int,
    val count: Int,
)
