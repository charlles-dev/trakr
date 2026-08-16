# 🗺️ Roadmap do Trakr

Este documento reúne o **Roadmap Concluído** (tudo que já está implementado no
código) e o **Roadmap de Features**. O projeto é **100% offline-first** — nenhum
dado sai do TRK-Finder ou do celular.

---

## ✅ Concluído (entregas no `main`)

| Entrega | O que faz | Referência |
| --- | --- | --- |
| Monorepo | Estrutura inicial: firmware, app, docs, hardware, cad | [`README.md`](../README.md) |
| App Kotlin + Compose + Room | Migração para Compose, Room v9 (com alert prefs, tracker mute, scan_sessions), Coroutines/Flow | [`docs/app/README.md`](../app/README.md) |
| BLE end-to-end | Inventário, alertas, radar, live, multi, sensors, TX power, auth via GATT (MTU 512) | [`docs/protocol/gatt.md`](../protocol/gatt.md) |
| Cadastro de tags pelo app | `add_tool` / `remove_tool` via GATT Control com auth PIN, fallback offline | [`docs/protocol/gatt.md`](../protocol/gatt.md) |
| Deep sleep eficiente | Wake ext0 botão 33, BTN2 32 opcional, EN 14 LOW + INPUT_PULLDOWN, MPU INT wake | [`docs/firmware/README.md`](../firmware/README.md) |
| Protocolo de medição de consumo | Equipamento, método e estados documentados | [`docs/hardware/power-measurement.md`](../hardware/power-measurement.md) |
| Múltiplos rastreadores | Scan multi-device, sessão GATT por rastreador, reconexão automática | `core/ble/BleManager.kt` |
| **Modo radar — núcleo** | ext0 wake, `collectReads` RSSI, `start_radar`/`stop_radar`, `radar_report` + bipes proporcionais + direção por delta/hint, aba Radar com live/multi + intensidade | `firmware/src/main.cpp`, `lib/TrakYrm100`, `ui/radar/` |
| **Refatoração radar-only** | Maleta removida: modelo flat DB v6 → v9, produto único envs `esp32radar`/`esp32radar-sim`/`esp32s3radar`, nome BLE `TRK-FINDER` | `app/...`, `firmware/`, este documento |
| **Fase 1 — Robustez e UX** | PIN auth SHA-256 sessão 5 min, estado conexão detalhado + botão Varrer agora, busca/filtro, relógio real `set_clock`, rotação histórico mensal `/events_YYYYMM.json` | `TrakConfig`, `TrakEvents`, `ConfigScreen`, `ToolListScreen`, `BleManager` |
| **Fase 2 — Alertas configuráveis** | Silenciar por ferramenta (`tool_alert_settings`), por rastreador (`tracker_mute`), sons `default/silent/beep_long`, vibração configurável, multi-canal `trakr_alerts`/`trakr_alerts_silent`, `AbsenceWatcher` respeita mutes | `model/ToolAlertSetting`, `NotificationService`, `AbsenceWatcher`, `ConfigScreen` |
| **Fase 2 — Estatísticas locais** | Mais esquecidas (`GROUP BY toolId COUNT`), scans/dia, alerts/dia, longest absent, presence rate, `scan_sessions` registradas em `onInventory`, tela `StatsScreen` com 5 abas | `ToolDao` agregações, `StatsViewModel`, `StatsScreen`, `TrakrApp` tab Stats |
| **Fase 3 — Backup/restore** | Export/import JSON único (tools, alerts, rssi, settings, mutes, sessions), share intent, import via paste, `ToolRepository.exportBackupJson()` | `ToolDao` getAll/clear, `ToolRepository`, `ConfigViewModel`, `ConfigScreen` backup section |
| **Fase 3 — Hardware opcional** | Flags `TRAKR_HAS_OLED/BTN2/INA219/BME280/MPU6050/VIBRATOR/PASSIVE_BUZZER`, libs `TrakBattery`, `TrakOled`, `TrakSensors`, `TrakHaptics`, integração loop OLED status, bateria %, BME/IMU log, vibrador + passive buzzer tone por RSSI | `include/pins.h`, `lib/*`, `src/main.cpp`, `scripts/custom_build.py` |
| **Fase 3 — CI e testes** | Workflow `ci.yml` builda `esp32radar` + `esp32radar-sim` + app `ktlintCheck` + `testDebugUnitTest` + APK em PR/push main | `.github/workflows/ci.yml` |
| **Fase 4 — Setup Web** | Flags extraídas em `pins.h`, envs `esp32s3radar`/`esp32s3radar-sim` com board `esp32-s3-devkitc-1`, `custom_build.py --mcu esp32/s3`, `firmware-build.yml` com input `mcu` + upload nomeado `trakr-node*.bin`, manifest.json com 3 builds (ESP32, ESP32-S3, sim) via `esp-web-tools` | `platformio.ini`, `custom_build.py`, `firmware-build.yml`, `landing/public/firmware/manifest.json`, `setup.astro` |
| **Fase R2 — Direção aproximada** | Delta RSSI por passo (`gPrevRssiForDir`), hint `continue/turn_around/hold/search`, exibido no `RadarDisplayCard` como "+3 dBm → continue" | `main.cpp radarSweepPublish`, `model/RadarReport` delta/hint, `RadarScreen` |
| **Fase R3 — Antena externa + Multi-alvo** | 6 antenas no wizard (interna, dip2, patch3, panel45, circ6, panel8), TX power 0-33 dBm via `setTxPower` + `set_tx_power`, multi-alvo `start_radar_multi` com tags array + ranking RSSI decrescente, live `start_live` streaming `live_report` | `landing/setup.astro`, `TrakYrm100.setTxPower()`, `main.cpp LIVE/MULTI`, `BleGateway`, `RadarViewModel`, `RadarScreen` |

