// ===== TRK-Finder - Firmware ESP32 (Rastreador Portátil) =====
//
// Estados:
//   ESCUTA     -> espera ativa (botão ou comando BLE para agir)
//   RASTREIA   -> modo radar: procura a tag alvo, mede RSSI e guia por LED/bip
//   LEITURA    -> varre o YRM100 uma vez e publica o inventário
//   SINCRONIZA -> janela BLE para o app sincronizar (re-varre com app conectado)
//   FINDME     -> "find my finder": LED/buzzer pulsando (non-blocking)
//   DORME      -> deep sleep (wake por botão)
//
// Pinagem: include/pins.h | GATT: include/ble_profile.h
// Se compilar com -DTRAKR_SIM, gera leituras simuladas (sem o YRM100).

#include <Arduino.h>
#include <LittleFS.h>
#include <esp_ota_ops.h>
#include <esp_sleep.h>
#include <esp_task_wdt.h>
#include <algorithm>

#include <ArduinoJson.h>

#include "pins.h"
#include "ble_profile.h"
#include "TrakConfig.h"
#include "TrakInventory.h"
#include "TrakYrm100.h"
#include "TrakBle.h"
#include "TrakLed.h"
#include "TrakEvents.h"
#include "TrakBattery.h"
#include "TrakOled.h"
#include "TrakSensors.h"
#include "TrakHaptics.h"

static TrakConfig gConfig;
static TrakInventory gInventory;
static TrakYrm100 gYrm;
static TrakBle gBle;
static TrakLed gLed;
static TrakEvents gEvents;
static TrakBattery gBatt;
static TrakOled gOled;
static TrakSensors gSensors;
static TrakHaptics gHaptics;

static const char* kConfigPath = "/config.json";
static const char* kInventoryPath = "/inventory.json";
static const char* kEventsPath = "/events.json";

enum class State {
  ESCUTA,    // rastreador em espera (botão ou comando para agir)
  RASTREIA,  // modo radar single tag
  LIVE,      // varredura ao vivo: streaming contínuo EPC+RSSI de todos
  MULTI,     // radar multi-alvo com ranking RSSI
  LEITURA,   // varredura manual (botão) — publica o inventário
  SINCRONIZA,
  FINDME,    // "find my finder" (LED/buzzer, non-blocking)
  DORME,
};

static State gState = State::ESCUTA;
static unsigned long gStateSince = 0;

static const unsigned long kRadarSweepMs = 400;   // varredura por ciclo do modo radar
static const unsigned long kSweepMs = 500;        // varredura documentada em ~500 ms
static const unsigned long kSyncWindowMs = 30000; // janela BLE pós-varredura
static const unsigned long kMonitorSweepMs = 10000; // re-varredura com app conectado

static void enter(const State s) {
  gState = s;
  gStateSince = millis();
}

// ---------------- GPIO ----------------
static void initInputs() {
  pinMode(BUTTON_PIN, INPUT_PULLDOWN);
#ifdef TRAKR_HAS_BTN2
  pinMode(BUTTON2_PIN, INPUT_PULLDOWN);
#endif
}

static bool buttonPressed() {
  static bool last = false;
  static unsigned long lastAt = 0;
  const bool pressed = digitalRead(BUTTON_PIN) == BUTTON_WAKE_LEVEL;
  const unsigned long now = millis();
  if (pressed && !last && (now - lastAt > 300)) {
    lastAt = now;
    last = true;
    return true;
  }
  if (!pressed) last = false;
  return false;
}

static bool button2Pressed() {
#ifdef TRAKR_HAS_BTN2
  static bool last2 = false;
  static unsigned long lastAt2 = 0;
  const bool pressed = digitalRead(BUTTON2_PIN) == BUTTON2_WAKE_LEVEL;
  const unsigned long now = millis();
  if (pressed && !last2 && (now - lastAt2 > 300)) {
    lastAt2 = now;
    last2 = true;
    return true;
  }
  if (!pressed) last2 = false;
#endif
  return false;
}

static void initFeedback() {
  pinMode(BUZZER_PIN, OUTPUT);
  digitalWrite(BUZZER_PIN, LOW);
  gLed.begin(LED_RGB_PIN);
  gBatt.begin();
  gOled.begin();
  gSensors.begin();
  gHaptics.begin();
}

// ---------------- Histórico de eventos ----------------
// Persiste em events.json (LittleFS) e publica via GATT (Event notify).

static void pushEvent(const String& type, const String& id, const String& name) {
  String ev = gEvents.add(LittleFS, kEventsPath, type, id, name);
  gBle.notifyEvent(ev);
  gBle.setHistory(gEvents.toJsonString());
  Serial.printf("[TRAKR] Evento: %s\n", ev.c_str());
}

// ---------------- Varredura ----------------
#ifdef TRAKR_SIM
static void simulateRead(std::vector<String>& outEpcs) {
  // Simula UMA ferramenta faltando, rotacionando a cada ~10 s.
  const auto& tools = gInventory.tools();
  static uint32_t iter = 0;
  const uint32_t missingIdx = (iter++ / 20) % (tools.empty() ? 1 : tools.size());
  for (size_t i = 0; i < tools.size(); i++) {
    if (i == missingIdx) continue;
    outEpcs.push_back(tools[i].epc);
  }
}
#endif

static void sweepAndPublish() {
  std::vector<String> readEpcs;
#ifdef TRAKR_SIM
  simulateRead(readEpcs);
#else
  gYrm.collectEpc(readEpcs, kSweepMs);
#endif

  gInventory.sweep(readEpcs);
  gBle.notifyInventory(gInventory.toJsonString());

  // Registra no histórico (events.json) as transições de presença da última
  // varredura — só dispara na transição (com histerese), sem duplicar.
  for (const auto* tool : gInventory.newlyMissing()) {
    pushEvent("tool_missing", tool->id, tool->name);
  }
  for (const auto* tool : gInventory.newlyFound()) {
    pushEvent("tool_found", tool->id, tool->name);
  }
}

// ---------------- Modo radar (rastreador portátil) ----------------
// Publica {"type":"radar_report","tag":...,"rssi":...,"present":...} via
// Event notify (NÃO persiste no histórico) e ajusta LED + buzzer conforme a
// potência do sinal da tag alvo.

static String gRadarTargetEpc;  // EPC da ferramenta procurada (vazio = desligado)
static int8_t gLastRadarRssi = -100;  // último sinal medido (para o bip)

// Sessão autenticada por PIN (TRK-Finder)
static bool gAuthOk = false;
static unsigned long gAuthSince = 0;
static const unsigned long kAuthTimeoutMs = 5 * 60 * 1000;  // 5 min

// Anti-brute-force: após kAuthMaxFails falhas seguidas, bloqueia o auth por
// kAuthLockoutMs (o PIN tem poucos dígitos e o canal é BLE — dicionário trivial).
static constexpr uint8_t kAuthMaxFails = 3;
static constexpr unsigned long kAuthLockoutMs = 30000;
static uint8_t gAuthFails = 0;
static unsigned long gAuthLockUntil = 0;

