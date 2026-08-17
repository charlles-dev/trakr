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
  if (err || !doc["tools"].is<JsonArray>()) {
    // Arquivo corrompido/incompleto: tenta restaurar do backup.
    String bak = String(path) + ".bak";
    Serial.printf("[TRAKR] ERRO JSON (inventory): %s\n",
                  err ? err.c_str() : "schema invalido");
    file = fs.open(bak, "r");
    if (!file) {
      Serial.println("[TRAKR] Sem backup disponivel — inventario vazio");
      return false;
    }
    err = deserializeJson(doc, file);
    file.close();
    if (err || !doc["tools"].is<JsonArray>()) {
      Serial.println("[TRAKR] Backup tambem corrompido — inventario vazio");
      return false;
    }
    Serial.println("[TRAKR] Inventario restaurado do backup (.bak)");
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
  JsonArray arr = doc["tools"].to<JsonArray>();
  for (const auto& t : tools_) {
    JsonObject o = arr.add<JsonObject>();
    o["id"] = t.id;
    o["name"] = t.name;
    o["tag"] = t.epc;
    o["present"] = t.present;
  }

  // Gravação atômica estilo "tmp + rename": o arquivo principal só é
  // substituído após a escrita completa, e o anterior vira .bak (backup
  // contra corrupção por perda de energia no meio da gravação).
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

void TrakInventory::sweep(const std::vector<String>& readEpcs) {
  newly_missing_.clear();
  newly_found_.clear();

  // Histerese: só marca ausente após kMissingThreshold varreduras
  // consecutivas sem leitura; volta a presente imediatamente quando lida.
  for (auto& t : tools_) {
    const bool wasPresent = t.present;
    bool read = false;
    if (!t.epc.isEmpty()) {
      for (const auto& epc : readEpcs) {
        if (epc.equalsIgnoreCase(t.epc)) {
          read = true;
          break;
        }
      }
    }

    bool nowPresent;
    if (read) {
      t.miss_streak = 0;
      nowPresent = true;
    } else {
      t.miss_streak++;
      nowPresent = (t.miss_streak < TrakInventory::kMissingThreshold) && t.present;
    }
    t.present = nowPresent;

    if (wasPresent && !nowPresent) {
      newly_missing_.push_back(&t);  // cruzou o limiar: sumiu de verdade
    } else if (!wasPresent && nowPresent) {
      newly_found_.push_back(&t);    // voltou a ser lida
    }
  }
}

bool TrakInventory::addTool(const String& name, const String& epc) {
  for (const auto& t : tools_) {
    if (t.epc.equalsIgnoreCase(epc)) return false;  // EPC duplicado
  }

  // Próximo id livre (sequencial a partir de 1).
  uint32_t next = 1;
  for (const auto& t : tools_) {
    uint32_t v = (uint32_t)strtoul(t.id.c_str(), nullptr, 10);
    if (v >= next) next = v + 1;
  }

  TrakTool tool;
  tool.id = String(next);
  tool.name = name;
  tool.epc = epc;
  tool.present = true;  // presumido presente até a próxima varredura
  Serial.printf("[TRAKR] Tool adicionada: id=%s nome=%s tag=%s\n",
                tool.id.c_str(), tool.name.c_str(), tool.epc.c_str());

  tools_.push_back(std::move(tool));
  newly_missing_.clear();  // vetor reaplicou; ponteiros antigos inválidos
  return true;
}

bool TrakInventory::removeTool(const String& id) {
  for (auto it = tools_.begin(); it != tools_.end(); ++it) {
    if (it->id == id) {
      Serial.printf("[TRAKR] Tool removida: id=%s nome=%s\n",
                    it->id.c_str(), it->name.c_str());
      tools_.erase(it);
      newly_missing_.clear();
      return true;
    }
  }
  return false;
}

bool TrakInventory::removeToolByEpc(const String& epc) {
  for (auto it = tools_.begin(); it != tools_.end(); ++it) {
    if (it->epc.equalsIgnoreCase(epc)) {
      Serial.printf("[TRAKR] Tool removida por EPC: nome=%s\n",
                    it->name.c_str());
      tools_.erase(it);
      newly_missing_.clear();
      return true;
    }
  }
  return false;
}

String TrakInventory::toJsonString() const {
  String out;
  JsonDocument doc;
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
