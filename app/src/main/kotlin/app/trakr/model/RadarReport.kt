package app.trakr.model

/**
 * Relatório do modo radar (rastreador portátil) recebido via GATT Event:
 * {"type":"radar_report","tag":"...","rssi":-52,"present":true}
 */
data class RadarReport(
    val tag: String,
    val rssi: Int,
    val present: Boolean,
    val at: Long = System.currentTimeMillis(),
)
