// Trakr - Firmware ESP32 (skeleton)
// Máquina de estados: LEITURA -> MONITORAMENTO -> ALERTA -> SINCRONIZA.

#include <Arduino.h>

void setup() {
  Serial.begin(115200);
  Serial.println("[TRAKR] Iniciando...");
}

void loop() {
  // Estado inicial: aguardando implementação do leitor YRM100 + BLE.
  delay(1000);
}