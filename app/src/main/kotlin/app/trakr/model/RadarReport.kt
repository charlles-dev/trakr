package app.trakr.model

/**
 * Relatório do modo radar (rastreador portátil) recebido via GATT Event:
 * {"type":"radar_report","tag":"...","rssi":-52,"present":true,"delta":3,"hint":"continue"}
 */
data class RadarReport(
    val tag: String,
    val rssi: Int,
    val present: Boolean,
    val at: Long = System.currentTimeMillis(),
    val delta: Int = 0,
    val hint: String = "search",
    val threshold: Int = -70,
)

data class LiveReport(
    val reads: List<LiveRead>,
    val at: Long = System.currentTimeMillis(),
)

data class LiveRead(val tag: String, val rssi: Int)

data class MultiRadarReport(
    val ranking: List<LiveRead>,
    val at: Long = System.currentTimeMillis(),
)
