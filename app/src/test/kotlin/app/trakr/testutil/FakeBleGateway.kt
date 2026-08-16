package app.trakr.testutil

import app.trakr.core.ble.BleDeviceInfo
import app.trakr.core.ble.BleGateway
import app.trakr.core.ble.BleStatus
import app.trakr.data.InventoryParser
import app.trakr.model.RadarReport
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Fake da [BleGateway]: expõe estados controláveis e captura as chamadas,
 * guardando os callbacks de indisponibilidade para o teste disparar quando quiser.
 */
class FakeBleGateway : BleGateway {
    override val devices = MutableStateFlow<List<BleDeviceInfo>>(emptyList())
    override val status = MutableStateFlow<BleStatus>(BleStatus.Idle)
    override val radarReport = MutableStateFlow<RadarReport?>(null)
    override val lastReply = MutableStateFlow<InventoryParser.CmdReply?>(null)

    val addToolCalls = mutableListOf<Pair<String, String>>()
    val removeToolCalls = mutableListOf<Triple<String, String, () -> Unit>>()
    val startRadarCalls = mutableListOf<Pair<String, String>>()
    var stopRadarCalls = 0
    var rescanCalls = 0
    var getConfigCalls = 0
    val setConfigCalls = mutableListOf<Map<String, Any>>()

    var addToolUnavailable: (() -> Unit)? = null
    var removeToolUnavailable: (() -> Unit)? = null
    var startRadarUnavailable: (() -> Unit)? = null
    var stopRadarUnavailable: (() -> Unit)? = null
    var getConfigUnavailable: (() -> Unit)? = null
    var setConfigUnavailable: (() -> Unit)? = null
    var authUnavailable: (() -> Unit)? = null
    var getHistoryUnavailable: (() -> Unit)? = null
    var listArchivesUnavailable: (() -> Unit)? = null

    var authCalls = mutableListOf<String>()
    var getHistoryCalls = mutableListOf<String?>()
    var listArchivesCalls = 0
    var getSensorsCalls = 0
    var getAddonsCalls = 0
    var startLiveCalls = 0
    var stopLiveCalls = 0
    var startMultiCalls = mutableListOf<List<String>>()
    var setTxPowerCalls = mutableListOf<Int>()

    override fun addTool(
        name: String,
        epc: String,
        onUnavailable: () -> Unit,
    ) {
        addToolCalls += name to epc
        addToolUnavailable = onUnavailable
    }

    override fun removeTool(
        id: String,
        epc: String,
        onUnavailable: () -> Unit,
    ) {
        removeToolCalls += Triple(id, epc, onUnavailable)
        removeToolUnavailable = onUnavailable
    }

    override fun startRadar(
        toolId: String,
        tag: String,
        onUnavailable: () -> Unit,
    ) {
        startRadarCalls += toolId to tag
        startRadarUnavailable = onUnavailable
    }

    override fun stopRadar(onUnavailable: () -> Unit) {
        stopRadarCalls++
        stopRadarUnavailable = onUnavailable
    }

    override fun rescan() {
        rescanCalls++
    }

    override fun getConfig(onUnavailable: () -> Unit) {
        getConfigCalls++
        getConfigUnavailable = onUnavailable
    }

    override fun setConfig(
        fields: Map<String, Any>,
        onUnavailable: () -> Unit,
    ) {
        setConfigCalls += fields
        setConfigUnavailable = onUnavailable
    }

    override fun auth(
        pin: String,
        onUnavailable: () -> Unit,
    ) {
        authCalls += pin
        authUnavailable = onUnavailable
    }

    override fun getHistory(
        month: String?,
        onUnavailable: () -> Unit,
    ) {
        getHistoryCalls += month
        getHistoryUnavailable = onUnavailable
    }

    override fun listArchives(onUnavailable: () -> Unit) {
        listArchivesCalls++
        listArchivesUnavailable = onUnavailable
    }

    override fun getSensors(onUnavailable: () -> Unit) {
        getSensorsCalls++
    }

    override fun getAddons(onUnavailable: () -> Unit) {
        getAddonsCalls++
    }

    override fun startLive(intervalMs: Int, onUnavailable: () -> Unit) {
        startLiveCalls++
    }

    override fun stopLive(onUnavailable: () -> Unit) {
        stopLiveCalls++
    }

    override fun startMultiRadar(tags: List<String>, onUnavailable: () -> Unit) {
        startMultiCalls += tags
    }

    override fun setTxPower(dbm: Int, onUnavailable: () -> Unit) {
        setTxPowerCalls += dbm
    }
}