static bool isAuthValid() {
  if (!gConfig.hasPin()) return true;  // sem PIN = aberto
  if (!gAuthOk) return false;
  if (millis() - gAuthSince > kAuthTimeoutMs) {
    gAuthOk = false;
    return false;
  }
  return true;
}

static std::vector<String> gMultiTargets;
static unsigned long gLiveIntervalMs = 500;
static int8_t gPrevRssiForDir = -100;
static unsigned long gFindEndAt = 0;  // fim do modo FINDME (find my finder)

static void radarSweepPublish() {
  int8_t bestRssi = -100;
  bool found = false;

  if (!gRadarTargetEpc.isEmpty()) {
#ifndef TRAKR_SIM
    std::vector<TrakRead> reads;
    gYrm.collectReads(reads, kRadarSweepMs);
    for (const auto& r : reads) {
      if (r.epc.equalsIgnoreCase(gRadarTargetEpc)) {
        found = true;
        int8_t calibrated = r.rssi + gConfig.rssiOffset();
        if (calibrated > bestRssi) bestRssi = calibrated;
      }
    }
#else
    static uint32_t iter = 0;
    found = true;
    bestRssi = -72 + (int8_t)((iter++ / 4) % 36) + gConfig.rssiOffset();
#endif
  }

  int8_t delta = found ? (bestRssi - gPrevRssiForDir) : 0;
  const char* hint = "search";
  if (found) {
    if (delta > 3) hint = "continue";
    else if (delta < -3) hint = "turn_around";
    else hint = "hold";
  }
  gPrevRssiForDir = found ? bestRssi : gPrevRssiForDir;
  // BUGFIX: gLastRadarRssi alimenta o radarBeep() no RASTREIA — sem esta
  // atribuição o bip nunca reagia ao sinal (ficava em -100 ou valor antigo).
  gLastRadarRssi = found ? bestRssi : -100;

  JsonDocument doc;
  doc["type"] = "radar_report";
  doc["tag"] = gRadarTargetEpc;
  doc["rssi"] = found ? bestRssi : -100;
  doc["present"] = found;
  doc["delta"] = delta;
  doc["hint"] = hint;
  doc["threshold"] = gConfig.rssiThreshold();
  String out;
  serializeJson(doc, out);
  gBle.notifyEvent(out);
  const char* toolName = gRadarTargetEpc.c_str();
  for (const auto& t : gInventory.tools()) {
    if (t.epc.equalsIgnoreCase(gRadarTargetEpc)) {
      toolName = t.name.c_str();
      break;
    }
  }
  gOled.showRadar(toolName, gRadarTargetEpc.c_str(), found ? bestRssi : -100, hint, gBatt.read().percent, gConfig.txPowerDbm());

  // LED por intensidade: azul (procurando) -> ciano (sinal) -> verde (perto).
  if (!found) {
    gLed.set(TrakLed::Color::SCANNING);
  } else if (bestRssi > -45) {
    gLed.set(TrakLed::Color::READY);
  } else {
    gLed.set(TrakLed::Color::SYNC);
  }
}

static void liveSweepPublish() {
  std::vector<TrakRead> reads;
#ifndef TRAKR_SIM
  gYrm.collectReads(reads, gLiveIntervalMs);
  for (auto& r : reads) r.rssi = r.rssi + gConfig.rssiOffset();
#else
  // Simula 3 tags com RSSI variado
  const auto& tools = gInventory.tools();
  for (size_t i = 0; i < tools.size() && i < 5; i++) {
    static uint32_t it = 0;
    int8_t rssi = -65 + (int8_t)((it++ + i * 7) % 30);
    reads.push_back(TrakRead{tools[i].epc, rssi});
  }
#endif

  JsonDocument doc;
  doc["type"] = "live_report";
  JsonArray arr = doc["reads"].to<JsonArray>();
  for (auto& r : reads) {
    JsonObject o = arr.add<JsonObject>();
    o["tag"] = r.epc;
    o["rssi"] = r.rssi;
  }
  String out;
  serializeJson(doc, out);
  gBle.notifyEvent(out);
}

static void multiRadarPublish() {
  std::vector<TrakRead> reads;
#ifndef TRAKR_SIM
  gYrm.collectReads(reads, kRadarSweepMs);
  for (auto& r : reads) r.rssi = r.rssi + gConfig.rssiOffset();
#else
  const auto& tools = gInventory.tools();
  for (size_t i = 0; i < tools.size() && i < 6; i++) {
    static uint32_t it = 0;
    int8_t rssi = -70 + (int8_t)((it++ + i * 11) % 40);
    reads.push_back(TrakRead{tools[i].epc, rssi});
  }
#endif

  struct RankItem { String tag; int8_t rssi; };
  std::vector<RankItem> ranking;
  for (auto& target : gMultiTargets) {
    int8_t best = -100;
    bool found = false;
    for (auto& r : reads) {
      if (r.epc.equalsIgnoreCase(target)) { found = true; if (r.rssi > best) best = r.rssi; }
    }
    if (found) ranking.push_back(RankItem{target, best});
  }
  // Ordena por RSSI decrescente (mais forte primeiro)
  std::sort(ranking.begin(), ranking.end(), [](const RankItem& a, const RankItem& b){ return a.rssi > b.rssi; });

  JsonDocument doc;
  doc["type"] = "radar_report_multi";
  JsonArray arr = doc["ranking"].to<JsonArray>();
  for (auto& it : ranking) {
    JsonObject o = arr.add<JsonObject>();
    o["tag"] = it.tag;
    o["rssi"] = it.rssi;
  }
  String out;
  serializeJson(doc, out);
  gBle.notifyEvent(out);

  if (!ranking.empty()) {
    gLastRadarRssi = ranking[0].rssi;
    if (ranking[0].rssi > -45) gLed.set(TrakLed::Color::READY);
    else gLed.set(TrakLed::Color::SYNC);
  } else {
    gLed.set(TrakLed::Color::SCANNING);
  }
}

// Bip "detector de metais": ativo (intervalo) ou passivo (frequência variável)
static void radarBeep(int8_t rssi) {
  if (!gConfig.beep()) {
#ifdef TRAKR_HAS_PASSIVE_BUZZER
    gHaptics.noTone();
#else
    digitalWrite(BUZZER_PIN, LOW);
#endif
    return;
  }
#ifdef TRAKR_HAS_PASSIVE_BUZZER
  if (rssi < -80) { gHaptics.noTone(); return; }
  // Mapeia RSSI -80..-30 => freq 200..2000 Hz, com ducking rítmico
  uint16_t freq = map(constrain(rssi, -80, -30), -80, -30, 200, 2000);
  long interval = map(constrain(rssi, -80, -30), -80, -30, 600, 80);
  bool on = (millis() % (interval * 2)) < interval;
  if (on) gHaptics.tone(freq);
  else gHaptics.noTone();
#ifdef TRAKR_HAS_VIBRATOR
  // Vibra junto quando muito perto
  if (rssi > -45) gHaptics.vibrate(30);
#endif
#else
  long interval = rssi >= -80 ? map(constrain(rssi, -80, -30), -80, -30, 1000, 100)
                              : 1000;
  const bool on = (millis() % (interval * 2)) < interval;
  digitalWrite(BUZZER_PIN, on ? HIGH : LOW);
#ifdef TRAKR_HAS_VIBRATOR
  if (rssi > -45 && on) { digitalWrite(VIB_PIN, HIGH); } else { digitalWrite(VIB_PIN, LOW); }
#endif
#endif
}

