#include "TrakBle.h"
#include "ble_profile.h"

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

  NimBLECharacteristic* controlChar = service->createCharacteristic(
      TRAKR_CHAR_CONTROL_UUID,
      NIMBLE_PROPERTY::WRITE);
  controlChar->setCallbacks(new TrakBleCharCallbacks(this));

  // v2.5: os serviços iniciam quando o servidor é iniciado.

  NimBLEAdvertising* advertising = NimBLEDevice::getAdvertising();
  advertising->setName(deviceName);
  advertising->addServiceUUID(TRAKR_SERVICE_UUID);
  NimBLEDevice::startAdvertising();

  Serial.println("[TRAKR] BLE iniciado com GATT pronto");
}

void TrakBle::notifyInventory(const String& json) {
  if (!inventoryChar_ || !notificationsEnabled()) return;
  inventoryChar_->setValue((uint8_t*)json.c_str(), json.length());
  inventoryChar_->notify();
}

void TrakBle::notifyEvent(const String& json) {
  if (!eventChar_ || !notificationsEnabled()) return;
  eventChar_->setValue((uint8_t*)json.c_str(), json.length());
  eventChar_->notify();
}