#pragma once

// ===== Trakr - Inventário em memória + persistência LittleFS =====
// O firmware é a fonte da verdade. O inventário fica em data/inventory.json
// e é espelhado para o app via BLE (mesmo formato JSON).

#include <Arduino.h>
#include <FS.h>
#include <vector>

struct TrakTool {
  String id;    // id curto no inventário (ex: "01")
  String name;  // nome exibível
  String epc;   // EPC da tag UHF (24 chars hex, ex: "E28011606000020400000001")
  bool present = true;
};

class TrakInventory {
 public:
  bool load(fs::FS& fs, const char* path);
  bool save(fs::FS& fs, const char* path) const;

  size_t size() const { return tools_.size(); }
  const std::vector<TrakTool>& tools() const { return tools_; }

  // Ferramentas que estavam presentes e sumiram na última varredura.
  const std::vector<const TrakTool*>& newlyMissing() const { return newly_missing_; }

  // Marca presença/ausência conforme os EPCs lidos pelo YRM100.
  void sweep(const std::vector<String>& readEpcs);

  // Serializa o inventário no mesmo formato do inventory.json.
  String toJsonString() const;

  void replaceTools(std::vector<TrakTool>&& tools) { tools_ = std::move(tools); }

 private:
  std::vector<TrakTool> tools_;
  std::vector<const TrakTool*> newly_missing_;
};
