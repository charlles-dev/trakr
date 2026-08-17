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
  uint8_t miss_streak = 0;  // varreduras consecutivas sem ler a tag (RAM)
};

class TrakInventory {
 public:
  // Varreduras consecutivas sem leitura antes de marcar a ferramenta como
  // ausente (histerese anti-falso-positivo: RF é ruidoso).
  static constexpr uint8_t kMissingThreshold = 3;

  bool load(fs::FS& fs, const char* path);
  bool save(fs::FS& fs, const char* path) const;

  size_t size() const { return tools_.size(); }
  const std::vector<TrakTool>& tools() const { return tools_; }

  // Ferramentas que estavam presentes e cruzaram o limiar de ausência na
  // última varredura (transição presente -> ausente).
  const std::vector<const TrakTool*>& newlyMissing() const { return newly_missing_; }

  // Ferramentas que estavam ausentes e voltaram a ser lidas na varredura
  // (transição ausente -> presente).
  const std::vector<const TrakTool*>& newlyFound() const { return newly_found_; }

  // Marca presença/ausência conforme os EPCs lidos pelo YRM100.
  void sweep(const std::vector<String>& readEpcs);

  // Adiciona ferramenta nova (id gerado automaticamente, sequencial).
  // Retorna false se o EPC já existir.
  bool addTool(const String& name, const String& epc);

  // Remove ferramenta pelo id. Retorna true se encontrou.
  bool removeTool(const String& id);

  // Remove ferramenta pelo EPC (usado no sync entre unidades, onde os ids
  // locais podem divergir). Retorna true se encontrou.
  bool removeToolByEpc(const String& epc);

  // Serializa o inventário no mesmo formato do inventory.json.
  String toJsonString() const;

  void clear() {
    tools_.clear();
    newly_missing_.clear();
    newly_found_.clear();
  }
  void replaceTools(std::vector<TrakTool>&& tools) { tools_ = std::move(tools); }

 private:
  std::vector<TrakTool> tools_;
  std::vector<const TrakTool*> newly_missing_;
  std::vector<const TrakTool*> newly_found_;
};
