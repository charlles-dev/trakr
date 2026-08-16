#include "TrakHaptics.h"
#include "pins.h"

void TrakHaptics::begin() {
#ifdef TRAKR_HAS_VIBRATOR
  pinMode(VIB_PIN, OUTPUT);
  digitalWrite(VIB_PIN, LOW);
#endif
#ifdef TRAKR_HAS_PASSIVE_BUZZER
  ledcSetup(BUZZER_LEDC_CHANNEL, 2000, 8);
  ledcAttachPin(BUZZER_PIN, BUZZER_LEDC_CHANNEL);
#endif
}

void TrakHaptics::vibrate(uint16_t ms) {
#ifdef TRAKR_HAS_VIBRATOR
  digitalWrite(VIB_PIN, HIGH);
  delay(ms);
  digitalWrite(VIB_PIN, LOW);
#endif
}

void TrakHaptics::stopVib() {
#ifdef TRAKR_HAS_VIBRATOR
  digitalWrite(VIB_PIN, LOW);
#endif
}

void TrakHaptics::tone(uint16_t freq, uint16_t ms) {
#ifdef TRAKR_HAS_PASSIVE_BUZZER
  ledcWriteTone(BUZZER_LEDC_CHANNEL, freq);
  if (ms > 0) {
    delay(ms);
    noTone();
  }
#else
  // Fallback ativo
  digitalWrite(BUZZER_PIN, HIGH);
  if (ms > 0) { delay(ms); digitalWrite(BUZZER_PIN, LOW); }
#endif
}

void TrakHaptics::noTone() {
#ifdef TRAKR_HAS_PASSIVE_BUZZER
  ledcWriteTone(BUZZER_LEDC_CHANNEL, 0);
#else
  digitalWrite(BUZZER_PIN, LOW);
#endif
}

void TrakHaptics::beepPattern(const char* pattern) {
  if (strcmp(pattern, "short") == 0) { tone(2000, 120); }
  else if (strcmp(pattern, "long") == 0) { tone(1500, 400); }
  else if (strcmp(pattern, "sos") == 0) {
    for (int i = 0; i < 3; i++) { tone(2000, 150); delay(150); }
    for (int i = 0; i < 3; i++) { tone(2000, 400); delay(200); }
    for (int i = 0; i < 3; i++) { tone(2000, 150); delay(150); }
  }
}
