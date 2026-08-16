#include "TrakSensors.h"
#include "pins.h"

#ifdef TRAKR_HAS_BME280
#include <Wire.h>
#include <Adafruit_BME280.h>
static Adafruit_BME280 bme;
#endif

#ifdef TRAKR_HAS_MPU6050
#include <Wire.h>
#include <MPU6050.h>
static MPU6050 mpu;
#endif

bool TrakSensors::begin() {
  bool any = false;
#ifdef TRAKR_HAS_BME280
  Wire.begin(I2C_SDA_PIN, I2C_SCL_PIN, I2C_FREQ);
  bme_ok_ = bme.begin(BME280_ADDR);
  Serial.printf("[TRAKR] BME280 %s\n", bme_ok_ ? "ok" : "fail");
  any |= bme_ok_;
#endif
#ifdef TRAKR_HAS_MPU6050
  Wire.begin(I2C_SDA_PIN, I2C_SCL_PIN, I2C_FREQ);
  mpu.initialize();
  mpu_ok_ = mpu.testConnection();
  Serial.printf("[TRAKR] MPU6050 %s\n", mpu_ok_ ? "ok" : "fail");
  any |= mpu_ok_;
#ifdef MPU_INT_PIN
  pinMode(MPU_INT_PIN, INPUT);
#endif
#endif
  return any;
}

EnvData TrakSensors::readBME() {
  EnvData d = {0, 0, 0, false};
#ifdef TRAKR_HAS_BME280
  if (!bme_ok_) return d;
  d.temp = bme.readTemperature();
  d.hum = bme.readHumidity();
  d.press = bme.readPressure() / 100.0F;
  d.valid = true;
#endif
  return d;
}

ImuData TrakSensors::readIMU() {
  ImuData d = {0, 0, 0, 0, 0, 0, false, false};
#ifdef TRAKR_HAS_MPU6050
  if (!mpu_ok_) return d;
  int16_t ax, ay, az, gx, gy, gz;
  mpu.getMotion6(&ax, &ay, &az, &gx, &gy, &gz);
  d.ax = ax / 16384.0f;
  d.ay = ay / 16384.0f;
  d.az = az / 16384.0f;
  d.gx = gx / 131.0f;
  d.gy = gy / 131.0f;
  d.gz = gz / 131.0f;
  float mag = sqrt(d.ax * d.ax + d.ay * d.ay + d.az * d.az);
  d.moving = fabs(mag - 1.0f) > 0.2f;
  d.valid = true;
#endif
  return d;
}

bool TrakSensors::isMoving() {
  ImuData d = readIMU();
  return d.moving;
}
