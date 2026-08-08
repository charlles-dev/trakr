// ===== Trakr - Firmware ESP32 (Máquina de Estados) =====
//
// Estados:
//   LEITURA     -> varre o YRM100 e atualiza o inventário
//   MONITOR     -> cruza leituras com o inventário local
//   ALERTA      -> alarme local (buzzer/LED) se ferramenta sumiu
//   SINCRONIZA  -> mantém BLE vivo para o app sincronizar
//   DORME       -> deep sleep (wake-up magnético via Sensor Hall)
//
// Pinagem: include/pins.h | GATT: include/ble_profile.h
// Se compilar com -DTRAKR_SIM, gera leituras simuladas (sem o YRM100).

#include <Arduino.h>
#include <LittleFS.h>
#include <esp_sleep.h>

#include <ArduinoJson.h>

#include "pins.h"
#include "ble_profile.h"
#include "TrakInventory.h"
#include "TrakYrm100.h"
#include "TrakBle.h"
#include "TrakLed.h"

static TrakInventory gInventory;
static TrakYrm100 gYrm;
static TrakBle gBle;
static TrakLed gLed;

static const char* kInventoryPath = "/inventory.json";

enum class State {
  LEITURA,
  MONITOR,
  ALERTA,
  SINCRONIZA,
  DORME,
};

static State gState = State::MONITOR;
static unsigned long gStateSince = 0;

static const unsigned long kSweepMs = 500;        // varredura documentada em ~500 ms
static const unsigned long kAlertMs = 3000;       // duração do alarme local
static const unsigned long kSyncWindowMs = 30000; // janela BLE pós-varredura

static void enter(const State s) {
  gState = s;
  gStateSince = millis();
}

// ---------------- GPIO ----------------
static void initInputs() {
  pinMode(HALL_PIN, INPUT_PULLUP);
}

static void initFeedback() {
  pinMode(BUZZER_PIN, OUTPUT);
  digitalWrite(BUZZER_PIN, LOW);
  gLed.begin(LED_RGB_PIN);
}

// ---------------- Alarmes ----------------
static void alarmPulse() {
  const bool on = (millis() % 600) < 300;
  digitalWrite(BUZZER_PIN, on ? HIGH : LOW);
  gLed.set(on ? TrakLed::Color::ALERT : TrakLed::Color::OFF);
}

static void alarmOff() {
  digitalWrite(BUZZER_PIN, LOW);
  gLed.set(TrakLed::Color::OFF);
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

  for (const auto* tool : gInventory.newlyMissing()) {
    String ev = "{\"type\":\"tool_missing\",\"tool_id\":\"";
    ev += tool->id;
    ev += "\",\"name\":\"";
    ev += tool->name;
    ev += "\"}";
    Serial.printf("[TRAKR] !!! Ferramenta ausente: %s\n", tool->name.c_str());
    gBle.notifyEvent(ev);
  }
}

// ---------------- Comandos do app (JSON via GATT Control) ----------------
static void handleControlCommand(const String& json) {
  Serial.printf("[TRAKR] Comando recebido: %s\n", json.c_str());

  JsonDocument doc;
  const DeserializationError err = deserializeJson(doc, json);
  if (err) {
    Serial.printf("[TRAKR] JSON inválido: %s\n", err.c_str());
    return;
  }

  const char* cmd = doc["cmd"] | "";

  if (strcmp(cmd, "rescan") == 0) {
    enter(State::LEITURA);
    return;
  }

  bool changed = false;

  if (strcmp(cmd, "add_tool") == 0) {
    const char* name = doc["name"] | "";
    const char* tag = doc["tag"] | "";
    if (strlen(name) == 0 || strlen(tag) == 0) {
      Serial.println("[TRAKR] add_tool: nome/tag obrigatórios");
      return;
    }
    changed = gInventory.addTool(name, tag);
    if (!changed) {
      Serial.println("[TRAKR] add_tool: EPC já cadastrado");
      return;
    }
  } else if (strcmp(cmd, "remove_tool") == 0) {
    const char* id = doc["id"] | "";
    changed = gInventory.removeTool(id);
    if (!changed) {
      Serial.printf("[TRAKR] remove_tool: id %s não encontrado\n", id);
      return;
    }
  } else {
    Serial.printf("[TRAKR] Comando desconhecido: %s\n", cmd);
    return;
  }

  // Persistência no LittleFS (fonte da verdade) + publicação via BLE.
  if (!gInventory.save(LittleFS, kInventoryPath)) {
    Serial.println("[TRAKR] ERRO: falha ao salvar inventory.json");
    return;
  }
  gBle.notifyInventory(gInventory.toJsonString());
}

// ---------------- Deep sleep ----------------
static void goToSleep() {
  alarmOff();
  Serial.println("[TRAKR] Deep sleep (aguardando gatilho magnético)...");
  Serial.flush();

  gYrm.disablePower();

  esp_sleep_enable_ext0_wakeup((gpio_num_t)HALL_PIN, HALL_WAKE_LEVEL);
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
  initFeedback();
  gYrm.begin(Serial2, YRM100_BAUD, YRM100_RX_PIN, YRM100_TX_PIN);

  gBle.init(TRAKR_DEVICE_NAME);
  gBle.setControlCallback(handleControlCommand);

  enter(State::LEITURA);
}

// ---------------- Loop ----------------
void loop() {
  switch (gState) {
    case State::LEITURA:
      gLed.set(TrakLed::Color::SCANNING);
      sweepAndPublish();
      enter(gInventory.newlyMissing().empty() ? State::MONITOR : State::ALERTA);
      break;

    case State::ALERTA:
      gLed.set(TrakLed::Color::ALERT);
      alarmPulse();
      if (millis() - gStateSince >= kAlertMs) {
        alarmOff();
        enter(State::SINCRONIZA);
      }
      break;

    case State::MONITOR:
      gLed.set(TrakLed::Color::READY);
      // Sem pendências: vai para sincronização mesmo assim (5 s) e depois dorme.
      if (millis() - gStateSince >= 5000) enter(State::SINCRONIZA);
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