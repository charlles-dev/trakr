package app.trakr.core.ble

import java.util.UUID

/**
 * Perfil GATT do Trakr — espelha firmware/include/ble_profile.h.
 */
object BleProfile {
    val SERVICE_UUID: UUID = UUID.fromString("60c1f000-1b2e-4d0f-9aeb-0fbe3c2a4b71")
    val INVENTORY_UUID: UUID = UUID.fromString("60c1f001-1b2e-4d0f-9aeb-0fbe3c2a4b71")
    val EVENT_UUID: UUID = UUID.fromString("60c1f002-1b2e-4d0f-9aeb-0fbe3c2a4b71")
    val CONTROL_UUID: UUID = UUID.fromString("60c1f003-1b2e-4d0f-9aeb-0fbe3c2a4b71")
    const val DEVICE_NAME = "TRAKR-MALETA"
}