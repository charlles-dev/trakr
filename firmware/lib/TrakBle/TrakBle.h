#pragma once

// ===== Trakr - Nidor GATT BLE (NimBLE-Arduino) =====
// Expõe:
//  - Inventory (notify/read): inventário em JSON (mesmo formato do .json)
//  - Event     (notify)     : eventos {type, tool_id, ...}
//  - Control   (write)      : comandos do app, ex: {"cmd":"rescan"}

#include <NimBLEDevice.h>
#include <Arduino.h>
#include <functional>

class TrakBle {
 public:
  using ControlCallback = std::function<void(const String& json)>;

  void init(const char* deviceName);
  void setControlCallback(ControlCallback cb) { control_cb_ = std::move(cb); }
  const ControlCallback& getControlCallback() const { return control_cb_; }

  bool hasClient() const { return client_connected_; }
  bool notificationsEnabled() const { return notifications_enabled_; }

  void notifyInventory(const String& json);
  void notifyEvent(const String& json);

  // chamado pelos callbacks NimBLE
  void onClientConnected() { client_connected_ = true; }
  void onClientDisconnected() { client_connected_ = false; notifications_enabled_ = false; }
  void onSubscriptionChanged(bool enabled) { notifications_enabled_ = enabled; }

 private:
  NimBLECharacteristic* inventoryChar_ = nullptr;
  NimBLECharacteristic* eventChar_ = nullptr;
  bool client_connected_ = false;
  bool notifications_enabled_ = false;
  ControlCallback control_cb_;
};