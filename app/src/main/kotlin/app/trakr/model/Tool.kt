package app.trakr.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tools",
    foreignKeys = [
        ForeignKey(
            entity = Toolbox::class,
            parentColumns = ["id"],
            childColumns = ["toolboxId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("toolboxId")],
)
data class Tool(
    @PrimaryKey val id: String,
    val toolboxId: String,
    val name: String,
    val icon: String = "wrench",
    val present: Boolean = true,
)