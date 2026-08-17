#include "TrakOled.h"
#include "pins.h"

#ifdef TRAKR_HAS_OLED
#include <Wire.h>
#include <Adafruit_GFX.h>
#include <Adafruit_SSD1306.h>
static Adafruit_SSD1306 display(OLED_WIDTH, OLED_HEIGHT, &Wire, -1);
#endif

bool TrakOled::begin() {
#ifdef TRAKR_HAS_OLED
  Wire.begin(I2C_SDA_PIN, I2C_SCL_PIN, I2C_FREQ);
  ok_ = display.begin(SSD1306_SWITCHCAPVCC, OLED_ADDR);
  if (ok_) {
    showBoot();
    Serial.println("[TRAKR] OLED SSD1306 Tactical HUD inicializado com sucesso");
  } else {
    Serial.println("[TRAKR] OLED SSD1306 nao encontrado no barramento I2C");
  }
  return ok_;
#else
  return false;
#endif
}

void TrakOled::clear() {
#ifdef TRAKR_HAS_OLED
  if (!ok_) return;
  display.clearDisplay();
  display.display();
#endif
}

void TrakOled::drawChamferedBox(int x, int y, int w, int h, int cut, bool fill) {
#ifdef TRAKR_HAS_OLED
  if (fill) {
    display.fillRect(x + cut, y, w - (2 * cut), h, SSD1306_WHITE);
    display.fillRect(x, y + cut, w, h - (2 * cut), SSD1306_WHITE);
    display.fillTriangle(x, y + cut, x + cut, y, x + cut, y + cut, SSD1306_WHITE);
    display.fillTriangle(x + w - 1 - cut, y, x + w - 1, y + cut, x + w - 1 - cut, y + cut, SSD1306_WHITE);
    display.fillTriangle(x, y + h - 1 - cut, x + cut, y + h - 1, x + cut, y + h - 1 - cut, SSD1306_WHITE);
    display.fillTriangle(x + w - 1 - cut, y + h - 1 - cut, x + w - 1, y + h - 1 - cut, x + w - 1 - cut, y + h - 1, SSD1306_WHITE);
  } else {
    display.drawFastHLine(x + cut, y, w - (2 * cut), SSD1306_WHITE);
    display.drawFastHLine(x + cut, y + h - 1, w - (2 * cut), SSD1306_WHITE);
    display.drawFastVLine(x, y + cut, h - (2 * cut), SSD1306_WHITE);
    display.drawFastVLine(x + w - 1, y + cut, h - (2 * cut), SSD1306_WHITE);
    display.drawLine(x, y + cut, x + cut, y, SSD1306_WHITE);
    display.drawLine(x + w - 1 - cut, y, x + w - 1, y + cut, SSD1306_WHITE);
    display.drawLine(x, y + h - 1 - cut, x + cut, y + h - 1, SSD1306_WHITE);
    display.drawLine(x + w - 1 - cut, y + h - 1, x + w - 1, y + h - 1 - cut, SSD1306_WHITE);
  }
#endif
}

void TrakOled::drawBatteryIcon(int x, int y, int pct) {
#ifdef TRAKR_HAS_OLED
  pct = constrain(pct, 0, 100);
  // Contorno da bateria (14x7 px)
  display.drawRect(x, y, 14, 7, SSD1306_WHITE);
  display.drawFastVLine(x + 14, y + 2, 3, SSD1306_WHITE);
  
  // 4 segmentos internos
  int segments = (pct + 12) / 25; // 0..4
  for (int i = 0; i < segments; i++) {
    display.fillRect(x + 2 + (i * 3), y + 2, 2, 3, SSD1306_WHITE);
  }
#endif
}

