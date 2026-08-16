#pragma once

// ===== Trakr - Configurações do TRK-Finder (LittleFS) =====
// Persistidas em config.json. O app lê/altera via comandos BLE
// get_config / set_config. Padrões: 30 s de espera, 120 s de radar, bip on.

#include <Arduino.h>
#include <FS.h>

class TrakConfig {
 public:
  bool load(fs::FS& fs, const char* path);
  bool save(fs::FS& fs, const char* path) const;

  unsigned long listenMs() const { return listen_ms_; }
  unsigned long radarMs() const { return radar_ms_; }
  bool beep() const { return beep_; }

  void setListenMs(unsigned long v) { listen_ms_ = v; }
  void setRadarMs(unsigned long v) { radar_ms_ = v; }
  void setBeep(bool v) { beep_ = v; }

  // Serializa {"listen_ms":...,"radar_ms":...,"beep":...}
  String toJsonString() const;

 private:
  unsigned long listen_ms_ = 30000;
  unsigned long radar_ms_ = 120000;
  bool beep_ = true;
};