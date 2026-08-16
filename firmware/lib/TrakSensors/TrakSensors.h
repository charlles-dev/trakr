#pragma once
#include <Arduino.h>

struct EnvData {
  float temp;
  float hum;
  float press;
  bool valid;
};

struct ImuData {
  float ax, ay, az;
  float gx, gy, gz;
  bool moving;
  bool valid;
};

class TrakSensors {
 public:
  bool begin();
  EnvData readBME();
  ImuData readIMU();
  bool isMoving();

 private:
  bool bme_ok_ = false;
  bool mpu_ok_ = false;
};
