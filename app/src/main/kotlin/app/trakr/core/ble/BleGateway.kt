package app.trakr.core.ble

import app.trakr.data.InventoryParser
import app.trakr.model.RadarReport
import kotlinx.coroutines.flow.StateFlow

/**
 * Porta de comunicação com os rastreadores TRK-Finder.
 *
 * Implementada por [BleManager] em produção; fakes nos testes de ViewModel.
 */
interface BleGateway {
    /** Rastreadores atualmente conectados. */
    val devices: StateFlow<List<BleDeviceInfo>>

    /** Estado global do motor BLE (scan/erros). */
    val status: StateFlow<BleStatus>

    /** Último relatório do modo radar, ou null. */
    val radarReport: StateFlow<RadarReport?>
    val liveReport: StateFlow<app.trakr.model.LiveReport?>
    val multiReport: StateFlow<app.trakr.model.MultiRadarReport?>

    /** Último ACK de comando recebido do firmware, ou null. */
    val lastReply: StateFlow<InventoryParser.CmdReply?>

    /** Envia {"cmd":"add_tool",...} para o primeiro rastreador disponível. */
    fun addTool(
        name: String,
        epc: String,
        onUnavailable: () -> Unit,
    )

    /** Envia {"cmd":"remove_tool",...}; fallback local via [onUnavailable]. */
    fun removeTool(
        id: String,
        epc: String,
        onUnavailable: () -> Unit,
    )

    /** Inicia o modo radar: {"cmd":"start_radar",...}. */
    fun startRadar(
        toolId: String,
        tag: String,
        onUnavailable: () -> Unit,
    )

    /** Para o modo radar: {"cmd":"stop_radar"}. */
    fun stopRadar(onUnavailable: () -> Unit)

    /** Pede um novo ciclo de escaneamento (re-sincronização manual). */
    fun rescan()

    /** Pede as configurações do rastreador: {"cmd":"get_config"}. */
    fun getConfig(onUnavailable: () -> Unit)

    /** Altera configs do rastreador (listen_ms/radar_ms/beep/pin): {"cmd":"set_config",...}. */
    fun setConfig(
        fields: Map<String, Any>,
        onUnavailable: () -> Unit,
    )

    /** Autentica no rastreador: {"cmd":"auth","pin":"..."} */
    fun auth(
        pin: String,
        onUnavailable: () -> Unit,
    )

    /** Pede histórico: {"cmd":"get_history"} ou com mês. */
    fun getHistory(
        month: String? = null,
        onUnavailable: () -> Unit,
    )

    /** Lista arquivos de histórico mensal. */
    fun listArchives(onUnavailable: () -> Unit)

    fun getSensors(onUnavailable: () -> Unit)
    fun getAddons(onUnavailable: () -> Unit)

    fun startLive(intervalMs: Int = 500, onUnavailable: () -> Unit)
    fun stopLive(onUnavailable: () -> Unit)
    fun startMultiRadar(tags: List<String>, onUnavailable: () -> Unit)

    fun setTxPower(dbm: Int, onUnavailable: () -> Unit)
}
