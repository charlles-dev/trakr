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