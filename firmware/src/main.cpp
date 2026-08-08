// Trakr - Firmware ESP32 (skeleton)
// Máquina de estados: LEITURA -> MONITORAMENTO -> ALERTA -> SINCRONIZA.
// Pinagem centralizada em include/pins.h (referência: docs/hardware/Wiring.md).

#include <Arduino.h>
#include "pins.h"

static void initUartYrm100() {
  // Comunicação com o leitor UHF YRM100 via UART2.
  Serial2.begin(YRM100_BAUD, SERIAL_8N1, YRM100_RX_PIN, YRM100_TX_PIN);
}

static void initInputs() {
  // Sensor Hall com pull-up interno.
  pinMode(HALL_PIN, INPUT_PULLUP);
}

static void initFeedback() {
  pinMode(BUZZER_PIN, OUTPUT);
  digitalWrite(BUZZER_PIN, LOW);
  pinMode(LED_RGB_PIN, OUTPUT);
  digitalWrite(LED_RGB_PIN, LOW);
}

void setup() {
  Serial.begin(115200);
  Serial.println("[TRAKR] Iniciando...");

  initUartYrm100();
  initInputs();
  initFeedback();

  // TODO(estado LEITURA): enviar comando HEX ao YRM100 para varrer EPCs.
  // TODO: carregar inventory.json do LittleFS e montar a memória local.
  // TODO: configurar o BLE (NimBLE-Arduino) com GATT server.
}

void loop() {
  // TODO(estado MONITORAMENTO): cruzar leituras com o inventário local.
  // TODO: acionar alarme local (BUZZER/LED) e notificar o app via BLE.

  delay(1000);

  // Quando a maleta estiver fechada, entrar em deep sleep para poupar bateria.
  // Acorda apenas com mudança magnética (tampa abrindo/fechando):
  // esp_sleep_enable_ext0_wakeup(HALL_PIN, HALL_WAKE_LEVEL);
  // esp_deep_sleep_start();
}