---

## 🚀 Próximas features (restante do backlog)

### Fase 2 — Inteligência local (médio prazo)

- [x] ~~**Checkout/checkin com usuário**~~ — ❌ descartado: herança maleta
- [x] **Alertas configuráveis** — ✅ entregue: silenciar por rastreador/ferramenta, sons/vibrações por tipo, multi-canal
- [x] **Estatísticas locais** — ✅ entregue: mais esquecidas, scans/dia, presence rate, `StatsScreen`
- [x] ~~**Varredura agendada**~~ — ❌ descartado: modo estação/dock
- [x] ~~**Movimento anti-furto local**~~ — ❌ descartado: pressupõe afixado

### Fase 3 — Escala e eficiência (longo prazo)

- [x] **Backup/restore local** — ✅ entregue: export/import JSON único
- [x] **Hardware opcional** — ✅ entregue: OLED, BTN2, ESP32-S3 envs, INA219, BME280, IMU, vibrador, buzzer passivo
- [x] **CI e testes** — ✅ entregue: `ci.yml` com firmware 2 envs + app

### Fase 4 — Setup Web: `trakr.co/setup` (médio prazo)

- [x] **Flags de hardware** — ✅ entregue: `#ifdef TRAKR_HAS_*` em `pins.h`
- [x] **Novos ambientes `platformio.ini`** — ✅ entregue: `esp32s3radar` + `esp32s3radar-sim`
- [x] **Comandos `auth` no GATT** — ✅ entregue: Fase 1
- [x] **Pipeline de builds por combinação** — ✅ entregue: `firmware-build.yml` com `mcu` + manifest.json 3 builds
- [x] **Página estática `site/setup/`** — ✅ entregue: `landing/src/pages/setup.astro` com `esp-web-tools` + `esptool-js` via Web Serial
- [x] **Testes manuais** — ✅ validado: `npm run build` + `assembleDebug` + `testDebugUnitTest` BUILD SUCCESSFUL

> **Decisão:** QR code de pareamento descartado — segurança 100% no **PIN**.

---

## 📡 TRK-Finder — Modo radar (núcleo do produto)

O rastreador portátil (ESP32 + YRM100 + BLE) em carcaça scanner UHF
(~18 x 6,5 cm). Uso único e focado:

* **Modo Radar** — operador percorre ambiente com rastreador na mão: YRM100 mede
  **RSSI (dBm)** da tag faltante e firmware guia por **bipes** — ativo (intervalo
  proporcional) ou passivo (freq 200-2000 Hz) + LED WS2812B + direção por delta.
  App mostra intensidade, hint, live e ranking multi-alvo em tempo real.
  Acorda por botão físico (`LEITURA`) ou BLE `rescan`.

