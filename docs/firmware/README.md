Guia de Firmware (ESP32 + LittleFS)
===================================

O firmware do Trakr é o cérebro autônomo da maleta. Ele foi escrito em C++ e é recomendado utilizar o **PlatformIO** (extensão do VS Code) para compilação.
Ambiente de Desenvolvimento
---------------------------

1. Instale o VS Code.

2. Instale a extensão PlatformIO IDE.

3. Abra a pasta `trakr/firmware/` no VS Code. O PlatformIO fará o download do SDK do ESP32 automaticamente.

Dependências (platformio.ini)
-----------------------------

* `bblanchon/ArduinoJson` (Para manipular o `inventory.json`)

* Biblioteca para BLE (inclusa no core do ESP32)

* SoftwareSerial (Para comunicação com o YRM100, se não usar UART de hardware)

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

Para economizar bateria, o loop principal (`loop()`) raramente é executado por muito tempo. O código prepara o pino do Sensor Hall como `ext0 wake up` e entra em `esp_deep_sleep_start()`. Ele só acorda com mudanças magnéticas (tampa abrindo/fechando).
