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
}
