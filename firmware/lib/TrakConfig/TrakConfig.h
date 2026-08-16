#pragma once

// ===== Trakr - Configurações do TRK-Finder (LittleFS) =====
// Persistidas em config.json. O app lê/altera via comandos BLE
// get_config / set_config. Padrões: 30 s de espera, 120 s de radar, bip on.
// O PIN de acesso é armazenado como SHA-256 hex (64 chars) — nunca em claro.

#include <Arduino.h>
#include <FS.h>

class TrakConfig {
 public:
  bool load(fs::FS& fs, const char* path);
  bool save(fs::FS& fs, const char* path) const;

  unsigned long listenMs() const { return listen_ms_; }
  unsigned long radarMs() const { return radar_ms_; }
  bool beep() const { return beep_; }

  // Delta do relógio sincronizado: epoch_ms - millis() no momento do sync
  // (0 = sem sync; ts dos eventos fica relativo ao boot).
  int64_t clockDeltaMs() const { return clock_delta_ms_; }

  // PIN de acesso (hash SHA-256 hex, 64 chars). Vazio = sem proteção.
  bool hasPin() const { return pin_hash_.length() == 64; }
  String pinHash() const { return pin_hash_; }

  void setListenMs(unsigned long v) { listen_ms_ = v; }
  void setRadarMs(unsigned long v) { radar_ms_ = v; }
  void setBeep(bool v) { beep_ = v; }
  void setClockDeltaMs(int64_t v) { clock_delta_ms_ = v; }
  void setPinHash(const String& h) { pin_hash_ = h; }
  void clearPin() { pin_hash_ = ""; }

  // RF e calibração
  uint8_t txPowerDbm() const { return tx_power_dbm_; }
  int8_t rssiOffset() const { return rssi_offset_; }
  int8_t rssiThreshold() const { return rssi_threshold_; }
  String envProfile() const { return env_profile_; }

  void setTxPowerDbm(uint8_t v) { tx_power_dbm_ = v; }
  void setRssiOffset(int8_t v) { rssi_offset_ = v; }
  void setRssiThreshold(int8_t v) { rssi_threshold_ = v; }
  void setEnvProfile(const String& s) { env_profile_ = s; }

  // Gera SHA-256 hex do PIN (sem salvar).
  static String hashPin(const String& pin);
  // Verifica PIN em claro contra o hash armazenado (comparação em tempo constante).
  bool verifyPin(const String& pin) const;

  // Serializa config (sem expor hash).
  String toJsonString() const;

 private:
  unsigned long listen_ms_ = 30000;
  unsigned long radar_ms_ = 120000;
  bool beep_ = true;
  int64_t clock_delta_ms_ = 0;
  String pin_hash_ = "";
  uint8_t tx_power_dbm_ = 26;
  int8_t rssi_offset_ = 0;
  int8_t rssi_threshold_ = -70;
  String env_profile_ = "default";
};