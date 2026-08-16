#include "TrakConfig.h"

#include <ArduinoJson.h>

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
  Serial.printf("[TRAKR] Config carregada: escuta %lu ms, radar %lu ms, bip %s\n",
                listen_ms_, radar_ms_, beep_ ? "on" : "off");
  return true;
}

bool TrakConfig::save(fs::FS& fs, const char* path) const {
  JsonDocument doc;
  doc["listen_ms"] = listen_ms_;
  doc["radar_ms"] = radar_ms_;
  doc["beep"] = beep_;

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
  serializeJson(doc, out);
  return out;
}