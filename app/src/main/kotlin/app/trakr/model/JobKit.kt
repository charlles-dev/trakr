package app.trakr.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "job_kits")
data class JobKit(
    @PrimaryKey val id: String,
    val name: String,
    val description: String = "",
    /** IDs das ferramentas associadas separados por vírgula. */
    val toolIdsCsv: String = "",
    val createdAt: Long = System.currentTimeMillis(),
) {
    fun getToolIdList(): List<String> {
        if (toolIdsCsv.isBlank()) return emptyList()
        return toolIdsCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }
}
