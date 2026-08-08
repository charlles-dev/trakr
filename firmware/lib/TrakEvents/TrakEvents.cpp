#include "TrakEvents.h"

#include <ArduinoJson.h>

bool TrakEvents::load(fs::FS& fs, const char* path) {
  File file = fs.open(path, "r");
  if (!file) {
    Serial.println("[TRAKR] events.json nao encontrado — histórico vazio");
    return false;
  }

  JsonDocument doc;
  DeserializationError err = deserializeJson(doc, file);
  file.close();
  if (err) {
    Serial.printf("[TRAKR] ERRO JSON (events): %s\n", err.c_str());
    return false;
  }

  events_.clear();
  for (JsonObject e : doc.as<JsonArray>()) {
    Event ev;
    ev.ts = e["ts"] | (uint32_t)0;
    ev.type = e["type"].as<String>();
    ev.toolId = e["tool_id"].as<String>();
    ev.toolName = e["name"].as<String>();
    events_.push_back(std::move(ev));
    if (events_.size() >= kMaxEvents) break;
  }
  Serial.printf("[TRAKR] Historico carregado: %u eventos\n", events_.size());
  return true;
}

bool TrakEvents::save(fs::FS& fs, const char* path) const {
  JsonDocument doc;
  JsonArray arr = doc.to<JsonArray>();
  for (const auto& ev : events_) {
    JsonObject o = arr.add<JsonObject>();
    o["ts"] = ev.ts;
    o["type"] = ev.type;
    if (!ev.toolId.isEmpty()) o["tool_id"] = ev.toolId;
    if (!ev.toolName.isEmpty()) o["name"] = ev.toolName;
  }

  File file = fs.open(path, "w");
  if (!file) return false;
  bool ok = serializeJson(doc, file) > 0;
  file.close();
  return ok;
}

String TrakEvents::add(const String& type, const String& toolId,
                       const String& toolName) {
  Event ev;
  ev.ts = millis();
  ev.type = type;
  ev.toolId = toolId;
  ev.toolName = toolName;
  events_.insert(events_.begin(), std::move(ev));

  if (events_.size() > kMaxEvents) events_.resize(kMaxEvents);

  JsonDocument doc;
  JsonObject o = doc.to<JsonObject>();
  o["ts"] = ev.ts;
  o["type"] = ev.type;
  if (!ev.toolId.isEmpty()) o["tool_id"] = ev.toolId;
  if (!ev.toolName.isEmpty()) o["name"] = ev.toolName;
  String json;
  serializeJson(doc, json);
  return json;
}

String TrakEvents::toJsonString() const {
  String out;
  JsonDocument doc;
  JsonArray arr = doc.to<JsonArray>();
  for (const auto& ev : events_) {
    JsonObject o = arr.add<JsonObject>();
    o["ts"] = ev.ts;
    o["type"] = ev.type;
    if (!ev.toolId.isEmpty()) o["tool_id"] = ev.toolId;
    if (!ev.toolName.isEmpty()) o["name"] = ev.toolName;
  }
  serializeJson(doc, out);
  return out;
}