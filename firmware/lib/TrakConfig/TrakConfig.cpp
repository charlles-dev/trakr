#include "TrakConfig.h"

#include <ArduinoJson.h>
#include <mbedtls/sha256.h>

bool TrakConfig::load(fs::FS& fs, const char* path) {
  File file = fs.open(path, "r");
  if (!file) {
    Serial.println("[TRAKR] config.json nao encontrado — usando padroes");
    return false;
  }

  JsonDocument doc;
  DeserializationError err = deserializeJson(doc, file);
  file.close();
  if (err) {
    Serial.printf("[TRAKR] ERRO JSON (config): %s\n", err.c_str());
    return false;
  }

  listen_ms_ = doc["listen_ms"] | listen_ms_;
  radar_ms_ = doc["radar_ms"] | radar_ms_;
  beep_ = doc["beep"] | beep_;
  clock_delta_ms_ = doc["clock_delta_ms"] | (int64_t)0;
  tx_power_dbm_ = doc["tx_power_dbm"] | tx_power_dbm_;
  rssi_offset_ = doc["rssi_offset"] | rssi_offset_;
  rssi_threshold_ = doc["rssi_threshold"] | rssi_threshold_;
  String env = doc["env_profile"] | env_profile_;
  env_profile_ = env;
  String ph = doc["pin_hash"] | "";
  if (ph.length() == 64) pin_hash_ = ph;
  Serial.printf("[TRAKR] Config: escuta %lu radar %lu bip %s clock_delta %lld pin %s tx %u rssi_off %d th %d env %s\n",
                listen_ms_, radar_ms_, beep_ ? "on" : "off", (long long)clock_delta_ms_,
                hasPin() ? "SET" : "none", tx_power_dbm_, rssi_offset_, rssi_threshold_, env_profile_.c_str());
  return true;
}

bool TrakConfig::save(fs::FS& fs, const char* path) const {
  JsonDocument doc;
  doc["listen_ms"] = listen_ms_;
  doc["radar_ms"] = radar_ms_;
  doc["beep"] = beep_;
  doc["clock_delta_ms"] = clock_delta_ms_;
  doc["tx_power_dbm"] = tx_power_dbm_;
  doc["rssi_offset"] = rssi_offset_;
  doc["rssi_threshold"] = rssi_threshold_;
  doc["env_profile"] = env_profile_;
  if (hasPin()) doc["pin_hash"] = pin_hash_;

  File file = fs.open(path, "w");
  if (!file) return false;
  bool ok = serializeJson(doc, file) > 0;
  file.close();
  return ok;
}

String TrakConfig::toJsonString() const {
  String out;
  JsonDocument doc;
  doc["listen_ms"] = listen_ms_;
  doc["radar_ms"] = radar_ms_;
  doc["beep"] = beep_;
  doc["clock_delta_ms"] = clock_delta_ms_;
  doc["tx_power_dbm"] = tx_power_dbm_;
  doc["rssi_offset"] = rssi_offset_;
  doc["rssi_threshold"] = rssi_threshold_;
  doc["env_profile"] = env_profile_;
  doc["has_pin"] = hasPin();
  serializeJson(doc, out);
  return out;
}

String TrakConfig::hashPin(const String& pin) {
  unsigned char hash[32];
  mbedtls_sha256_context ctx;
  mbedtls_sha256_init(&ctx);
  mbedtls_sha256_starts(&ctx, 0);
  mbedtls_sha256_update(&ctx, reinterpret_cast<const unsigned char*>(pin.c_str()), pin.length());
  mbedtls_sha256_finish(&ctx, hash);
  mbedtls_sha256_free(&ctx);
  char out[65];
  for (int i = 0; i < 32; i++) {
    sprintf(out + i * 2, "%02x", hash[i]);
  }
  out[64] = '\0';
  return String(out);
}

bool TrakConfig::verifyPin(const String& pin) const {
  if (!hasPin()) return true;
  String h = hashPin(pin);
  if (h.length() != pin_hash_.length()) return false;
  // Comparação em tempo constante para evitar timing attack.
  volatile int diff = 0;
  for (size_t i = 0; i < h.length(); i++) {
    diff |= h[i] ^ pin_hash_[i];
  }
  return diff == 0;
}