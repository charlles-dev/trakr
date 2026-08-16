// ===== TRK-Finder - Firmware ESP32 (Rastreador Portátil) =====
//
// Estados:
//   ESCUTA     -> espera ativa (botão ou comando BLE para agir)
//   RASTREIA   -> modo radar: procura a tag alvo, mede RSSI e guia por LED/bip
//   LEITURA    -> varre o YRM100 uma vez e publica o inventário
//   SINCRONIZA -> janela BLE para o app sincronizar
//   DORME      -> deep sleep (wake por botão)
//
// Pinagem: include/pins.h | GATT: include/ble_profile.h
// Se compilar com -DTRAKR_SIM, gera leituras simuladas (sem o YRM100).

#include <Arduino.h>
#include <LittleFS.h>
#include <esp_ota_ops.h>
#include <esp_sleep.h>

#include <ArduinoJson.h>

#include "pins.h"
#include "ble_profile.h"
#include "TrakInventory.h"
#include "TrakYrm100.h"
#include "TrakBle.h"
#include "TrakLed.h"
#include "TrakEvents.h"

static TrakInventory gInventory;
static TrakYrm100 gYrm;
static TrakBle gBle;
static TrakLed gLed;
static TrakEvents gEvents;

static const char* kInventoryPath = "/inventory.json";
static const char* kEventsPath = "/events.json";

enum class State {
  ESCUTA,    // rastreador em espera (botão ou comando para agir)
  RASTREIA,  // rastreador procurando a tag alvo (modo radar)
  LEITURA,   // varredura manual (botão) — publica o inventário
  SINCRONIZA,
  DORME,
};

static State gState = State::ESCUTA;
static unsigned long gStateSince = 0;

static const unsigned long kListenMs = 30000;     // espera em ESCUTA antes de dormir
static const unsigned long kRadarMs = 120000;     // duração máxima do modo radar
static const unsigned long kRadarSweepMs = 400;   // varredura por ciclo do modo radar
static const unsigned long kSweepMs = 500;        // varredura documentada em ~500 ms
static const unsigned long kSyncWindowMs = 30000; // janela BLE pós-varredura

static void enter(const State s) {
  gState = s;
  gStateSince = millis();
}

// ---------------- GPIO ----------------
static void initInputs() {
  pinMode(BUTTON_PIN, INPUT_PULLDOWN);
}

// Detecção de borda do botão físico com debounce (~300 ms).
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

static void initFeedback() {
  pinMode(BUZZER_PIN, OUTPUT);
  digitalWrite(BUZZER_PIN, LOW);
  gLed.begin(LED_RGB_PIN);
}

// ---------------- Histórico de eventos ----------------
// Persiste em events.json (LittleFS) e publica via GATT (Event notify).

static void pushEvent(const String& type, const String& id, const String& name) {
  String ev = gEvents.add(type, id, name);
  gEvents.save(LittleFS, kEventsPath);
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
}

// ---------------- Modo radar (rastreador portátil) ----------------
// Publica {"type":"radar_report","tag":...,"rssi":...,"present":...} via
// Event notify (NÃO persiste no histórico) e ajusta LED + buzzer conforme a
// potência do sinal da tag alvo.

static String gRadarTargetEpc;  // EPC da ferramenta procurada (vazio = desligado)
static int8_t gLastRadarRssi = -100;  // último sinal medido (para o bip)

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
        if (r.rssi > bestRssi) bestRssi = r.rssi;
      }
    }
#else
    // Modo simulado: o sinal "oscila" com o tempo como se a tag estivesse
    // se aproximando e afastando do leitor.
    static uint32_t iter = 0;
    found = true;
    bestRssi = -72 + (int8_t)((iter++ / 4) % 36);  // -72 .. -37 dBm
#endif
  }

  JsonDocument doc;
  doc["type"] = "radar_report";
  doc["tag"] = gRadarTargetEpc;
  doc["rssi"] = found ? bestRssi : -100;
  doc["present"] = found;
  String out;
  serializeJson(doc, out);
  gBle.notifyEvent(out);
  gLastRadarRssi = found ? bestRssi : -100;
  Serial.printf("[TRAKR] Radar: %s (%ddBm)\n", gRadarTargetEpc.c_str(), bestRssi);

  // LED por intensidade: azul (procurando) -> ciano (sinal) -> verde (perto).
  if (!found) {
    gLed.set(TrakLed::Color::SCANNING);
  } else if (bestRssi > -45) {
    gLed.set(TrakLed::Color::READY);
  } else {
    gLed.set(TrakLed::Color::SYNC);
  }
}

// Bip "detector de metais": quanto mais forte o sinal (rssi mais próximo de
// 0), menor o intervalo e mais rápido o bip. Sem sinal = bip lento e espaçado.
static void radarBeep(int8_t rssi) {
  long interval = rssi >= -80 ? map(constrain(rssi, -80, -30), -80, -30, 1000, 100)
                              : 1000;
  const bool on = (millis() % (interval * 2)) < interval;
  digitalWrite(BUZZER_PIN, on ? HIGH : LOW);
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

  if (strcmp(cmd, "rescan") == 0) {
    enter(State::LEITURA);
    replyControl("rescan", "ok");
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
    if (target == nullptr) {
      for (const auto& t : gInventory.tools()) {
        if (t.id == id) {
          target = &t;
          break;
        }
      }
    }
    if (target != nullptr) {
      gRadarTargetEpc = target->epc;
      digitalWrite(BUZZER_PIN, LOW);
      Serial.printf("[TRAKR] Radar iniciado: %s (%s)\n", target->name.c_str(), id);
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

// ---------------- Setup ----------------
void setup() {
  Serial.begin(115200);
  delay(200);

  const uint32_t wakeCause = esp_sleep_get_wakeup_cause();
  Serial.printf("[TRAKR] Boot | wake cause: %" PRIu32 "\n", wakeCause);

  if (!LittleFS.begin()) {
    Serial.println("[TRAKR] ERRO: falha ao montar LittleFS");
  }

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

  gEvents.load(LittleFS, kEventsPath);

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
  switch (gState) {
    case State::ESCUTA:
      // Espera ativa: LED verde, dorme após o tempo sem interação.
      gLed.set(TrakLed::Color::READY);
      if (buttonPressed()) enter(State::LEITURA);
      if (millis() - gStateSince >= kListenMs) enter(State::DORME);
      break;

    case State::RASTREIA:
      radarSweepPublish();
      radarBeep(gLastRadarRssi);
      if (buttonPressed()) {
        gRadarTargetEpc = "";
        digitalWrite(BUZZER_PIN, LOW);
        enter(State::LEITURA);
      } else if (millis() - gStateSince >= kRadarMs) {
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

    case State::SINCRONIZA:
      gLed.set(TrakLed::Color::SYNC);
      if (gBle.notificationsEnabled()) {
        gStateSince = millis(); // renovar janela enquanto houver app ouvindo
        break;
      }
      if (millis() - gStateSince >= kSyncWindowMs) enter(State::DORME);
      break;

    case State::DORME:
      gLed.set(TrakLed::Color::OFF);
      goToSleep();
      // Nunca chega aqui (MF00).
      for (;;) delay(1000);
  }

  gLed.show();
  delay(50);
}