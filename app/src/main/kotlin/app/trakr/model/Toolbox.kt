package app.trakr.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "toolboxes")
data class Toolbox(
    @PrimaryKey val id: String,
    val name: String,
    val lastSyncAt: Long? = null,
)