// ---------------- Comandos do app (JSON via GATT Control) ----------------

// Resposta de comando (ACK) via Event notify: permite ao app saber se o
// firmware aceitou/rejeitou a ação. Não é persistida no histórico.
static void replyControl(const char* cmd, const char* status, const char* reason = nullptr) {
  JsonDocument doc;
  doc["type"] = "cmd_reply";
  doc["cmd"] = cmd;
  doc["status"] = status;
  if (reason) doc["reason"] = reason;
  String out;
  serializeJson(doc, out);
  gBle.notifyEvent(out);
}

// Definido abaixo (seção OTA) — declarado aqui para uso no handleControlCommand.
static void handleOtaCommand(const JsonDocument& doc, const char* cmd);

static void handleControlCommand(const String& json) {
  Serial.printf("[TRAKR] Comando recebido: %s\n", json.c_str());

  JsonDocument doc;
  const DeserializationError err = deserializeJson(doc, json);
  if (err) {
    Serial.printf("[TRAKR] JSON inválido: %s\n", err.c_str());
    replyControl("?", "error", "invalid_json");
    return;
  }

  const char* cmd = doc["cmd"] | "";

  // Versão do firmware: o app usa para gating de features (compatibilidade).
  if (strcmp(cmd, "get_version") == 0) {
    JsonDocument out;
    out["type"] = "cmd_reply";
    out["cmd"] = "get_version";
    out["status"] = "ok";
    out["fw_version"] = TRAKR_FW_VERSION;
    out["git_commit"] = TRAKR_GIT_COMMIT;
    String jsonOut;
    serializeJson(out, jsonOut);
    gBle.notifyEvent(jsonOut);
    return;
  }

  // Configurações do dispositivo: o app lê e altera via config.json.
  // get_config responde com os valores atuais (inclui has_pin/authed + RF calibration);
  // set_config aceita campos parciais (listen_ms/radar_ms/beep/pin/tx_power/rssi).
  if (strcmp(cmd, "get_config") == 0) {
    JsonDocument out;
    out["type"] = "cmd_reply";
    out["cmd"] = "get_config";
    out["status"] = "ok";
    out["listen_ms"] = gConfig.listenMs();
    out["radar_ms"] = gConfig.radarMs();
    out["beep"] = gConfig.beep();
    out["tx_power_dbm"] = gConfig.txPowerDbm();
    out["rssi_offset"] = gConfig.rssiOffset();
    out["rssi_threshold"] = gConfig.rssiThreshold();
    out["env_profile"] = gConfig.envProfile();
    out["has_pin"] = gConfig.hasPin();
    out["authed"] = isAuthValid();
    out["fw_version"] = TRAKR_FW_VERSION;
    out["git_commit"] = TRAKR_GIT_COMMIT;
    if (gAuthOk && gConfig.hasPin()) {
      unsigned long elapsed = millis() - gAuthSince;
      unsigned long remaining = (elapsed < kAuthTimeoutMs) ? (kAuthTimeoutMs - elapsed) : 0;
      out["auth_expires_ms"] = remaining;
    }
    String jsonOut;
    serializeJson(out, jsonOut);
    gBle.notifyEvent(jsonOut);
    return;
  }

  if (strcmp(cmd, "set_config") == 0) {
    const bool hasListen = !doc["listen_ms"].isNull();
    const bool hasRadar = !doc["radar_ms"].isNull();
    const bool hasBeep = !doc["beep"].isNull();
    const bool hasPin = !doc["pin"].isNull();
    const bool hasTx = !doc["tx_power_dbm"].isNull();
    const bool hasRssiOff = !doc["rssi_offset"].isNull();
    const bool hasRssiTh = !doc["rssi_threshold"].isNull();
    const bool hasEnv = !doc["env_profile"].isNull();
    if (!hasListen && !hasRadar && !hasBeep && !hasPin && !hasTx && !hasRssiOff && !hasRssiTh && !hasEnv) {
      replyControl("set_config", "error", "missing_fields");
      return;
    }

    // PIN: requer auth se já houver PIN configurado
    if (hasPin) {
      if (gConfig.hasPin() && !isAuthValid()) {
        replyControl("set_config", "error", "auth_required");
        return;
      }
    }

    const unsigned long oldListen = gConfig.listenMs();
    const unsigned long oldRadar = gConfig.radarMs();
    const bool oldBeep = gConfig.beep();
    const String oldPinHash = gConfig.pinHash();
    const uint8_t oldTx = gConfig.txPowerDbm();
    const int8_t oldRssiOff = gConfig.rssiOffset();
    const int8_t oldRssiTh = gConfig.rssiThreshold();
    const String oldEnv = gConfig.envProfile();

    if (hasListen) {
      const unsigned long v = doc["listen_ms"] | 0ul;
      if (v < 5000 || v > 300000) {
        replyControl("set_config", "error", "invalid_value");
        return;
      }
      gConfig.setListenMs(v);
    }
    if (hasRadar) {
      const unsigned long v = doc["radar_ms"] | 0ul;
      if (v < 10000 || v > 600000) {
        replyControl("set_config", "error", "invalid_value");
        return;
      }
      gConfig.setRadarMs(v);
    }
    if (hasBeep) gConfig.setBeep(doc["beep"] | true);

    if (hasTx) {
      const int v = doc["tx_power_dbm"] | 26;
      if (v < 0 || v > 33) {
        replyControl("set_config", "error", "invalid_value");
        return;
      }
      gConfig.setTxPowerDbm((uint8_t)v);
      gYrm.setTxPower((uint8_t)v);
    }
    if (hasRssiOff) {
      const int v = doc["rssi_offset"] | 0;
      if (v < -30 || v > 30) {
        replyControl("set_config", "error", "invalid_value");
        return;
      }
      gConfig.setRssiOffset((int8_t)v);
    }
    if (hasRssiTh) {
      const int v = doc["rssi_threshold"] | -70;
      if (v < -100 || v > -20) {
        replyControl("set_config", "error", "invalid_value");
        return;
      }
      gConfig.setRssiThreshold((int8_t)v);
    }
    if (hasEnv) {
      String env = doc["env_profile"] | "default";
      if (env.length() > 32) {
        replyControl("set_config", "error", "invalid_value");
        return;
      }
      gConfig.setEnvProfile(env);
    }

    if (hasPin) {
      const char* pinRaw = doc["pin"] | "";
      String pinStr = String(pinRaw);
      if (pinStr.length() == 0) {
        gConfig.clearPin();
        gAuthOk = false;
      } else {
        if (pinStr.length() < 4 || pinStr.length() > 32) {
          gConfig.setListenMs(oldListen);
          gConfig.setRadarMs(oldRadar);
          gConfig.setBeep(oldBeep);
          gConfig.setTxPowerDbm(oldTx);
          gConfig.setRssiOffset(oldRssiOff);
          gConfig.setRssiThreshold(oldRssiTh);
          gConfig.setEnvProfile(oldEnv);
          replyControl("set_config", "error", "invalid_value");
          return;
        }
        gConfig.setPinHash(TrakConfig::hashPin(pinStr));
        gAuthOk = true;
        gAuthSince = millis();
      }
    }

    if (!gConfig.save(LittleFS, kConfigPath)) {
      gConfig.setListenMs(oldListen);
      gConfig.setRadarMs(oldRadar);
      gConfig.setBeep(oldBeep);
      gConfig.setTxPowerDbm(oldTx);
      gConfig.setRssiOffset(oldRssiOff);
      gConfig.setRssiThreshold(oldRssiTh);
      gConfig.setEnvProfile(oldEnv);
      if (hasPin) {
        if (oldPinHash.length() == 64) gConfig.setPinHash(oldPinHash);
        else gConfig.clearPin();
      }
      Serial.printf("[TRAKR] ERRO: falha ao salvar %s\n", kConfigPath);
      replyControl("set_config", "error", "save_failed");
      return;
    }
    Serial.printf("[TRAKR] Config atualizada: has_pin=%s authed=%s tx=%u rssi_off=%d\n",
                  gConfig.hasPin() ? "yes" : "no",
                  isAuthValid() ? "yes" : "no",
                  gConfig.txPowerDbm(), gConfig.rssiOffset());
    JsonDocument out;
    out["type"] = "cmd_reply";
    out["cmd"] = "set_config";
    out["status"] = "ok";
    out["listen_ms"] = gConfig.listenMs();
    out["radar_ms"] = gConfig.radarMs();
    out["beep"] = gConfig.beep();
    out["tx_power_dbm"] = gConfig.txPowerDbm();
    out["rssi_offset"] = gConfig.rssiOffset();
    out["rssi_threshold"] = gConfig.rssiThreshold();
    out["env_profile"] = gConfig.envProfile();
    out["has_pin"] = gConfig.hasPin();
    out["authed"] = isAuthValid();
    if (gAuthOk && gConfig.hasPin()) {
      unsigned long elapsed = millis() - gAuthSince;
      out["auth_expires_ms"] = (elapsed < kAuthTimeoutMs) ? (kAuthTimeoutMs - elapsed) : 0;
    }
    String jsonOut;
    serializeJson(out, jsonOut);
    gBle.notifyEvent(jsonOut);
    return;
  }

  // Autenticação por PIN: {"cmd":"auth","pin":"1234"}
  if (strcmp(cmd, "auth") == 0) {
    if (!gConfig.hasPin()) {
      replyControl("auth", "ok");
      return;
    }
    if (millis() < gAuthLockUntil) {
      JsonDocument out;
      out["type"] = "cmd_reply";
      out["cmd"] = "auth";
      out["status"] = "error";
      out["reason"] = "locked";
      out["retry_after_ms"] = gAuthLockUntil - millis();
      String jsonOut;
      serializeJson(out, jsonOut);
      gBle.notifyEvent(jsonOut);
      return;
    }
    const char* pinRaw = doc["pin"] | "";
    if (pinRaw[0] == '\0') {
      replyControl("auth", "error", "missing_fields");
      return;
    }
    String pinStr = String(pinRaw);
    if (gConfig.verifyPin(pinStr)) {
      gAuthOk = true;
      gAuthSince = millis();
      gAuthFails = 0;  // reset do contador de tentativas
      Serial.println("[TRAKR] PIN ok — sessão autenticada");
      replyControl("auth", "ok");
    } else {
      gAuthFails++;
      if (gAuthFails >= kAuthMaxFails) {
        gAuthLockUntil = millis() + kAuthLockoutMs;
        gAuthFails = 0;
        Serial.printf("[TRAKR] PIN falhou — bloqueio de %lu ms\n",
                      (unsigned long)kAuthLockoutMs);
        replyControl("auth", "error", "locked");
      } else {
        Serial.printf("[TRAKR] PIN falhou (%u/%u)\n", gAuthFails, kAuthMaxFails);
        replyControl("auth", "error", "auth_failed");
      }
    }
    return;
  }

  if (strcmp(cmd, "set_clock") == 0) {
    const uint64_t epochMs = doc["epoch_ms"] | (uint64_t)0;
    if (epochMs < 1704067200000ull || epochMs > 2070000000000ull) {
      replyControl("set_clock", "error", "invalid_epoch");
      return;
    }
    gConfig.setClockDeltaMs((int64_t)epochMs - (int64_t)millis());
    if (!gConfig.save(LittleFS, kConfigPath)) {
      replyControl("set_clock", "error", "save_failed");
      return;
    }
    gEvents.setClockDeltaMs(gConfig.clockDeltaMs());
    replyControl("set_clock", "ok");
    return;
  }

  if (strcmp(cmd, "rescan") == 0) {
    enter(State::LEITURA);
    replyControl("rescan", "ok");
    return;
  }

  if (strcmp(cmd, "get_history") == 0) {
    const char* month = doc["month"] | "";
    // Paginação: resposta pode ser enorme (200 eventos) — o app percorre
    // com offset/limit. Default: 50 eventos iniciais.
    int limit = doc["limit"] | 50;
    int offset = doc["offset"] | 0;
    if (limit < 1) limit = 1;
    if (limit > 200) limit = 200;
    if (offset < 0) offset = 0;

    JsonDocument histDoc;
    if (month[0] != '\0') {
      String m = String(month);
      String hist = gEvents.archiveJsonForMonth(LittleFS, m);
      // Re-parse para inserir no reply
      deserializeJson(histDoc, hist);
    } else {
      deserializeJson(histDoc, gEvents.toJsonString());
    }
    const JsonArray all = histDoc.as<JsonArray>();
    const int total = all.size();

    JsonDocument out;
    out["type"] = "cmd_reply";
    out["cmd"] = "get_history";
    out["status"] = "ok";
    if (month[0] != '\0') out["month"] = month;
    JsonArray slice = out["history"].to<JsonArray>();
    int end = offset + limit;
    if (end > total) end = total;
    for (int i = offset; i < end; i++) {
      slice.add(all[i]);
    }
    out["total"] = total;
    out["has_more"] = end < total;
    String jsonOut;
    serializeJson(out, jsonOut);
    gBle.notifyEvent(jsonOut);
    gBle.notifyEvent(jsonOut);
    return;
  }

  if (strcmp(cmd, "list_archives") == 0 || strcmp(cmd, "list_history") == 0) {
    JsonDocument out;
    out["type"] = "cmd_reply";
    out["cmd"] = cmd;
    out["status"] = "ok";
    JsonDocument listDoc;
    deserializeJson(listDoc, gEvents.listArchivesJson(LittleFS));
    out["archives"] = listDoc.as<JsonArray>();
    String jsonOut;
    serializeJson(out, jsonOut);
    gBle.notifyEvent(jsonOut);
    return;
  }

  if (strcmp(cmd, "set_tx_power") == 0) {
    int dbm = doc["dbm"] | doc["tx_power_dbm"] | 26;
    if (dbm < 0 || dbm > 33) { replyControl("set_tx_power", "error", "invalid_value"); return; }
    gConfig.setTxPowerDbm((uint8_t)dbm);
    gYrm.setTxPower((uint8_t)dbm);
    gConfig.save(LittleFS, kConfigPath);
    replyControl("set_tx_power", "ok");
    return;
  }

  if (strcmp(cmd, "capture_tag") == 0 || strcmp(cmd, "scan_one") == 0) {
    std::vector<TrakRead> reads;
#ifndef TRAKR_SIM
    gYrm.collectReads(reads, 1200);
#else
    reads.push_back(TrakRead{"E28011600123456789ABCDEF", -42});
#endif
    if (!reads.empty()) {
      std::sort(reads.begin(), reads.end(), [](const TrakRead& a, const TrakRead& b) {
        return a.rssi > b.rssi;
      });
      JsonDocument out;
      out["type"] = "cmd_reply";
      out["cmd"] = "capture_tag";
      out["status"] = "ok";
      out["tag"] = reads[0].epc;
      out["rssi"] = reads[0].rssi + gConfig.rssiOffset();
      String jsonOut;
      serializeJson(out, jsonOut);
      gBle.notifyEvent(jsonOut);
#ifdef TRAKR_HAS_PASSIVE_BUZZER
      gHaptics.tone(1400);
      delay(60);
      gHaptics.noTone();
#endif
      Serial.printf("[TRAKR] Tag capturada para cadastro: %s (%d dBm)\n", reads[0].epc.c_str(), reads[0].rssi);
    } else {
      replyControl("capture_tag", "error", "no_tag_found");
    }
    return;
  }

  if (strcmp(cmd, "sync_inventory") == 0) {
    JsonArray toolsArr = doc["tools"].as<JsonArray>();
    if (!toolsArr.isNull()) {
      gInventory.clear();
      for (JsonObject t : toolsArr) {
        const char* name = t["name"] | "";
        const char* epc = t["epc"] | "";
        if (strlen(epc) > 0) {
          gInventory.addTool(name, epc);
        }
      }
      gInventory.save(LittleFS, kInventoryPath);
      gOled.showSync(gInventory.tools().size());
      replyControl("sync_inventory", "ok");
      gBle.notifyInventory(gInventory.toJsonString());
      return;
    }
  }

  if (strcmp(cmd, "find_device") == 0 || strcmp(cmd, "locate_finder") == 0) {
    int durationSec = doc["duration_sec"] | 5;
    if (durationSec < 1) durationSec = 1;
    if (durationSec > 60) durationSec = 60;
    Serial.printf("[TRAKR] Find My Finder ativado por %d s\n", durationSec);
    // Non-blocking: o estado FINDME pulsa LED/buzzer no loop(); o comando
    // volta imediatamente (não trava o processamento de outros comandos).
    gFindEndAt = millis() + (durationSec * 1000UL);
    replyControl(cmd, "ok");
    enter(State::FINDME);
    return;
  }

  if (strcmp(cmd, "set_rf_power") == 0) {
    int dbm = doc["power_dbm"] | doc["dbm"] | 18;
    if (dbm < 0 || dbm > 26) { replyControl("set_rf_power", "error", "invalid_range"); return; }
    gConfig.setTxPowerDbm((uint8_t)dbm);
    gYrm.setTxPower((uint8_t)dbm);
    gConfig.save(LittleFS, kConfigPath);
    replyControl("set_rf_power", "ok");
    Serial.printf("[TRAKR] RF Power YRM100 ajustado: %d dBm\n", dbm);
    return;
  }

  if (strcmp(cmd, "write_epc") == 0) {
    if (!isAuthValid()) {
      replyControl("write_epc", "error", "auth_required");
      return;
    }
    const char* newEpc = doc["new_epc"] | doc["epc"] | "";
    const size_t epcLen = strlen(newEpc);
    // EPC UHF padrão: 24 chars hex (12 bytes).
    if (epcLen != 24) {
      replyControl("write_epc", "error", "invalid_epc");
      return;
    }
    uint8_t epcBytes[12];
    for (size_t i = 0; i < 12; i++) {
      const char c0 = newEpc[2 * i];
      const char c1 = newEpc[2 * i + 1];
      const auto hexVal = [](char c) -> int {
        if (c >= '0' && c <= '9') return c - '0';
        if (c >= 'a' && c <= 'f') return c - 'a' + 10;
        if (c >= 'A' && c <= 'F') return c - 'A' + 10;
        return -1;
      };
      const int h0 = hexVal(c0);
      const int h1 = hexVal(c1);
      if (h0 < 0 || h1 < 0) {
        replyControl("write_epc", "error", "invalid_epc");
        return;
      }
      epcBytes[i] = (uint8_t)((h0 << 4) | h1);
    }
    if (gYrm.writeEpc(epcBytes, 12)) {
      Serial.printf("[TRAKR] EPC gravado no módulo: %s\n", newEpc);
      replyControl("write_epc", "ok");
    } else {
      // Sem ACK do YRM100: não afirmar sucesso (comportamento honesto).
      replyControl("write_epc", "error", "write_failed");
    }
    return;
  }

  if (strcmp(cmd, "get_blackbox_logs") == 0) {
    JsonDocument out;
    out["type"] = "cmd_reply";
    out["cmd"] = "get_blackbox_logs";
    out["status"] = "ok";
    JsonDocument histDoc;
    deserializeJson(histDoc, gEvents.toJsonString());
    out["logs"] = histDoc.as<JsonArray>();
    String jsonOut;
    serializeJson(out, jsonOut);
    gBle.notifyEvent(jsonOut);
    return;
  }

  if (strcmp(cmd, "start_live") == 0) {
    gLiveIntervalMs = doc["interval_ms"] | 500;
    enter(State::LIVE);
    replyControl("start_live", "ok");
    return;
  }

  if (strcmp(cmd, "stop_live") == 0) {
    enter(State::SINCRONIZA);
    replyControl("stop_live", "ok");
    return;
  }

  if (strcmp(cmd, "start_radar_multi") == 0 || strcmp(cmd, "start_multi") == 0) {
    gMultiTargets.clear();
    JsonArray arr = doc["tags"].as<JsonArray>();
    if (arr.isNull()) arr = doc["ids"].as<JsonArray>();
    if (!arr.isNull()) {
      for (JsonVariant v : arr) {
        String s = v.as<String>();
        if (s.length() > 0) gMultiTargets.push_back(s);
      }
    }
    // Fallback: se veio apenas um id/tag, usa como multi de 1
    if (gMultiTargets.empty()) {
      const char* id = doc["id"] | "";
      const char* tag = doc["tag"] | "";
      if (tag[0] != '\0') gMultiTargets.push_back(String(tag));
      else if (id[0] != '\0') {
        for (auto& t : gInventory.tools()) if (t.id == id) { gMultiTargets.push_back(t.epc); break; }
      }
    }
    if (gMultiTargets.empty()) { replyControl(cmd, "error", "missing_fields"); return; }
    enter(State::MULTI);
    replyControl(cmd, "ok");
    return;
  }

  if (strcmp(cmd, "get_sensors") == 0) {
    JsonDocument out;
    out["type"] = "cmd_reply";
    out["cmd"] = "get_sensors";
    out["status"] = "ok";
    out["has_oled"] =
#ifdef TRAKR_HAS_OLED
      true
#else
      false
#endif
    ;
    out["has_ina219"] =
#ifdef TRAKR_HAS_INA219
      true
#else
      false
#endif
    ;
    out["has_bme280"] =
#ifdef TRAKR_HAS_BME280
      true
#else
      false
#endif
    ;
    out["has_mpu"] =
#ifdef TRAKR_HAS_MPU6050
      true
#else
      false
#endif
    ;
    out["has_vib"] =
#ifdef TRAKR_HAS_VIBRATOR
      true
#else
      false
#endif
    ;
    out["has_btn2"] =
#ifdef TRAKR_HAS_BTN2
      true
#else
      false
#endif
    ;
    out["tx_power_dbm"] = gConfig.txPowerDbm();
    out["rssi_offset"] = gConfig.rssiOffset();
    out["rssi_threshold"] = gConfig.rssiThreshold();
    out["env"] = gConfig.envProfile();
    {
      BatteryInfo bi = gBatt.read();
      out["batt_v"] = bi.voltage;
      out["batt_pct"] = bi.percent;
      out["batt_valid"] = bi.valid;
    }
#ifdef TRAKR_HAS_BME280
    {
      EnvData env = gSensors.readBME();
      if (env.valid) {
        out["temp_c"] = env.temp;
        out["hum_pct"] = env.hum;
        out["press_hpa"] = env.press;
      }
    }
#endif
#ifdef TRAKR_HAS_MPU6050
    {
      ImuData imu = gSensors.readIMU();
      if (imu.valid) {
        out["moving"] = imu.moving;
        out["ax"] = imu.ax;
        out["ay"] = imu.ay;
        out["az"] = imu.az;
      }
    }
#endif
    String jsonOut;
    serializeJson(out, jsonOut);
    gBle.notifyEvent(jsonOut);
    return;
  }

  if (strcmp(cmd, "get_addons") == 0) {
    JsonDocument out;
    out["type"] = "cmd_reply";
    out["cmd"] = "get_addons";
    out["status"] = "ok";
    JsonArray arr = out["addons"].to<JsonArray>();
#ifdef TRAKR_HAS_OLED
    arr.add("oled");
#endif
#ifdef TRAKR_HAS_BTN2
    arr.add("btn2");
#endif
#ifdef TRAKR_HAS_INA219
    arr.add("ina219");
#endif
#ifdef TRAKR_HAS_BME280
    arr.add("bme280");
#endif
#ifdef TRAKR_HAS_MPU6050
    arr.add("mpu6050");
#endif
#ifdef TRAKR_HAS_VIBRATOR
    arr.add("vib");
#endif
    String jsonOut;
    serializeJson(out, jsonOut);
    gBle.notifyEvent(jsonOut);
    return;
  }

  // Modo radar: procura a tag de uma ferramenta pelo EPC (preferencial) ou
  // pelo id (o app envia os dois — no sync entre unidades os ids divergem).
  if (strcmp(cmd, "start_radar") == 0) {
    const char* id = doc["id"] | "";
    const char* tag = doc["tag"] | "";
    const TrakTool* target = nullptr;
    if (tag[0] != '\0') {
      for (const auto& t : gInventory.tools()) {
        if (t.epc.equalsIgnoreCase(tag)) {
          target = &t;
          break;
        }
      }
    }
    if (target == nullptr && id[0] != '\0') {
      for (const auto& t : gInventory.tools()) {
        if (t.id == id) {
          target = &t;
          break;
        }
      }
    }
    if (target != nullptr) {
      gRadarTargetEpc = target->epc;
    } else if (tag[0] != '\0') {
      gRadarTargetEpc = String(tag);
    } else if (id[0] != '\0') {
      gRadarTargetEpc = String(id);
    }

    if (gRadarTargetEpc.length() > 0) {
      digitalWrite(BUZZER_PIN, LOW);
      Serial.printf("[TRAKR] Radar iniciado para tag: %s\n", gRadarTargetEpc.c_str());
      enter(State::RASTREIA);
      replyControl("start_radar", "ok");
      return;
    }
    Serial.printf("[TRAKR] start_radar: id %s / tag %s não encontrados\n", id, tag);
    replyControl("start_radar", "error", "tool_not_found");
    return;
  }

  if (strcmp(cmd, "stop_radar") == 0) {
    gRadarTargetEpc = "";
    digitalWrite(BUZZER_PIN, LOW);
    Serial.println("[TRAKR] Radar parado");
    enter(State::SINCRONIZA);
    replyControl("stop_radar", "ok");
    return;
  }

  if (strcmp(cmd, "ota_begin") == 0 || strcmp(cmd, "ota_end") == 0 ||
      strcmp(cmd, "ota_abort") == 0) {
    handleOtaCommand(doc, cmd);
    return;
  }

  bool changed = false;

  if (strcmp(cmd, "add_tool") == 0) {
    if (!isAuthValid()) {
      replyControl("add_tool", "error", "auth_required");
      return;
    }
    const char* name = doc["name"] | "";
    const char* tag = doc["tag"] | "";
    if (strlen(name) == 0 || strlen(tag) == 0) {
      Serial.println("[TRAKR] add_tool: nome/tag obrigatórios");
      replyControl("add_tool", "error", "missing_fields");
      return;
    }
    changed = gInventory.addTool(name, tag);
    if (!changed) {
      Serial.println("[TRAKR] add_tool: EPC já cadastrado");
      replyControl("add_tool", "error", "duplicate_epc");
      return;
    }
  } else if (strcmp(cmd, "remove_tool") == 0) {
    if (!isAuthValid()) {
      replyControl("remove_tool", "error", "auth_required");
      return;
    }
    const char* id = doc["id"] | "";
    const char* epc = doc["epc"] | "";
    // Por EPC primeiro (os ids locais podem divergir entre app e firmware).
    if (strlen(epc) > 0) {
      changed = gInventory.removeToolByEpc(epc);
      if (!changed) {
        Serial.printf("[TRAKR] remove_tool: epc %s não encontrado\n", epc);
        replyControl("remove_tool", "error", "tool_not_found");
        return;
      }
    } else {
      changed = gInventory.removeTool(id);
      if (!changed) {
        Serial.printf("[TRAKR] remove_tool: id %s não encontrado\n", id);
        replyControl("remove_tool", "error", "tool_not_found");
        return;
      }
    }
  } else {
    Serial.printf("[TRAKR] Comando desconhecido: %s\n", cmd);
    replyControl(cmd, "error", "unknown_cmd");
    return;
  }

  // Persistência no LittleFS (fonte da verdade) + publicação via BLE.
  if (!gInventory.save(LittleFS, kInventoryPath)) {
    Serial.printf("[TRAKR] ERRO: falha ao salvar %s\n", kInventoryPath);
    replyControl(cmd, "error", "save_failed");
    return;
  }
  gBle.notifyInventory(gInventory.toJsonString());
  replyControl(cmd, "ok");
}

