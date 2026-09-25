#include "TrakBattery.h"
#include "pins.h"

#ifdef TRAKR_HAS_INA219
#include <Wire.h>
#include <Adafruit_INA219.h>
static Adafruit_INA219 ina219(INA219_ADDR);
#endif

bool TrakBattery::begin() {
#ifdef TRAKR_HAS_INA219
  Wire.begin(I2C_SDA_PIN, I2C_SCL_PIN, I2C_FREQ);
  ina_ok_ = ina219.begin();
  Serial.printf("[TRAKR] INA219 %s\n", ina_ok_ ? "ok" : "fail");
  return ina_ok_;
#else
  // Fallback: divisor de tensão no ADC (pino 34 por exemplo)
  return true;
#endif
}

BatteryInfo TrakBattery::read() {
#ifdef TRAKR_HAS_INA219
  if (!ina_ok_) { last_.valid = false; return last_; }
  float busV = ina219.getBusVoltage_V();
  float shuntV = ina219.getShuntVoltage_mV() / 1000.0f;
  float current = ina219.getCurrent_mA();
  float voltage = busV + shuntV;
  // Curva aproximada de descarga para célula Li-ion 18650
  float pct = 0.0f;
  if (voltage >= 4.2f) pct = 100.0f;
  else if (voltage >= 4.0f) pct = 80.0f + 20.0f * ((voltage - 4.0f) / 0.2f);
  else if (voltage >= 3.9f) pct = 60.0f + 20.0f * ((voltage - 3.9f) / 0.1f);
  else if (voltage >= 3.8f) pct = 40.0f + 20.0f * ((voltage - 3.8f) / 0.1f);
  else if (voltage >= 3.7f) pct = 20.0f + 20.0f * ((voltage - 3.7f) / 0.1f);
  else if (voltage >= 3.5f) pct = 10.0f + 10.0f * ((voltage - 3.5f) / 0.2f);
  else if (voltage >= 3.3f) pct = 5.0f + 5.0f * ((voltage - 3.3f) / 0.2f);
  else if (voltage > 3.0f) pct = 5.0f * ((voltage - 3.0f) / 0.3f);
  else pct = 0.0f;
  
  pct = constrain(pct, 0, 100);
  last_ = {voltage, current, pct, true};
  return last_;
#else
  // Simula leitura ADC: 3.7V nominal
  last_ = {3.7f, 0, 58.0f, true};
  return last_;
#endif
}