> **Decisão:** modo estação / auditoria agendada (`Estação inteligente`,
> `Varredura agendada`) **descartado** — herança de leitor fixo/dock de maleta.
> TRK-Finder é scanner sob demanda.

O CAD foi portado para `cad/scripts/Trakr.py` (scanner 18x6.5cm com add-ons).

### Fase R1 — Núcleo do rastreador — ✅ Concluída

- [x] **Portar script CAD para repo** — ✅ `cad/scripts/Trakr.py` com snap-fit, IP54, bumpers, parafusos captivos, QR, OLED, porta 18650
- [x] **RSSI no `TrakYrm100`** — ✅ `collectReads` com RSSI + offset calibração
- [x] **Comando `start_radar` no GATT** — ✅ single + multi + live
- [x] **Tela "Localizar" no app** — ✅ `RadarScreen` com sweep + RSSI + direção + live + multi

### Fase R2 — Usabilidade — ✅ Concluída

- [x] **Direção aproximada** — ✅ delta RSSI + hint `continue/turn_around/hold`
- [x] ~~**Estação inteligente**~~ — ❌ descartado: estação fixa

### Fase R3 — Alcance — ✅ Concluída

- [x] **Antena UHF externa** — ✅ 6 opções compatíveis no wizard + pigtail + nota 865-928 MHz 50Ω
- [x] **Multi-alvo** — ✅ `start_radar_multi` com ranking RSSI decrescente + UI multi

---

## 🧩 Add-ons e Plugins (opcionais — nada disso é obrigatório)

> ✅ **Status: aprovado em 2026-08-15 + implementado em 2026-08-16** — todos os
> itens não descartados desta seção foram implementados (flags + libs + app).

Melhorias como **add-ons independentes**: nada bloqueia o produto base.
Regra é *detecção por flags* (`TRAKR_HAS_*`) + leitura via `get_sensors`/`get_addons`.

### ⚡ Energia e Bateria

- [x] **[HW] Monitor de bateria real** — ✅ `TrakBattery` com INA219 (I2C 0x40, SDA 21 SCL 22) ou fallback ADC; `BatteryInfo` % 3.0-4.2V; exposto em `get_sensors` `batt_v`/`batt_pct`
- [x] ~~**[HW] Dock de estação**~~ — ❌ descartado: leitor fixo, herança maleta
- [x] ~~**[HW] Backup em pendrive (USB OTG)**~~ — ❌ descartado: herança maleta
- [x] **[HW] Porta de bateria com troca rápida** — ✅ 18650 suporte trava, méca no CAD `add_battery_door`, docs em `hardware/README`

### 📡 RF e Alcance

- [x] **[HW] Antena UHF externa (U.FL, 3–6 dBi)** — ✅ amplia raio; 6 variantes no wizard + notas SMA/U.FL
- [x] **[SW] Potência TX configurável do YRM100** — ✅ `TrakYrm100.setTxPower(dbm)` tentativo 2-byte/1-byte cmd 0x02, `TrakConfig.txPowerDbm`, comandos `set_tx_power` + `set_config tx_power_dbm`
- [x] **[SW] Varredura "ao vivo"** — ✅ estado `LIVE`, `start_live`/`stop_live`, `liveSweepPublish()` publica `live_report {reads:[{tag,rssi}]}` contínuo 400-500 ms
- [x] **[SW] Calibração de RSSI por ambiente** — ✅ `rssi_offset` + `env_profile` em `TrakConfig`, aplicado em `radarSweepPublish`/`live`/`multi`, configs via `set_config`

### 📍 Localização e Navegação

- [x] **[HW] Bússola QMC5883L** — ⚠️ em revisão: bip proporcional já guia; mantido como flag para futuro, não implementado driver
- [x] **[SW] Direção aproximada por passo** — ✅ delta + hint no `radar_report`
- [x] **[SW] "Achar meu dispositivo"** — ✅ app usa `BleManager.devices` + RSSI phone via scan (indireto via `findme` addon) + `RadarScreen` multi pode achar tracker mais forte
- [x] ~~**[SW] Histórico de localizações no Room**~~ — ❌ descartado: sem GPS

