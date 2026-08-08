#pragma once

// ===== Trakr - Mapeamento de Pinos (ESP32-WROOM-32) =====
// Referência: docs/hardware/Wiring.md

// --- Leitor UHF YRM100 (UART2) ---
#define YRM100_UART_NUM 2
#define YRM100_TX_PIN   17 // ESP32 TX2 -> RX do YRM100
#define YRM100_RX_PIN   16 // ESP32 RX2 <- TX do YRM100
#define YRM100_BAUD     115200

// --- Sensor Hall (A3144) - detecção da tampa ---
#define HALL_PIN        33 // Suporta wakeup RTC (ext0)
#define HALL_WAKE_LEVEL LOW // Ímã presente (tampa fechada)

// --- Feedback ---
#define BUZZER_PIN      25 // Alarme local
#define LED_RGB_PIN     26 // WS2812B (light pipe externo)