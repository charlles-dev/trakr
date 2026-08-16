#include "TrakOled.h"
#include "pins.h"

#ifdef TRAKR_HAS_OLED
#include <Wire.h>
#include <Adafruit_GFX.h>
#include <Adafruit_SSD1306.h>
static Adafruit_SSD1306 display(OLED_WIDTH, OLED_HEIGHT, &Wire, -1);
#endif

bool TrakOled::begin() {
#ifdef TRAKR_HAS_OLED
  Wire.begin(I2C_SDA_PIN, I2C_SCL_PIN, I2C_FREQ);
  ok_ = display.begin(SSD1306_SWITCHCAPVCC, OLED_ADDR);
  if (ok_) {
    display.clearDisplay();
    display.setTextSize(1);
    display.setTextColor(SSD1306_WHITE);
    display.setCursor(0, 0);
    display.println("TRK-FINDER");
    display.display();
    Serial.println("[TRAKR] OLED ok");
  } else {
    Serial.println("[TRAKR] OLED fail");
  }
  return ok_;
#else
  return false;
#endif
}

void TrakOled::clear() {
#ifdef TRAKR_HAS_OLED
  if (!ok_) return;
  display.clearDisplay();
  display.display();
#endif
}

void TrakOled::showStatus(int present, int total, int rssi, float battPct) {
#ifdef TRAKR_HAS_OLED
  if (!ok_) return;
  display.clearDisplay();
  display.setCursor(0, 0);
  display.printf("TRK %d/%d\nRSSI %d dBm\nBatt %.0f%%\n", present, total, rssi, battPct);
  display.display();
#endif
}

void TrakOled::showRadar(const char* tag, int rssi, const char* hint) {
#ifdef TRAKR_HAS_OLED
  if (!ok_) return;
  display.clearDisplay();
  display.setCursor(0, 0);
  display.println("RADAR");
  display.printf("%s\n%d dBm\n%s\n", tag, rssi, hint);
  display.display();
#endif
}

void TrakOled::showBoot() {
#ifdef TRAKR_HAS_OLED
  if (!ok_) return;
  display.clearDisplay();
  display.setCursor(0, 0);
  display.println("Booting TRK\nFinder...");
  display.display();
#endif
}