void TrakOled::drawBleIcon(int x, int y) {
#ifdef TRAKR_HAS_OLED
  // Símbolo Bluetooth estilizado 5x7 px
  display.drawLine(x + 2, y, x + 2, y + 6, SSD1306_WHITE);
  display.drawLine(x + 2, y, x + 4, y + 2, SSD1306_WHITE);
  display.drawLine(x + 4, y + 2, x, y + 4, SSD1306_WHITE);
  display.drawLine(x, y + 2, x + 4, y + 4, SSD1306_WHITE);
  display.drawLine(x + 4, y + 4, x + 2, y + 6, SSD1306_WHITE);
#endif
}

void TrakOled::drawRfBadge(int x, int y, uint8_t dbm) {
#ifdef TRAKR_HAS_OLED
  display.fillRect(x, y, 22, 7, SSD1306_WHITE);
  display.setTextColor(SSD1306_BLACK, SSD1306_WHITE);
  display.setTextSize(1);
  display.setCursor(x + 1, y);
  display.printf("P%02d", dbm);
  display.setTextColor(SSD1306_WHITE, SSD1306_BLACK);
#endif
}

void TrakOled::drawSegmentedBar(int x, int y, int w, int h, int percent) {
#ifdef TRAKR_HAS_OLED
  percent = constrain(percent, 0, 100);
  int numBlocks = (w - 2) / 4; // blocos de 3px com 1px de espaçamento
  int activeBlocks = (percent * numBlocks) / 100;
  display.drawRect(x, y, w, h, SSD1306_WHITE);
  for (int i = 0; i < activeBlocks; i++) {
    display.fillRect(x + 2 + (i * 4), y + 2, 2, h - 4, SSD1306_WHITE);
  }
#endif
}

void TrakOled::drawTopBar(const char* title, float battPct, bool bleConnected, uint8_t rfPower) {
#ifdef TRAKR_HAS_OLED
  display.setTextSize(1);
  display.setTextColor(SSD1306_WHITE);
  
  // Título com marcador tático
  display.setCursor(0, 1);
  display.printf("■ %s", title);

  // Badge de potência RF
  drawRfBadge(68, 1, rfPower);

  // Indicador BLE
  if (bleConnected) {
    drawBleIcon(94, 1);
  }

  // Indicador Bateria
  if (battPct >= 0.0f) {
    drawBatteryIcon(106, 1, (int)battPct);
  }

  // Linha tática dupla
  display.drawFastHLine(0, 9, 128, SSD1306_WHITE);
#endif
}

void TrakOled::showStatus(
    int present,
    int total,
    int rssi,
    float battPct,
    bool bleConnected,
    const char* missingName,
    uint8_t rfPower
) {
#ifdef TRAKR_HAS_OLED
  if (!ok_) return;
  display.clearDisplay();
  drawTopBar("TRK-FINDER", battPct, bleConnected, rfPower);

  // --- CARD ESQUERDO: CONTADOR TÁTICO (x: 0, y: 13, w: 46, h: 49) ---
  drawChamferedBox(0, 13, 46, 49, 3, false);
  display.setTextSize(1);
  display.setCursor(6, 17);
  display.print("TOTAL");

  display.setTextSize(2);
  display.setCursor(5, 29);
  display.printf("%02d", present);
  
  display.setTextSize(1);
  display.setCursor(30, 36);
  display.printf("/%02d", total);

  // Mini barra de conformidade embaixo do número
  int pctTools = total > 0 ? (present * 100) / total : 100;
  drawSegmentedBar(4, 52, 38, 6, pctTools);

  // --- CARD DIREITO: STATUS & DETALHES (x: 50, y: 13, w: 78, h: 49) ---
  int missing = total - present;
  if (missing <= 0) {
    // NOMINAL
    drawChamferedBox(50, 13, 78, 16, 2, true);
    display.setTextColor(SSD1306_BLACK, SSD1306_WHITE);
    display.setTextSize(1);
    display.setCursor(54, 17);
    display.print("● NOMINAL");
    display.setTextColor(SSD1306_WHITE, SSD1306_BLACK);

    display.setCursor(54, 33);
    display.print("INVENTARIO OK");
    display.setCursor(54, 45);
    display.print("100% SEGURO");
  } else {
    // ALERTA DE AUSÊNCIA
    drawChamferedBox(50, 13, 78, 16, 2, true);
    display.setTextColor(SSD1306_BLACK, SSD1306_WHITE);
    display.setTextSize(1);
    display.setCursor(54, 17);
    display.printf("! %d AUSENTE", missing);
    display.setTextColor(SSD1306_WHITE, SSD1306_BLACK);

    display.setCursor(54, 33);
    if (missingName && strlen(missingName) > 0) {
      char buf[12];
      strncpy(buf, missingName, 10);
      buf[10] = '\0';
      display.printf("%s", buf);
    } else {
      display.print("REQUER BUSCA");
    }

    display.setCursor(54, 45);
    display.print("ACIONE O RADAR");
  }

  display.display();
#endif
}

