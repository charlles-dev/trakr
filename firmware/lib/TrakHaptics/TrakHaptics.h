#pragma once
#include <Arduino.h>

class TrakHaptics {
 public:
  void begin();
  void vibrate(uint16_t ms);
  void stopVib();
  void tone(uint16_t freq, uint16_t ms = 0);
  void noTone();
  void beepPattern(const char* pattern); // short, long, sos
};
