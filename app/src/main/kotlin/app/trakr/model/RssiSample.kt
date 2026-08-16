package app.trakr.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** Amostra de RSSI de uma tag no modo radar (histórico de sinal). */
@Entity(tableName = "rssi_samples")
data class RssiSample(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val epc: String,
    val rssi: Int,
    @ColumnInfo(name = "ts") val ts: Long = System.currentTimeMillis(),
)
