#include "TrakBle.h"
#include "ble_profile.h"

#include <ArduinoJson.h>

namespace {

class TrakBleServerCallbacks : public NimBLEServerCallbacks {
 public:
  explicit TrakBleServerCallbacks(TrakBle& outer) : outer_(outer) {}

  void onConnect(NimBLEServer* server, NimBLEConnInfo& connInfo) override {
    server->updateConnParams(connInfo.getConnHandle(), 24, 48, 0, 60);
    outer_.onClientConnected();
  }

  void onDisconnect(NimBLEServer* server, NimBLEConnInfo& connInfo,
                    int reason) override {
    (void)server;
    (void)connInfo;
    (void)reason;
    outer_.onClientDisconnected();
  }

 private:
  TrakBle& outer_;
};

class TrakBleCharCallbacks : public NimBLECharacteristicCallbacks {
 public:
  explicit TrakBleCharCallbacks(TrakBle* outer) : outer_(outer) {}

  void onWrite(NimBLECharacteristic* characteristic,
               NimBLEConnInfo& connInfo) override {
    (void)connInfo;
    if (!outer_) return;
    // OTA: stream binário bruto (chunks), não é texto.
    if (characteristic->getUUID() ==
        NimBLEUUID(std::string(TRAKR_CHAR_OTA_UUID))) {
      if (outer_->getOtaWriteCallback()) {
        const std::string value = characteristic->getValue();
        outer_->getOtaWriteCallback()(
            reinterpret_cast<const uint8_t*>(value.data()), value.size());
      }
      return;
    }
    String value = characteristic->getValue().c_str();
    if (outer_->getControlCallback()) outer_->getControlCallback()(value);
  }

  void onSubscribe(NimBLECharacteristic* characteristic, NimBLEConnInfo& connInfo,
                   const uint16_t subcode) override {
    (void)characteristic;
    (void)connInfo;
    if (outer_) outer_->onSubscriptionChanged(subcode != 0);
  }

 private:
  TrakBle* outer_;
};

}  // namespace

void TrakBle::init(const char* deviceName) {
  NimBLEDevice::init(deviceName);
  NimBLEDevice::setMTU(512);

  // Bonding + Secure Connections (Just Works, sem MITM): após o primeiro
  // pareamento o link fica criptografado e o pareamento é lembrado (NVS).
  NimBLEDevice::setSecurityAuth(true /*bonding*/, false /*mitm*/,
                                true /*secure_conn*/);
  NimBLEDevice::setSecurityIOCap(BLE_HS_IO_NO_INPUT_OUTPUT);

  NimBLEServer* server = NimBLEDevice::createServer();
  server->setCallbacks(new TrakBleServerCallbacks(*this));
  server->advertiseOnDisconnect(true);

  NimBLEService* service = server->createService(TRAKR_SERVICE_UUID);

  inventoryChar_ = service->createCharacteristic(
      TRAKR_CHAR_INVENTORY_UUID,
      NIMBLE_PROPERTY::READ | NIMBLE_PROPERTY::NOTIFY);
  inventoryChar_->setCallbacks(new TrakBleCharCallbacks(this));
  inventoryChar_->setValue((uint8_t*)"{}", 2);

  eventChar_ = service->createCharacteristic(
      TRAKR_CHAR_EVENT_UUID,
      NIMBLE_PROPERTY::READ | NIMBLE_PROPERTY::NOTIFY);
  eventChar_->setCallbacks(new TrakBleCharCallbacks(this));
  eventChar_->setValue((uint8_t*)"{}", 2);

  historyChar_ = service->createCharacteristic(
      TRAKR_CHAR_HISTORY_UUID,
      NIMBLE_PROPERTY::READ);
  historyChar_->setValue((uint8_t*)"[]", 2);

  NimBLECharacteristic* controlChar = service->createCharacteristic(
      TRAKR_CHAR_CONTROL_UUID,
      NIMBLE_PROPERTY::WRITE);
  controlChar->setCallbacks(new TrakBleCharCallbacks(this));

  // OTA: gravação de chunks binários (o app controla begin/end via Control).
  otaChar_ = service->createCharacteristic(
      TRAKR_CHAR_OTA_UUID,
      NIMBLE_PROPERTY::WRITE);
  otaChar_->setCallbacks(new TrakBleCharCallbacks(this));

  // v2.5: os serviços iniciam quando o servidor é iniciado.

  NimBLEAdvertising* advertising = NimBLEDevice::getAdvertising();
  advertising->setName(deviceName);
  advertising->addServiceUUID(TRAKR_SERVICE_UUID);
  NimBLEDevice::startAdvertising();

  Serial.println("[TRAKR] BLE iniciado com GATT pronto");
}

void TrakBle::notifyInventory(const String& json) {
  if (!inventoryChar_ || !notificationsEnabled()) return;
  notifyChunked(inventoryChar_, "inventory", json);
}

void TrakBle::notifyEvent(const String& json) {
  if (!eventChar_ || !notificationsEnabled()) return;
  notifyChunked(eventChar_, "event", json);
}

void TrakBle::notifyChunked(NimBLECharacteristic* ch, const char* kind,
                            const String& json) {
  if (json.length() <= kDirectMaxBytes) {
    // Payload pequeno: notify direto (compatível com versões antigas).
    ch->setValue((uint8_t*)json.c_str(), json.length());
    ch->notify();
    return;
  }

  const size_t total = json.length();
  const size_t count = (total + kChunkRawMaxBytes - 1) / kChunkRawMaxBytes;
  size_t offset = 0;
  for (size_t i = 0; i < count; ++i) {
    size_t len = min(kChunkRawMaxBytes, total - offset);
    if (offset + len < total) {
      // Não cortar no meio de um caractere UTF-8: recua enquanto o byte de
      // corte for continuação (0b10xxxxxx) ou a fatia terminar em byte
      // inicial de sequência multibyte.
      while (len > 0) {
        const uint8_t next = (uint8_t)json[offset + len];
        const uint8_t last = (uint8_t)json[offset + len - 1];
        const bool cutIsContinuation = (next & 0xC0) == 0x80;
        const bool lastIsAscii = last < 0x80;
        const bool lastStartsSeq = last >= 0xC0;
        if (!cutIsContinuation && (lastIsAscii || lastStartsSeq)) break;
        --len;
      }
    }
    if (len == 0) len = 1;  // UTF-8 inválido: não deixar loop infinito

    const String slice = json.substring(offset, offset + len);

    JsonDocument doc;
    doc["t"] = "chunk";
    doc["k"] = kind;
    doc["n"] = count;
    doc["i"] = i;
    doc["d"] = slice;
    String out;
    serializeJson(doc, out);

    ch->setValue((uint8_t*)out.c_str(), out.length());
    ch->notify();
    offset += len;
  }
}

void TrakBle::setHistory(const String& json) {
  if (!historyChar_) return;
  // READ do GATT é limitado ao MTU: se o histórico exceder, mantém os
  // eventos mais recentes (ficam no início do array) cortando numa fronteira
  // de vírgula para o JSON continuar válido.
  const size_t maxLen = 440;
  if (json.length() > maxLen) {
    size_t cut = maxLen;
    while (cut > 0 && json[cut] != ',') --cut;
    if (cut == 0) cut = maxLen;
    historyChar_->setValue((uint8_t*)json.c_str(), cut + 1);
  } else {
    historyChar_->setValue((uint8_t*)json.c_str(), json.length());
  }
}