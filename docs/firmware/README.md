Guia de Firmware (ESP32 + LittleFS)
===================================

O firmware do Trakr é o cérebro autônomo do **TRK-Finder** (rastreador
portátil). Ele foi escrito em C++ e é recomendado utilizar o **PlatformIO**
(extensão do VS Code) para compilação.

Ambiente de Desenvolvimento
---------------------------

1. Instale o VS Code.

2. Instale a extensão PlatformIO IDE.

3. Abra a pasta `trakr/firmware/` no VS Code. O PlatformIO fará o download do SDK do ESP32 automaticamente.

Dependências (platformio.ini)
-----------------------------

* `bblanchon/ArduinoJson` (Para manipular o `inventory.json`)

* `h2zero/NimBLE-Arduino` (Servidor GATT para notificar o app Android)

* `fastled/FastLED` (Driver do LED RGB WS2812B — ver `lib/TrakLed`)

* **YRM100** comunicação via **UART2 de hardware** (`Serial2`) — GPIO16/17. Não é necessário `SoftwareSerial`.

A pinagem de todo o projeto está centralizada em `firmware/include/pins.h`
e documentada em [`docs/hardware/README.md`](../hardware/README.md). O pino
`YRM100_EN_PIN` (GPIO14) controla a energia do módulo UHF no deep sleep —
desligue a `#define` se a sua placa YRM100 não tiver pino de habilitação.

O protocolo GATT (UUIDs, características e comandos) é definido em
`firmware/include/ble_profile.h` e descrito em [`docs/protocol/gatt.md`](../protocol/gatt.md).

Ambientes
---------

O produto é único (TRK-Finder); os ambientes variam apenas pelo modo de leitura:

* `esp32radar` — build padrão com o YRM100 real;
* `esp32radar-sim` — mesmo código com `-DTRAKR_SIM`: gera leituras simuladas
  para testar o app sem o módulo UHF.

Gravando o Banco de Dados Local (LittleFS)
------------------------------------------

A grande sacada desta arquitetura é que as ferramentas ficam salvas no próprio chip do ESP32. Nós usamos o LittleFS para particionar a memória.

1. Dentro da pasta `firmware/`, crie uma pasta chamada `data/`.

2. Crie um arquivo `inventory.json` vazio (ou com ferramentas padrão) dentro de `data/`.

3. Conecte o ESP32 via USB-C.

4. No menu lateral do PlatformIO, clique em **Platform -> Upload Filesystem Image**.
   _(Isso envia o arquivo JSON para a memória particionada)._

Gravando o Código (Upload)
--------------------------

Com o FileSystem gravado, basta clicar em **Upload** no PlatformIO para compilar o código `.cpp` e enviar para o ESP32.

### Lógica de Deep Sleep

Para economizar bateria, o loop principal (`loop()`) raramente é executado por muito tempo. O código prepara o **botão físico** (GPIO 33) como `ext0 wake up` e entra em `esp_deep_sleep_start()`. Ele só acorda com a pressão do botão.

### Recursos Nativos (Sempre Inclusos no Produto Base)

O TRK-Finder conta com os seguintes recursos nativos integrados ao firmware e app sem necessidade de add-ons adicionais:

1. **Modo Radar e Localização por Proximidade:**
   * Disparado pelo comando `start_radar` no GATT Control (suporta ID da ferramenta ou EPC).
   * Varredura contínua via `TrakYrm100::collectReads()` medindo EPC + RSSI em dBm.
   * Feedback sonoro no buzzer ativo com cadência proporcional (1000 ms sem sinal → 100 ms sinal forte).
   * Feedback visual nativo através do display **OLED SSD1306 (Tactical HUD)**, exibindo retículo octogonal, proximidade percentual e status geral.

2. **Registro Local de Eventos (Ledger):**
   * Histórico persistido em LittleFS (`events.json`) para rastreabilidade offline.
   * Leitura sob demanda via characteristic *History* (array JSON com rotação circular de até 100 registros).

3. **Sincronização de Relógio via BLE (`set_clock`):**
   * Dispensa módulo RTC externo (DS3231).
   * O app Android envia `{"cmd":"set_clock","epoch_ms":<timestamp>}` no pareamento.
   * O firmware calcula o delta em relação ao `millis()`, persiste `clock_delta_ms` em `config.json` e gera timestamps absolutos UTC (`uint64_t`) nos eventos.

4. **Gerenciamento de Energia e Deep Sleep:**
   * Controle de corte de energia do YRM100 via pino `YRM100_EN_PIN` (GPIO14).
   * Entrada automática em sono profundo após 30 s de inatividade no estado `ESCUTA`.
   * Despertar imediato por botão físico (`ext0 wake up` no GPIO33).

5. **Atualização OTA sem Fio via BLE:**
   * Particionamento duplo de OTA no flash do ESP32.
   * Recebimento de chunks binários via characteristic `Ota` com validação de integridade antes do reboot.

6. **Notificação de Ausência (App Android):**
   * Implementada no app através do `AbsenceWatcher.kt`.
   * Monitora os relatórios de presença e gera notificações locais push caso uma ferramenta monitorada não responda no intervalo esperado.

7. **Histórico com rotação mensal (sem perdas):**
   * `events.json` mantém os 200 eventos mais recentes em RAM/Flash.
   * Ao exceder, o mais antigo é arquivado automaticamente em `/events_YYYYMM.json` (ou `events_boot.json` quando sem epoch).
   * Comandos BLE `get_history` e `list_archives` permitem leitura paginada por mês, evitando perda por limite de 100.

8. **PIN de acesso e sessão autenticada:**
   * SHA-256 do PIN em `config.json` (`pin_hash`, 64 hex).
   * Comando `auth` abre sessão de 5 min para `add_tool`/`remove_tool` e troca de PIN via `set_config`.

### Modo radar (RASTREIA)

O núcleo do produto:

* **Botão físico** no GPIO 33 (pull-down interno) serve como wake `ext0`; o
  dispositivo acorda em `LEITURA` (varredura imediata) quando acordou pelo
  botão, senão entra em `ESCUTA` (espera ativa ~30 s → dorme).
* **Estado `RASTREIA` (modo radar):** disparado por
  `{"cmd":"start_radar","id":"<id>"}` (ou por tag) no GATT Control. A cada
  ciclo (~400 ms) o `TrakYrm100::collectReads()` mede o EPC + **RSSI (dBm)**
  da tag alvo e o firmware publica `{"type":"radar_report",...}` via Event
  notify — sem persistir no histórico. O buzzer bipa com intervalo
  proporcional à potência (1000 ms sem sinal → 100 ms com sinal forte) e o
  LED muda de cor (azul procurando → ciano sinal → verde perto).
* `TrakYrm100::collectEpc()` delega para `collectReads()` — o esquema de
  parsing é o mesmo, o RSSI é extraído do byte após o EPC quando presente no
  payload.

Detalhes do protocolo: [`docs/protocol/gatt.md`](../protocol/gatt.md).
Roadmap e próximos passos: [`docs/roadmap.md`](../roadmap.md).