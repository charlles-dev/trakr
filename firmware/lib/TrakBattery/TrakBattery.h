#pragma once
#include <Arduino.h>

struct BatteryInfo {
  float voltage;
  float current;
  float percent;
  bool valid;
};

class TrakBattery {
 public:
  bool begin();
  BatteryInfo read();
  bool isLow() const { return last_.percent < 15.0f && last_.valid; }

 private:
  BatteryInfo last_ = {0, 0, 100, false};
#ifdef TRAKR_HAS_INA219
  bool ina_ok_ = false;
#endif
};