void TrakOled::showRadar(
    const char* toolName,
    const char* tag,
    int rssi,
    const char* hint,
    float battPct,
    uint8_t rfPower
) {
#ifdef TRAKR_HAS_OLED
  if (!ok_) return;
  display.clearDisplay();
  
  // Custom Top Bar to match ASCII: "■ TRK-FINDER v3.0"
  drawTopBar("TRK-FINDER", battPct, true, rfPower);

  // --- RETÍCULO OCTAGONAL (Centro: 24, 31, Raio: 18) ---
  int cx = 24;
  int cy = 31;
  int r = 18;
  int a = 8; // approx r * tan(22.5)

  // Octagon
  display.drawLine(cx - a, cy - r, cx + a, cy - r, SSD1306_WHITE);
  display.drawLine(cx + a, cy - r, cx + r, cy - a, SSD1306_WHITE);
  display.drawLine(cx + r, cy - a, cx + r, cy + a, SSD1306_WHITE);
  display.drawLine(cx + r, cy + a, cx + a, cy + r, SSD1306_WHITE);
  display.drawLine(cx + a, cy + r, cx - a, cy + r, SSD1306_WHITE);
  display.drawLine(cx - a, cy + r, cx - r, cy + a, SSD1306_WHITE);
  display.drawLine(cx - r, cy + a, cx - r, cy - a, SSD1306_WHITE);
  display.drawLine(cx - r, cy - a, cx - a, cy - r, SSD1306_WHITE);

  // Arrows (▲ ▼ ◄ ►)
  display.fillTriangle(cx, cy - r + 3, cx - 2, cy - r + 6, cx + 2, cy - r + 6, SSD1306_WHITE);
  display.fillTriangle(cx, cy + r - 3, cx - 2, cy + r - 6, cx + 2, cy + r - 6, SSD1306_WHITE);
  display.fillTriangle(cx - r + 3, cy, cx - r + 6, cy - 2, cx - r + 6, cy + 2, SSD1306_WHITE);
  display.fillTriangle(cx + r - 3, cy, cx + r - 6, cy - 2, cx + r - 6, cy + 2, SSD1306_WHITE);

  // Center mark
  display.drawPixel(cx, cy, SSD1306_WHITE);

  // Blip da Tag Alvo
  sweepAngleStep_ = (sweepAngleStep_ + 1) % 16;
  float rad = (sweepAngleStep_ * 3.14159f * 2.0f) / 16.0f;
  
  if (rssi > -99) {
    int dist = map(constrain(rssi, -85, -30), -85, -30, r - 5, 2);
    int bx = cx + (int)(cos(rad + 1.2f) * dist);
    int by = cy + (int)(sin(rad + 1.2f) * dist);
    display.fillCircle(bx, by, 2, SSD1306_WHITE);
  }

  // --- PAINEL DIREITO ---
  int rx = 48;
  display.setTextSize(1);
  
  // TAG
  display.setCursor(rx, 12);
  display.print("TAG:");
  if (toolName && strlen(toolName) > 0) {
    char nameBuf[10];
    strncpy(nameBuf, toolName, 9);
    nameBuf[9] = '\0';
    display.print(nameBuf);
  } else if (tag && strlen(tag) > 4) {
    display.printf("..%s", tag + strlen(tag) - 4);
  } else {
    display.print("ALVO");
  }

  // SINAL
  display.setCursor(rx, 22);
  if (rssi > -99) {
    display.printf("SINAL:%ddBm", rssi);
  } else {
    display.print("SINAL:---");
  }

  // PROX + Bar
  display.setCursor(rx, 32);
  display.print("PROX:");
  int proxPct = map(constrain(rssi, -90, -30), -90, -30, 0, 100);
  if (rssi <= -99) proxPct = 0;
  drawSegmentedBar(rx + 30, 32, 26, 7, proxPct);
  display.setCursor(rx + 58, 32);
  display.printf("%d%%", proxPct);

  // GUIA
  display.setCursor(rx, 42);
  display.print("GUIA:");
  if (strcmp(hint, "continue") == 0 || rssi > -45) {
    display.print("AVANCE");
  } else if (strcmp(hint, "turn_around") == 0) {
    display.print("RECUE");
  } else if (strcmp(hint, "hold") == 0) {
    display.print("MANTENHA");
  } else {
    display.print("BUSCANDO");
  }

  // --- ALERTA FOOTER ---
  display.drawFastHLine(0, 52, 128, SSD1306_WHITE);
  display.setCursor(0, 55);
  if (rssi <= -99) {
    display.print("! ALERTA: SEM SINAL !");
  } else if (rssi < -80) {
    display.print("! ALERTA: SINAL FRACO");
  } else {
    display.print("* RASTREAMENTO ATIVO");
  }

  display.display();
#endif
}

