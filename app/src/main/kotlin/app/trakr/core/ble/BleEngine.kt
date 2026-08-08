package app.trakr.core.ble

/**
 * Motor de conexão BLE com o firmware da maleta (ESP32).
 *
 * A maleta é a fonte da verdade do inventário. Este motor é responsável por:
 * - scan e conexão com a maleta
 * - leitura do inventário (GATT notify)
 * - eventos de retirada/devolução de ferramentas
 *
 * TODO: implementar com android.bluetooth.le (BluetoothLeScanner/GattClient)
 *       e expor os eventos via Flow para o repository/UI.
 */
class BleEngine {
    // Dados da característica GATT ainda a definir no firmware.
    // private val serviceUuid: UUID = ...
    // private val inventoryCharacteristicUuid: UUID = ...

    fun isSupported(): Boolean = TODO("Verificar suporte BLE via BluetoothManager")

    fun startScan() {
        TODO("BluetoothLeScanner.startScan(...)")
    }

    fun stopScan() {
        TODO("BluetoothLeScanner.stopScan(...)")
    }
}