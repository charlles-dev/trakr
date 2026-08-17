#include "TrakLed.h"
#include "pins.h"

// FastLED exige o pino no template (tempo de compilação), por isso usamos
// LED_RGB_PIN de pins.h em vez do parâmetro de begin().
static constexpr uint8_t kLedPin = LED_RGB_PIN;

void TrakLed::begin(uint8_t pin, uint8_t brightness) {
  (void)pin;
  FastLED.addLeds<WS2812B, kLedPin, GRB>(leds_, 1);
  FastLED.setBrightness(brightness);
  leds_[0] = CRGB::Black;
  FastLED.show();
}

void TrakLed::set(Color color) {
  color_ = color;
}

void TrakLed::show() {
  switch (color_) {
    case Color::OFF:
      leds_[0] = CRGB::Black;
      break;
    case Color::SCANNING:
      leds_[0] = CRGB::Blue;
      break;
    case Color::READY:
      leds_[0] = CRGB::Green;
      break;
    case Color::SYNC:
      leds_[0] = CRGB(0, 180, 255);  // ciano
      break;
    case Color::ALERT:
      // Pisca em vermelho ~2Hz (código não-bloqueante)
      leds_[0] = ((millis() / 250) % 2 == 0) ? CRGB::Red : CRGB::Black;
      break;
    case Color::FINDME:
      // "Find my finder": pisca em branco ~2Hz (código não-bloqueante)
      leds_[0] = ((millis() / 250) % 2 == 0) ? CRGB::White : CRGB::Black;
      break;
  }
  FastLED.show();
}