void TrakOled::showBoot(const char* version) {
#ifdef TRAKR_HAS_OLED
  if (!ok_) return;
  display.clearDisplay();

  // Borda militar chanfrada dupla
  drawChamferedBox(0, 0, 128, 64, 4, false);
  drawChamferedBox(3, 3, 122, 58, 3, false);

  display.setTextSize(2);
  display.setTextColor(SSD1306_WHITE);
  display.setCursor(18, 12);
  display.print("T R A K R");

  display.setTextSize(1);
  display.setCursor(24, 32);
  display.print("TRK-FINDER v3.0");

  display.setCursor(16, 44);
  display.print("TACTICAL EDITION");

  // Barra de carregamento
  drawSegmentedBar(12, 54, 104, 5, 100);

  display.display();
#endif
}

void TrakOled::showSync(int count, const char* msg) {
#ifdef TRAKR_HAS_OLED
  if (!ok_) return;
  display.clearDisplay();
  drawTopBar("SYNC FLASH", -1.0f, true);

  drawChamferedBox(10, 16, 108, 42, 3, false);
  display.setTextSize(1);
  display.setCursor(18, 22);
  display.print(msg);

  display.setCursor(18, 34);
  display.printf("Total: %02d itens", count);

  drawSegmentedBar(18, 46, 90, 6, 80);

  display.display();
#endif
}

void TrakOled::showBeaconAlert(int remainingSec) {
#ifdef TRAKR_HAS_OLED
  if (!ok_) return;
  display.clearDisplay();
  
  display.fillRect(0, 0, 128, 64, SSD1306_WHITE);
  display.setTextColor(SSD1306_BLACK, SSD1306_WHITE);
  
  display.setTextSize(2);
  display.setCursor(16, 12);
  display.print("! BEACON !");

  display.setTextSize(1);
  display.setCursor(12, 34);
  display.print("LOCALIZANDO FINDER");

  display.setCursor(34, 48);
  display.printf("TEMPO: %02ds", remainingSec);

  display.setTextColor(SSD1306_WHITE, SSD1306_BLACK);
  display.display();
#endif
}