// ---------------- OTA via BLE ----------------
// Fluxo (controlado pelo app):
//   {"cmd":"ota_begin","size":N} -> sessão aberta na partição OTA
//   gravações na característica OTA (chunks) -> esp_ota_write
//   {"cmd":"ota_end"}            -> valida, define boot e reinicia
//   {"cmd":"ota_abort"}          -> descarta a sessão

static esp_ota_handle_t gOtaHandle = 0;
static uint32_t gOtaSize = 0;
static uint32_t gOtaReceived = 0;

static void abortOta() {
  if (gOtaHandle != 0) {
    esp_ota_abort(gOtaHandle);
    gOtaHandle = 0;
  }
  gOtaSize = 0;
  gOtaReceived = 0;
}

static void otaWriteChunk(const uint8_t* data, size_t len) {
  if (gOtaHandle == 0) return;  // fora de sessão: ignora
  if (gOtaReceived + len > gOtaSize) {
    Serial.println("[TRAKR] OTA: tamanho excede o anunciado — abortando");
    abortOta();
    replyControl("ota", "error", "size_exceeded");
    return;
  }
  const esp_err_t err = esp_ota_write(gOtaHandle, data, len);
  if (err != ESP_OK) {
    Serial.printf("[TRAKR] OTA: esp_ota_write falhou (0x%x) — abortando\n", err);
    abortOta();
    replyControl("ota", "error", "write_failed");
    return;
  }
  gOtaReceived += len;
}