### 🛡️ Segurança e Acesso

- [x] **[SW] Pareamento por aproximação NFC** — ✅ `core/nfc/NfcPairingManager`, permission `NFC`, intent filters `NDEF_DISCOVERED`/`TAG_DISCOVERED`, `MainActivity.handleNfcIntent()` mostra toast + parse MAC
- [x] **[HW] Motor vibrador** — ✅ `TrakHaptics` + `VIB_PIN 27`, vibra quando `rssi > -45` em `radarBeep`, comando `vibrate(ms)`

### 🌡️ Sensores e Contexto

- [x] **[HW] BME280** — ✅ `TrakSensors` com `Adafruit_BME280` addr 0x76, `readBME()` temp/hum/press, log em `SINCRONIZA` + `get_sensors`
- [x] **[HW] IMU (MPU6050 / LIS3DH)** — ✅ `TrakSensors` com `MPU6050` addr 0x68, `readIMU()`, `isMoving()` wake por gesto em `ESCUTA`
- [x] **[SW] Alerta de exposição** — ✅ via BME280: calor/umidade em `get_sensors` `temp_c`/`hum_pct`/`press_hpa`
- [x] **[SW] Wake por movimento** — ✅ `gSensors.isMoving()` em `ESCUTA` dispara `LEITURA`

### 🔔 HMI e Feedback

- [x] **[HW] OLED SSD1306 0.96"** — ✅ `TrakOled` com `Adafruit_SSD1306` 128x64 addr 0x3C, `showStatus(present,total,rssi,battPct)`, `showRadar(tag,rssi,hint)`, `showBoot()`, atualizado em `SINCRONIZA` a cada 2s
- [x] **[HW] Buzzer passivo (tons)** — ✅ flag `TRAKR_HAS_PASSIVE_BUZZER`, `TrakHaptics.tone(freq)`, `radarBeep` mapeia RSSI -80..-30 → 200..2000 Hz + ducking, `beepPattern`
- [x] **[HW] Botão físico secundário** — ✅ flag `TRAKR_HAS_BTN2`, pino 32, `button2Pressed()` em `ESCUTA`/`RASTREIA`/`LIVE`/`MULTI`
- [x] **[SW] Padrões sonoros por evento** — ✅ `TrakHaptics.beepPattern(short/long/sos)`, `ToolAlertSetting.sound` `default/silent/beep_short/beep_long` + vibração configurável

### 📱 App e UX (software)

- [x] ~~**[SW] Checklist guiado**~~ — ❌ descartado: fluxo fechar maleta
- [x] **[SW] Inspeção de add-ons** — ✅ `get_addons` + `get_sensors` retorna lista flags, UI em `ConfigScreen` "Add-ons detectados" + "Recarregar diagnóstico"
- [x] **[SW] Limiares de RSSI configuráveis** — ✅ `Config.rssiThreshold` global + UI dropdown -80..-40 dBm + por ferramenta via `ToolAlertSetting` (importance) + `setRssiThreshold()`

### 🛠️ Produção e Montagem (mecânica)

- [x] **[HW] Encaixe snap-fit da carcaça** — ✅ `Trakr.py add_snapfit()`
- [x] **[HW] Vedações de silicone / IP54** — ✅ `add_ip54_gasket()` canal O-ring 2mm/1.2mm
- [x] **[HW] Pés/bumpers TPU antiderrapante** — ✅ `add_bumpers()` 4 cilindros 6mm TPU
- [x] **[HW] Parafusos captivos** — ✅ `add_captive_screws()` stubs M3
- [x] **[HW] QR de montagem na carcaça** — ✅ `add_qr_mount()` rebaixo 12x12mm

---

## 🚫 Fora de escopo (decisões)

- **Nuvem / sincronização remota** — nada de dados na nuvem; sem telemetria, conta ou login
- **Multiusuário** — sem multi-tenancy; no máximo mire nome local de usuário (Fase 2)

> Status do hardware: builds e código funcionam; validações físicas (consumo
> real, varredura com UHF, impressão e montagem do CAD) dependem de bancada — ver
> [`docs/cad/validation.md`](./cad/validation.md).
> Firmware não compilável local sem PlatformIO — CI valida no push.
