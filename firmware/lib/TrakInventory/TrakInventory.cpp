#include "TrakInventory.h"

#include <ArduinoJson.h>

bool TrakInventory::load(fs::FS& fs, const char* path) {
  File file = fs.open(path, "r");
  if (!file) {
    Serial.println("[TRAKR] ERRO: inventory.json nao encontrado");
    return false;
  }

  JsonDocument doc;
  DeserializationError err = deserializeJson(doc, file);
  file.close();
  if (err) {
    Serial.printf("[TRAKR] ERRO JSON: %s\n", err.c_str());
    return false;
  }

  tools_.clear();
  for (JsonObject t : doc["tools"].as<JsonArray>()) {
    TrakTool tool;
    tool.id = t["id"].as<String>();
    tool.name = t["name"].as<String>();
    tool.epc = t["tag"].as<String>();
    tool.present = t["present"] | true;
    tools_.push_back(std::move(tool));
  }

  Serial.printf("[TRAKR] Inventario carregado: %u ferramentas\n", tools_.size());
  return true;
}

bool TrakInventory::save(fs::FS& fs, const char* path) const {
  JsonDocument doc;
  doc["toolbox"] = "Trakr";
  JsonArray arr = doc["tools"].to<JsonArray>();
  for (const auto& t : tools_) {
    JsonObject o = arr.add<JsonObject>();
    o["id"] = t.id;
    o["name"] = t.name;
    o["tag"] = t.epc;
    o["present"] = t.present;
  }

  File file = fs.open(path, "w");
  if (!file) return false;
  bool ok = serializeJson(doc, file) > 0;
  file.close();
  return ok;
}

void TrakInventory::sweep(const std::vector<String>& readEpcs) {
  newly_missing_.clear();

  for (auto& t : tools_) {
    bool wasPresent = t.present;
    bool read = false;
    if (!t.epc.isEmpty()) {
      for (const auto& epc : readEpcs) {
        if (epc.equalsIgnoreCase(t.epc)) {
          read = true;
          break;
        }
      }
    }
    t.present = read;
    if (wasPresent && !read) {
      newly_missing_.push_back(&t);
    }
  }
}

String TrakInventory::toJsonString() const {
  String out;
  JsonDocument doc;
  doc["toolbox"] = "Trakr";
  JsonArray arr = doc["tools"].to<JsonArray>();
  for (const auto& t : tools_) {
    JsonObject o = arr.add<JsonObject>();
    o["id"] = t.id;
    o["name"] = t.name;
    o["tag"] = t.epc;
    o["present"] = t.present;
  }
  serializeJson(doc, out);
  return out;
}