static void handleOtaCommand(const JsonDocument& doc, const char* cmd) {
  if (strcmp(cmd, "ota_begin") == 0) {
    // Segurança: flash via BLE exige sessão autenticada por PIN (se houver).
    if (!isAuthValid()) {
      replyControl("ota_begin", "error", "auth_required");
      return;
    }
    const uint32_t size = doc["size"] | 0u;
    if (size == 0 || gOtaHandle != 0) {
      replyControl("ota_begin", "error", "invalid_state");
      return;
    }
    const esp_partition_t* partition = esp_ota_get_next_update_partition(nullptr);
    const esp_err_t err = esp_ota_begin(partition, size, &gOtaHandle);
    if (err != ESP_OK) {
      gOtaHandle = 0;
      Serial.printf("[TRAKR] OTA: esp_ota_begin falhou (0x%x)\n", err);
      replyControl("ota_begin", "error", "begin_failed");
      return;
    }
    gOtaSize = size;
    gOtaReceived = 0;
    Serial.printf("[TRAKR] OTA iniciada: %u bytes -> %s\n", size, partition->label);
    replyControl("ota_begin", "ok");
    return;
  }

  if (strcmp(cmd, "ota_end") == 0) {
    if (gOtaHandle == 0) {
      replyControl("ota_end", "error", "no_session");
      return;
    }
    // Auth pode ter expirado durante a transferência — aborta se preciso.
    if (!isAuthValid()) {
      abortOta();
      replyControl("ota_end", "error", "auth_required");
      return;
    }
    if (gOtaReceived != gOtaSize) {
      Serial.printf("[TRAKR] OTA: recebidos %u de %u — abortando\n", gOtaReceived, gOtaSize);
      abortOta();
      replyControl("ota_end", "error", "size_mismatch");
      return;
    }
    const esp_partition_t* partition = esp_ota_get_next_update_partition(nullptr);
    esp_err_t err = esp_ota_end(gOtaHandle);
    gOtaHandle = 0;
    if (err != ESP_OK) {
      Serial.printf("[TRAKR] OTA: esp_ota_end falhou (0x%x)\n", err);
      replyControl("ota_end", "error", "end_failed");
      return;
    }
    err = esp_ota_set_boot_partition(partition);
    if (err != ESP_OK) {
      Serial.printf("[TRAKR] OTA: esp_ota_set_boot_partition falhou (0x%x)\n", err);
      replyControl("ota_end", "error", "set_boot_failed");
      return;
    }
    Serial.println("[TRAKR] OTA concluída — reiniciando");
    replyControl("ota_end", "ok");
    delay(200);
    esp_restart();
    return;
  }

  if (strcmp(cmd, "ota_abort") == 0) {
    abortOta();
    replyControl("ota_abort", "ok");
  }
}

