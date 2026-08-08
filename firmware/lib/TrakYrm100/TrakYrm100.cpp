#include "TrakYrm100.h"

static const uint8_t IR_FRAME_HEADER = 0xBB;

void TrakYrm100::begin(HardwareSerial& serial, uint32_t baud, uint8_t rx, uint8_t tx) {
  port_ = &serial;
  port_->begin(baud, SERIAL_8N1, rx, tx);
}

void TrakYrm100::sendFrame(uint8_t cmd, const uint8_t* payload, uint8_t payloadLen) {
  uint8_t frame[64];
  uint8_t n = 0;
  frame[n++] = IR_FRAME_HEADER;
  frame[n++] = (uint8_t)(2 + payloadLen + 1); // addr + cmd + payload + xor
  frame[n++] = 0x00;                          // addr
  frame[n++] = cmd;
  for (uint8_t i = 0; i < payloadLen; i++) frame[n++] = payload[i];

  uint8_t chk = 0;
  for (uint8_t i = 0; i < n; i++) chk ^= frame[i];
  frame[n++] = chk;

  port_->write(frame, n);
  port_->flush();
}

bool TrakYrm100::awaitFrame(uint8_t& cmd, uint8_t* data, uint8_t& dataLen, uint32_t timeoutMs) {
  static uint8_t buf[64];
  static uint8_t n = 0;
  uint32_t start = millis();

  while (millis() - start < timeoutMs) {
    while (port_->available()) {
      uint8_t b = port_->read();

      if (n == 0) {
        if (b == IR_FRAME_HEADER) buf[n++] = b;
        continue;
      }
      if (n == 1) {
        // len inválido: tenta re-sincronizar
        if (b == 0 || b > 60) { n = 0; continue; }
        buf[n++] = b;
        continue;
      }

      buf[n++] = b;
      if (n == (uint16_t)(buf[1] + 2)) { // trama completa
        uint8_t chk = 0;
        for (uint8_t i = 0; i < n - 1; i++) chk ^= buf[i];
        if (chk == buf[n - 1]) {
          cmd = buf[3];            // addr(1) + cmd(1) => cmd em [3]
          dataLen = buf[1] - 3;    // remove addr + cmd + xor
          if (dataLen > 0) memcpy(data, &buf[4], dataLen);
          n = 0;
          return true;
        }
        n = 0; // checksum errado, aguarda próximo header
      }
    }
    // pequeno respiro para o loop de scan
    delay(5);
  }
  return false;
}

String TrakYrm100::hexEpc(const uint8_t* payload, uint8_t len) {
  static const char* HEX_CHARS = "0123456789ABCDEF";
  String out;
  for (uint8_t i = 0; i < len; i++) {
    out += HEX_CHARS[payload[i] >> 4];
    out += HEX_CHARS[payload[i] & 0x0F];
  }
  return out;
}

uint8_t TrakYrm100::collectEpc(std::vector<String>& outEpcs, uint32_t maxReadMs) {
  sendFrame(CMD_START_INVENTORY, nullptr, 0);

  uint8_t data[64];
  uint32_t start = millis();
  while (millis() - start < maxReadMs) {
    uint8_t dataLen = 0;
    uint8_t cmd = 0;
    if (!awaitFrame(cmd, data, dataLen, 100)) continue;

    if (cmd == CMD_START_INVENTORY || cmd == CMD_SINGLE_READ) {
      // Payload típico: [PC 2 bytes][EPC 12 bytes][RSSI 1 byte] por tag.
      uint8_t offset = 0;
      while (offset + 14 <= dataLen) {
        String epc = hexEpc(&data[offset + 2], 12);
        bool dup = false;
        for (auto& e : outEpcs) {
          if (e.equalsIgnoreCase(epc)) { dup = true; break; }
        }
        if (!dup) outEpcs.push_back(std::move(epc));
        offset += 14;
      }
    }
  }

  sendFrame(CMD_STOP_INVENTORY, nullptr, 0);
  return 0;
}

void TrakYrm100::disablePower() {
  // Sem pin de controle de energia mapeado nas docs atuais.
  // Se o YRM100 tiver EN, ligue-o aqui para desligar o rádio no deep sleep.
}