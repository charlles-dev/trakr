#include "TrakEvents.h"

#include <ArduinoJson.h>
#include <time.h>

bool TrakEvents::load(fs::FS& fs, const char* path) {
  File file = fs.open(path, "r");
  if (!file) {
    Serial.println("[TRAKR] events.json nao encontrado — histórico vazio");
    return false;
  }

  JsonDocument doc;
  DeserializationError err = deserializeJson(doc, file);
  file.close();
  if (err || !doc.is<JsonArray>()) {
    // Corrompido: tenta restaurar do backup antes de começar vazio.
    String bak = String(path) + ".bak";
    Serial.printf("[TRAKR] ERRO JSON (events): %s\n",
                  err ? err.c_str() : "schema invalido");
    file = fs.open(bak, "r");
    if (!file) {
      Serial.println("[TRAKR] Sem backup disponivel — historico vazio");
      return false;
    }
    err = deserializeJson(doc, file);
    file.close();
    if (err || !doc.is<JsonArray>()) {
      Serial.println("[TRAKR] Backup tambem corrompido — historico vazio");
      return false;
    }
    Serial.println("[TRAKR] events.json restaurado do backup (.bak)");
  }

  events_.clear();
  for (JsonObject e : doc.as<JsonArray>()) {
    Event ev;
    ev.ts = e["ts"] | (uint64_t)0;
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

  // Gravação atômica "tmp + rename" com backup (.bak), contra corrupção
  // por perda de energia no meio da gravação (ver TrakInventory::save).
  const String tmp = String(path) + ".tmp";
  const String bak = String(path) + ".bak";

  File file = fs.open(tmp, "w");
  if (!file) return false;
  const bool ok = serializeJson(doc, file) > 0;
  file.close();
  if (!ok) {
    fs.remove(tmp);
    return false;
  }
  if (fs.exists(path)) fs.rename(path, bak);
  const bool promoted = fs.rename(tmp, path);
  if (!promoted) fs.remove(tmp);
  return promoted;
}

String TrakEvents::monthKeyForTs(uint64_t ts) {
  // ts < 1e12 = millis() relativo ao boot (sem epoch)
  if (ts < 1700000000000ULL) return "boot";
  time_t sec = (time_t)(ts / 1000);
  struct tm tm;
  gmtime_r(&sec, &tm);
  char buf[7];
  snprintf(buf, sizeof(buf), "%04d%02d", tm.tm_year + 1900, tm.tm_mon + 1);
  return String(buf);
}

String TrakEvents::archivePathForMonth(const String& monthKey) {
  return "/events_" + monthKey + ".json";
}

bool TrakEvents::archiveEvent(fs::FS& fs, const Event& ev) const {
  String key = monthKeyForTs(ev.ts);
  String path = archivePathForMonth(key);

  JsonDocument doc;
  File file = fs.open(path, "r");
  if (file) {
    DeserializationError err = deserializeJson(doc, file);
    file.close();
    if (err) doc.clear();
  }
  if (!doc.is<JsonArray>()) doc.to<JsonArray>();

  JsonArray arr = doc.as<JsonArray>();
  JsonObject o = arr.add<JsonObject>();
  o["ts"] = ev.ts;
  o["type"] = ev.type;
  if (!ev.toolId.isEmpty()) o["tool_id"] = ev.toolId;
  if (!ev.toolName.isEmpty()) o["name"] = ev.toolName;

  file = fs.open(path + ".tmp", "w");
  if (!file) return false;
  bool ok = serializeJson(doc, file) > 0;
  file.close();
  if (!ok) return false;
  // Atômico: promove o .tmp para o arquivo do mês (backup implícito no .tmp).
  if (fs.exists(path)) fs.remove(path);
  ok = fs.rename(path + ".tmp", path);
  if (ok) {
    Serial.printf("[TRAKR] Evento arquivado em %s (%s)\n", path.c_str(), key.c_str());
  }
  return ok;
}

String TrakEvents::add(fs::FS& fs, const char* basePath, const String& type,
                       const String& toolId, const String& toolName) {
  Event ev;
  ev.ts = nowMs();
  ev.type = type;
  ev.toolId = toolId;
  ev.toolName = toolName;
  events_.insert(events_.begin(), ev);

  // Rotação: arquiva excedente por mês, sem perda
  while (events_.size() > kMaxEvents) {
    Event oldest = events_.back();
    archiveEvent(fs, oldest);
    events_.pop_back();
  }
  save(fs, basePath);

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

String TrakEvents::add(const String& type, const String& toolId,
                       const String& toolName) {
  Event ev;
  ev.ts = nowMs();
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

String TrakEvents::listArchivesJson(fs::FS& fs) const {
  JsonDocument doc;
  JsonArray arr = doc.to<JsonArray>();
  File root = fs.open("/");
  if (!root) {
    String out;
    serializeJson(doc, out);
    return out;
  }
  File file = root.openNextFile();
  while (file) {
    String name = String(file.name());
    if (name.startsWith("/events_") && name.endsWith(".json") && name != "/events.json") {
      String key = name.substring(8, name.length() - 5);  // YYYYMM ou boot
      size_t count = 0;
      File f = fs.open(name, "r");
      if (f) {
        JsonDocument d;
        if (!deserializeJson(d, f)) {
          if (d.is<JsonArray>()) count = d.as<JsonArray>().size();
        }
        f.close();
      }
      JsonObject o = arr.add<JsonObject>();
      o["month"] = key;
      o["count"] = count;
      o["path"] = name;
    }
    file = root.openNextFile();
  }
  String out;
  serializeJson(doc, out);
  return out;
}

String TrakEvents::archiveJsonForMonth(fs::FS& fs, const String& monthKey) const {
  String path = archivePathForMonth(monthKey);
  File file = fs.open(path, "r");
  if (!file) return "[]";
  String content = file.readString();
  file.close();
  if (content.isEmpty()) return "[]";
  return content;
}