#pragma once
#include <Arduino.h>

class TrakOled {
 public:
  bool begin();
  void clear();
  void showStatus(int present, int total, int rssi, float battPct);
  void showRadar(const char* tag, int rssi, const char* hint);
  void showBoot();

 private:
  bool ok_ = false;
};
