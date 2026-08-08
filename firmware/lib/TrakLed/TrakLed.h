#pragma once

// ===== Trakr - LED RGB WS2812B (light pipe) =====
// Controle da cor do LED de status conforme a máquina de estados:
//   IDLE      -> desligado
//   SCANNING  -> azul (varredura UHF em andamento)
//   READY     -> verde (tudo presente)
//   SYNC      -> ciano (janela BLE de sincronização)
//   ALERT     -> vermelho (ferramenta ausente)

#include <Arduino.h>
#include <FastLED.h>

class TrakLed {
 public:
  enum class Color : uint8_t {
    OFF,
    SCANNING,
    READY,
    SYNC,
    ALERT,
  };

  void begin(uint8_t pin, uint8_t brightness = 24);

  // Define a cor de forma não-bloqueante; ALERT pisca sozinho no show().
  void set(Color color);

  // Envia o frame para o LED (chame no loop, ~30fps).
  void show();

 private:
  CRGB leds_[1];
  Color color_ = Color::OFF;
};