// ---------------- Deep sleep ----------------
static void goToSleep() {
  Serial.println("[TRAKR] Deep sleep (aguardando botão)...");
  Serial.flush();

  gYrm.disablePower();
  Serial2.end();  // desliga a UART do YRM100 (economia no dormir)

  // LED e buzzer no nível LOW: segura os pinos no estado atual durante o
  // deep sleep (evita flutuação/consumo parasitário na saída dos drivers).
  digitalWrite(BUZZER_PIN, LOW);
  gpio_hold_en((gpio_num_t)BUZZER_PIN);
  gpio_hold_en((gpio_num_t)LED_RGB_PIN);

  // Desliga os domínios RTC de RAM: sem wake stub, não há nada para reter
  // e desligar os dois domínios reduz o consumo em deep sleep.
  esp_sleep_pd_config(ESP_PD_DOMAIN_RTC_SLOW_MEM, ESP_PD_OPTION_OFF);
  esp_sleep_pd_config(ESP_PD_DOMAIN_RTC_FAST_MEM, ESP_PD_OPTION_OFF);

  esp_sleep_enable_ext0_wakeup((gpio_num_t)BUTTON_PIN, BUTTON_WAKE_LEVEL);
  esp_deep_sleep_start();
}

// ---------------- Healthcheck OTA ----------------
// Valida o firmware atual ~10 s após o boot sem crash: se o novo firmware
// da OTA estiver PENDING_VERIFY e o dispositivo reiniciar antes disso, o
// bootloader faz rollback automático para a partição anterior (evita brick
// por FW ruim que trava no boot).

