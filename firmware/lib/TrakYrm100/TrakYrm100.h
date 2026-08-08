#pragma once

// ===== Trakr - Driver do Leitor UHF YRM100 (UART2) =====
//
// ATENÇÃO: os números de comando abaixo seguem o protocolo UART com trama
// "0xBB + checksum XOR" usado pela família de módulos YRM100/YR100.
// Se a sua placa usar outra versão de firmware, TODOS os comandos novos
// devem ser validados com o datasheet (colocar o PDF em hardware/datasheets/).
//
// Trama: [0xBB] [len] [addr=0x00] [cmd] [data...] [xor]
//   len        = addr(1) + cmd(1) + payload(N) + checksum(1)
//   xor        = XOR de 0xBB .. último byte do payload

#include <Arduino.h>
#include <vector>

class TrakYrm100 {
 public:
  void begin(HardwareSerial& serial, uint32_t baud, uint8_t rx, uint8_t tx);

  // Executa uma varredura contínua de inventário por maxReadMs.
  // Retorna lista de EPCs (hex, 12 bytes) lidos. 0 = sucesso, 1 = falha.
  uint8_t collectEpc(std::vector<String>& outEpcs, uint32_t maxReadMs);

  void disablePower();  // desliga o rádio para economizar energia

 private:
  HardwareSerial* port_ = nullptr;

  void sendFrame(uint8_t cmd, const uint8_t* payload, uint8_t payloadLen);
  bool awaitFrame(uint8_t& cmd, uint8_t* data, uint8_t& dataLen, uint32_t timeoutMs);
  String hexEpc(const uint8_t* payload, uint8_t len);
};

// ---- Comandos (calibrar com o datasheet da placa) ----
enum YrmCmd : uint8_t {
  CMD_GET_VERSION    = 0x01,
  CMD_START_INVENTORY = 0x15,  // varredura contínua (multiple tags)
  CMD_STOP_INVENTORY  = 0x16,
  CMD_SINGLE_READ     = 0x17,
};