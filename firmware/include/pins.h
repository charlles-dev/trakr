#pragma once

// ===== TRK-Finder - Mapeamento de Pinos (ESP32-WROOM-32) =====
// Referência: docs/hardware/README.md
//
// Rastreador Portátil: sem tampa, botão físico para acordar e disparar a
// varredura (GPIO 33 é RTC, serve para ext0 wake).

// --- Leitor UHF YRM100 (UART2) ---
#define YRM100_UART_NUM 2
#define YRM100_TX_PIN   17 // ESP32 TX2 -> RX do YRM100
#define YRM100_RX_PIN   16 // ESP32 RX2 <- TX do YRM100
#define YRM100_BAUD     115200

// --- Botão físico ---
// Segura o ext0 wake e dispara a varredura. Pull-down interno + wake em HIGH
// (botão entre o pino e 3.3V).
#define BUTTON_PIN       33 // Suporta wakeup RTC (ext0)
#define BUTTON_WAKE_LEVEL HIGH

// --- Feedback ---
#define BUZZER_PIN      25 // Alarme local
#define LED_RGB_PIN     26 // WS2812B (light pipe externo)

// --- Controle de energia do YRM100 (EN) ---
// Só use se o seu módulo YRM100 tiver pino EN/PE (varia por fabricante).
// Se não tiver EN, desligar o UART (disablePower) já reduz o consumo; o
// consumo real deve ser medido com multímetro (ver docs).
#define YRM100_EN_PIN   14