#include "TrakYrm100.h"

#include "pins.h"

static const uint8_t IR_FRAME_HEADER = 0xBB;

void TrakYrm100::begin(HardwareSerial& serial, uint32_t baud, uint8_t rx, uint8_t tx) {
  port_ = &serial;
  baud_ = baud;
  rx_pin_ = rx;
  tx_pin_ = tx;
  enablePower();
}

void TrakYrm100::enablePower() {
  if (power_enabled_) return;
#ifdef YRM100_EN_PIN
  pinMode(YRM100_EN_PIN, OUTPUT);
  digitalWrite(YRM100_EN_PIN, HIGH);  // EN alto = módulo energizado
#endif
  if (port_) port_->begin(baud_, SERIAL_8N1, rx_pin_, tx_pin_);
  power_enabled_ = true;
  Serial.println("[TRAKR] YRM100 energizado");
}

void TrakYrm100::disablePower() {
  if (!power_enabled_) return;
#ifndef TRAKR_SIM
  // Sem EN: ao menos para a varredura antes de desligar o UART.
  sendFrame(CMD_STOP_INVENTORY, nullptr, 0);
#endif
  if (port_) port_->end();  // encerra UART2 (economiza corrente e evita ruído)
#ifdef YRM100_EN_PIN
  digitalWrite(YRM100_EN_PIN, LOW);
  pinMode(YRM100_EN_PIN, INPUT_PULLDOWN);  // trava LOW no deep sleep
#endif
  power_enabled_ = false;
  Serial.println("[TRAKR] YRM100 desenergizado");
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
  std::vector<TrakRead> reads;
  uint8_t result = collectReads(reads, maxReadMs);
  for (const auto& r : reads) outEpcs.push_back(r.epc);
  return result;
}

uint8_t TrakYrm100::collectReads(std::vector<TrakRead>& outReads, uint32_t maxReadMs) {
  sendFrame(CMD_START_INVENTORY, nullptr, 0);

  uint8_t data[64];
  uint32_t start = millis();
  while (millis() - start < maxReadMs) {
    uint8_t dataLen = 0;
    uint8_t cmd = 0;
    if (!awaitFrame(cmd, data, dataLen, 100)) continue;

    if (cmd == CMD_START_INVENTORY || cmd == CMD_SINGLE_READ) {
      // Payload típico: [PC 2 bytes][EPC 12 bytes][RSSI 1 byte opcional] por
      // tag. O stride por entrada é resolvido por frame: se o payload é
      // múltiplo de 15, cada entrada inclui RSSI (15 bytes); senão, entradas
      // de 14 bytes (PC+EPC) e o RSSI fica desconhecido (-100).
      const uint8_t stride = (dataLen % 15 == 0) ? 15 : 14;
      uint8_t offset = 0;
      while (offset + 14 <= dataLen) {
        String epc = hexEpc(&data[offset + 2], 12);
        int8_t rssi = (stride == 15 && offset + 15 <= dataLen)
                          ? (int8_t)data[offset + 14]
                          : -100;

        bool dup = false;
        for (auto& r : outReads) {
          if (r.epc.equalsIgnoreCase(epc)) {
            dup = true;
            if (rssi > r.rssi) r.rssi = rssi;  // mantém o sinal mais forte
            break;
          }
        }
        if (!dup) outReads.push_back(TrakRead{std::move(epc), rssi});
        offset += stride;
      }
    }
  }

  sendFrame(CMD_STOP_INVENTORY, nullptr, 0);
  return 0;
}

bool TrakYrm100::setTxPower(uint8_t dbm) {
  if (dbm > 33) dbm = 33;
  // Família M100: payload [power*100?][?]. Tentativa com 2 bytes little-endian power
  // Ex: 26 dBm => 2600 = 0x0A28 -> [0x28, 0x0A]. Algumas versões usam 1 byte direto.
  // Enviamos ambas tentativas e aceitamos qualquer ACK.
  uint8_t payload2[2];
  payload2[0] = (uint8_t)(dbm * 100 & 0xFF);
  payload2[1] = (uint8_t)((dbm * 100 >> 8) & 0xFF);
  sendFrame(CMD_SET_TX_POWER, payload2, 2);
  uint8_t cmd = 0;
  uint8_t data[16];
  uint8_t len = 0;
  if (awaitFrame(cmd, data, len, 500)) {
    last_tx_dbm_ = dbm;
    Serial.printf("[TRAKR] TX power set %u dBm (2-byte) ack cmd 0x%02X\n", dbm, cmd);
    return true;
  }
  // Fallback 1 byte
  uint8_t payload1[1] = {dbm};
  sendFrame(CMD_SET_TX_POWER, payload1, 1);
  if (awaitFrame(cmd, data, len, 500)) {
    last_tx_dbm_ = dbm;
    Serial.printf("[TRAKR] TX power set %u dBm (1-byte) ack\n", dbm);
    return true;
  }
  Serial.printf("[TRAKR] TX power set %u dBm falhou (sem ACK)\n", dbm);
  return false;
}

bool TrakYrm100::writeEpc(const uint8_t* epc, uint8_t len) {
  if (port_ == nullptr || epc == nullptr || len == 0 || len > 12) return false;

  // Payload: [access password 4 bytes = 0x00000000][novo EPC len bytes].
  // O número do comando (CMD_WRITE_EPC) é uma tentativa — valide no
  // datasheet da sua placa. O ACK do módulo é obrigatório para reportar
  // sucesso: falha de comunicação é reportada honestamente como falha.
  uint8_t payload[16] = {0, 0, 0, 0};
  memcpy(&payload[4], epc, len);

  sendFrame(CMD_WRITE_EPC, payload, 4 + len);

  uint8_t cmd = 0;
  uint8_t data[16];
  uint8_t dataLen = 0;
  bool ack = false;
  const uint32_t start = millis();
  while (millis() - start < 1000) {
    if (awaitFrame(cmd, data, dataLen, 100)) {
      ack = true;
      break;
    }
  }
  if (ack) {
    Serial.printf("[TRAKR] Write EPC aceito pelo módulo (cmd 0x%02X)\n", cmd);
    return true;
  }
  Serial.println("[TRAKR] Write EPC sem ACK do módulo — falha honesta");
  return false;
}