#pragma once

// ===== Trakr - Perfil GATT BLE (compartilhado com o app Android) =====
// Espelhar exatamente em app/src/main/kotlin/app/trakr/core/ble/BleProfile.kt.

#define TRAKR_SERVICE_UUID        "60c1f000-1b2e-4d0f-9aeb-0fbe3c2a4b71"
#define TRAKR_CHAR_INVENTORY_UUID  "60c1f001-1b2e-4d0f-9aeb-0fbe3c2a4b71"
#define TRAKR_CHAR_EVENT_UUID      "60c1f002-1b2e-4d0f-9aeb-0fbe3c2a4b71"
#define TRAKR_CHAR_CONTROL_UUID    "60c1f003-1b2e-4d0f-9aeb-0fbe3c2a4b71"

// Nome do device visível no scan BLE.
#define TRAKR_DEVICE_NAME "TRAKR-MALETA"