static bool gOtaValidMarked = false;

static void otaHealthCheck() {
  if (gOtaValidMarked) return;
  const esp_partition_t* cur = esp_ota_get_running_partition();
  if (cur == nullptr) return;
  esp_ota_img_states_t state;
  if (esp_ota_get_state_partition(cur, &state) != ESP_OK) return;
  Serial.printf("[TRAKR] OTA partition %s state: %d\n", cur->label, (int)state);
  if (state == ESP_OTA_IMG_PENDING_VERIFY) {
    // Chegou aqui sem crash: firmware novo é válido — confirma o boot.
    esp_ota_mark_app_valid_cancel_rollback();
    Serial.println("[TRAKR] OTA: firmware novo validado (rollback cancelado)");
  }
  gOtaValidMarked = true;
}

// ---------------- Setup ----------------
void setup() {
  Serial.begin(115200);
  delay(200);

  // Watchdog: 30 s sem o loop responder = reset limpo (protege contra
  // estado preso ou módulo UHF travado).
  esp_task_wdt_init(30, true);
  esp_task_wdt_add(nullptr);

  const uint32_t wakeCause = esp_sleep_get_wakeup_cause();
  Serial.printf("[TRAKR] Boot | wake cause: %" PRIu32 "\n", wakeCause);

  if (!LittleFS.begin()) {
    Serial.println("[TRAKR] ERRO: falha ao montar LittleFS");
  }

  gConfig.load(LittleFS, kConfigPath);

  if (!gInventory.load(LittleFS, kInventoryPath)) {
    Serial.println("[TRAKR] Inventário vazio — gravar inventory.json em data/");
  }

  initInputs();

  // Libera o gpio_hold do deep sleep antes de reconfigurar LED/buzzer
  // (a trava persiste no wake até gpio_hold_dis).
  gpio_hold_dis((gpio_num_t)BUZZER_PIN);
  gpio_hold_dis((gpio_num_t)LED_RGB_PIN);

  initFeedback();
  gYrm.begin(Serial2, YRM100_BAUD, YRM100_RX_PIN, YRM100_TX_PIN);
#ifndef TRAKR_SIM
  // Reaplica a potência TX configurada: após reboot o módulo volta ao
  // default do fabricante, ignorando o config.json salvo.
  gYrm.setTxPower(gConfig.txPowerDbm());
#endif

  gEvents.load(LittleFS, kEventsPath);
  gEvents.setClockDeltaMs(gConfig.clockDeltaMs());

  gBle.init(TRAKR_DEVICE_NAME);
  gBle.setControlCallback(handleControlCommand);
  gBle.setOtaWriteCallback(otaWriteChunk);
  gBle.setHistory(gEvents.toJsonString());

  // Evento "boot": registrado em ligação/reset — não em wake pelo botão.
  if (wakeCause == ESP_SLEEP_WAKEUP_UNDEFINED) {
    pushEvent("boot", "", "");
  }

  // Rastreador: wake pelo botão já dispara a varredura; nos demais casos
  // entra em espera até receber comando (app) ou nova pressão do botão.
  if (wakeCause == ESP_SLEEP_WAKEUP_EXT0) {
    enter(State::LEITURA);
  } else {
    enter(State::ESCUTA);
  }
}

