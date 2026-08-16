#pragma once

// ===== Trakr - Histórico de eventos persistido (LittleFS) =====
// Mantém um anel de eventos (mais recente primeiro) em events.json, com
// tamanho máximo kMaxEvents. A cada adição, o app BLE é avisado via GATT.
//
// Campos de cada evento:
//   ts    -> millis() no momento em que o evento surgiu (relativo ao boot)
//   type  -> boot (radar_report e cmd_reply são efêmeros, não persistidos)
//   tool_id / name -> opcionais (vazios para eventos de sistema)

#include <Arduino.h>
#include <FS.h>
#include <vector>

class TrakEvents {
 public:
  bool load(fs::FS& fs, const char* path);
  bool save(fs::FS& fs, const char* path) const;

  size_t size() const { return events_.size(); }

  // Adiciona evento no topo do histórico (mais recente primeiro),
  // respeitando o limite; retorna o JSON do evento (para notify via GATT).
  String add(const String& type, const String& toolId, const String& toolName);

  // Serializa o histórico completo como array JSON.
  String toJsonString() const;

 private:
  struct Event {
    uint32_t ts;
    String type;
    String toolId;
    String toolName;
  };

  std::vector<Event> events_;
  static const size_t kMaxEvents = 100;
};