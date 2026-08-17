#pragma once
#include <Arduino.h>

class TrakOled {
 public:
  bool begin();
  void clear();
  void showStatus(
      int present,
      int total,
      int rssi,
      float battPct,
      bool bleConnected = false,
      const char* missingName = nullptr,
      uint8_t rfPower = 26
  );
  void showRadar(
      const char* toolName,
      const char* tag,
      int rssi,
      const char* hint,
      float battPct = -1.0f,
      uint8_t rfPower = 26
  );
  void showBoot(const char* version = "v3.0.0 TACTICAL");
  void showSync(int count, const char* msg = "SINCRONIZANDO");
  void showBeaconAlert(int remainingSec);

 private:
  bool ok_ = false;
  uint8_t sweepAngleStep_ = 0;

  void drawTopBar(const char* title, float battPct, bool bleConnected, uint8_t rfPower = 26);
  void drawBatteryIcon(int x, int y, int pct);
  void drawBleIcon(int x, int y);
  void drawRfBadge(int x, int y, uint8_t dbm);
  void drawChamferedBox(int x, int y, int w, int h, int cut = 3, bool fill = false);
  void drawSegmentedBar(int x, int y, int w, int h, int percent);
};