// ---------------- Loop ----------------
void loop() {
  esp_task_wdt_reset();

  // Healthcheck OTA: valida o FW novo ~10 s após o boot (rollback seguro).
  {
    static unsigned long healthcheckAt = millis() + 10000;
    if (!gOtaValidMarked && millis() >= healthcheckAt) otaHealthCheck();
  }

  switch (gState) {
    case State::ESCUTA:
      gLed.set(TrakLed::Color::READY);
      if (buttonPressed() || button2Pressed()) enter(State::LEITURA);
#ifdef TRAKR_HAS_MPU6050
      if (gSensors.isMoving()) { Serial.println("[TRAKR] Wake por movimento"); enter(State::LEITURA); }
#endif
      if (millis() - gStateSince >= gConfig.listenMs()) enter(State::DORME);
      break;

    case State::RASTREIA:
      radarSweepPublish();
      radarBeep(gLastRadarRssi);
      if (buttonPressed()) {
        gRadarTargetEpc = "";
        digitalWrite(BUZZER_PIN, LOW);
        enter(State::LEITURA);
      } else if (millis() - gStateSince >= gConfig.radarMs()) {
        gRadarTargetEpc = "";
        digitalWrite(BUZZER_PIN, LOW);
        enter(State::SINCRONIZA);
      }
      break;

    case State::LIVE:
      liveSweepPublish();
      gLed.set(TrakLed::Color::SCANNING);
      if (buttonPressed()) {
        enter(State::LEITURA);
      } else if (millis() - gStateSince >= gConfig.radarMs()) {
        enter(State::SINCRONIZA);
      }
      break;

    case State::MULTI:
      multiRadarPublish();
      radarBeep(gLastRadarRssi);
      if (buttonPressed()) {
        gMultiTargets.clear();
        gRadarTargetEpc = "";
        digitalWrite(BUZZER_PIN, LOW);
        enter(State::LEITURA);
      } else if (millis() - gStateSince >= gConfig.radarMs()) {
        gMultiTargets.clear();
        gRadarTargetEpc = "";
        digitalWrite(BUZZER_PIN, LOW);
        enter(State::SINCRONIZA);
      }
      break;

    case State::LEITURA:
      gLed.set(TrakLed::Color::SCANNING);
      sweepAndPublish();
      enter(State::SINCRONIZA);
      break;

    case State::SINCRONIZA: {
      gLed.set(TrakLed::Color::SYNC);
      static unsigned long lastOled = 0;
      if (millis() - lastOled > 2000) {
        lastOled = millis();
        BatteryInfo bi = gBatt.read();
        int present = 0;
        const char* missingName = nullptr;
        for (auto& t : gInventory.tools()) {
          if (t.present) {
            present++;
          } else if (missingName == nullptr) {
            missingName = t.name.c_str();
          }
        }
        int total = gInventory.tools().size();
        gOled.showStatus(present, total, gLastRadarRssi, bi.percent, gBle.notificationsEnabled(), missingName, gConfig.txPowerDbm());
#ifdef TRAKR_HAS_BME280
        EnvData env = gSensors.readBME();
        if (env.valid) Serial.printf("[TRAKR] BME: %.1fC %.0f%% %.0fhPa\n", env.temp, env.hum, env.press);
#endif
      }
      if (gBle.notificationsEnabled()) {
        // App conectado: re-varre periodicamente para manter o inventário
        // atualizado ao vivo (sem depender de "rescan" manual).
        static unsigned long lastMonitorSweep = 0;
        if (millis() - lastMonitorSweep >= kMonitorSweepMs) {
          lastMonitorSweep = millis();
          sweepAndPublish();
        }
        gStateSince = millis();
        break;
      }
      if (millis() - gStateSince >= kSyncWindowMs) enter(State::DORME);
      break;
    }

    case State::FINDME: {
      // Pulsa LED branco + tom a ~2 Hz, non-blocking (não trava o loop).
      const bool on = (millis() / 250) % 2 == 0;
      gLed.set(on ? TrakLed::Color::FINDME : TrakLed::Color::OFF);
#ifdef TRAKR_HAS_PASSIVE_BUZZER
      if (on) gHaptics.tone(2400); else gHaptics.noTone();
#else
      digitalWrite(BUZZER_PIN, on ? HIGH : LOW);
#endif
      if (buttonPressed() || millis() >= gFindEndAt) {
        gLed.set(TrakLed::Color::OFF);
        gHaptics.noTone();
        digitalWrite(BUZZER_PIN, LOW);
        enter(State::SINCRONIZA);
      }
      break;
    }

    case State::DORME:
      gLed.set(TrakLed::Color::OFF);
      goToSleep();
      // Nunca chega aqui (MF00).
      for (;;) delay(1000);
  }

  gLed.show();
  delay(50);
}