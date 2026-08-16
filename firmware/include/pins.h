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

// --- I2C (OLED, INA219, BME280, MPU6050) ---
#define I2C_SDA_PIN     21
#define I2C_SCL_PIN     22
#define I2C_FREQ        400000

// --- OLED SSD1306 0.96" (opcional) ---
#ifdef TRAKR_HAS_OLED
#define OLED_ADDR       0x3C
#define OLED_WIDTH      128
#define OLED_HEIGHT     64
#endif

// --- INA219 Monitor de bateria (opcional) ---
#ifdef TRAKR_HAS_INA219
#define INA219_ADDR     0x40
#endif

// --- BME280 (opcional) ---
#ifdef TRAKR_HAS_BME280
#define BME280_ADDR     0x76
#endif

// --- MPU6050 / IMU (opcional) ---
#ifdef TRAKR_HAS_MPU6050
#define MPU_ADDR        0x68
#define MPU_INT_PIN     35 // interrupção, RTC-capable para wake
#endif

// --- Botão físico secundário (opcional) ---
#ifdef TRAKR_HAS_BTN2
#define BUTTON2_PIN     32
#define BUTTON2_WAKE_LEVEL HIGH
#endif

// --- Motor vibrador (opcional) ---
#ifdef TRAKR_HAS_VIBRATOR
#define VIB_PIN         27
#endif

// --- Buzzer passivo (opcional, tons via LEDC) ---
// Reusa BUZZER_PIN com PWM quando TRAKR_HAS_PASSIVE_BUZZER definido
#define BUZZER_LEDC_CHANNEL 0
#define BUZZER_LEDC_TIMER   0

// --- 18650 troca rápida (suporte com trava, sem solda) ---
// Nenhum pino extra, apenas mecânica + divisor de tensão existente