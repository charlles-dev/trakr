#pragma once

// ===== Trakr - Histórico de eventos persistido (LittleFS) =====
// Mantém um anel de eventos (mais recente primeiro) em events.json, com
// tamanho máximo kMaxEvents. Quando excede, arquiva o mais antigo em
// eventos por mês (/events_YYYYMM.json) para não perder histórico —
// rotação mensal paginada (Fase 1 - Robustez).
//
// Campos de cada evento:
//   ts    -> epoch ms (UTC) quando o relógio foi sincronizado via set_clock;
//            caso contrário, millis() relativo ao boot (delta = 0)
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

  // Define o delta do relógio sincronizado (epoch_ms - millis()).
  void setClockDeltaMs(int64_t delta) { clock_delta_ms_ = delta; }

  // Adiciona evento no topo do histórico (mais recente primeiro),
  // arquivando o mais antigo por mês quando excede o limite.
  // Retorna o JSON do evento (para notify via GATT).
  String add(fs::FS& fs, const char* basePath, const String& type,
             const String& toolId, const String& toolName);
  // Compatível com código legado (sem arquivamento, apenas RAM)
  String add(const String& type, const String& toolId, const String& toolName);

  // Serializa o histórico recente como array JSON.
  String toJsonString() const;

  // Lista meses arquivados (ex.: ["202508","202509"]) e contagem por mês.
  String listArchivesJson(fs::FS& fs) const;

  // Lê arquivo de um mês específico (YYYYMM) ou "boot".
  String archiveJsonForMonth(fs::FS& fs, const String& monthKey) const;

 private:
  struct Event {
    uint64_t ts;
    String type;
    String toolId;
    String toolName;
  };

  // Horário atual em ms: epoch quando sincronizado, senão millis() do boot.
  uint64_t nowMs() const {
    return clock_delta_ms_ ? (uint64_t)(millis() + clock_delta_ms_) : millis();
  }

  static String monthKeyForTs(uint64_t ts);
  static String archivePathForMonth(const String& monthKey);
  bool archiveEvent(fs::FS& fs, const Event& ev) const;

  std::vector<Event> events_;
  int64_t clock_delta_ms_ = 0;
  static const size_t kMaxEvents = 200;
};