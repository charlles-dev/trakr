Lista de Materiais (BoM) e Eletrônica
=====================================

Os componentes foram escolhidos pelo custo-benefício (Mercado Livre e AliExpress).

| **Componente**        | **Especificação**                  | **Qtd** | **Função**                                    |
| --------------------- | ---------------------------------- | ------- | --------------------------------------------- |
| **Módulo RFID**       | YRM100 (UHF Mini)                  | 1       | Motor de leitura de rádio frequência (RF).    |
| **Antena**            | Cerâmica IPEX (2dBi)               | 1       | Irradiação do sinal dentro da maleta.         |
| **Microcontrolador**  | ESP32-WROOM-32 (30 pinos)          | 1       | Processamento de dados e comunicação BLE.     |
| **Gestão de Energia** | Módulo TP4056 (USB-C)              | 1       | Carregamento da bateria de lítio.             |
| **Bateria**           | 18650 Li-ion (ex: Samsung 2600mAh) | 1       | Alimentação autônoma de longo prazo.          |
| **Sensor de Maleta**  | Módulo Sensor Hall (A3144)         | 1       | Detecta a aproximação do ímã (tampa fechada). |
| **Ímã**               | Neodímio (Pequeno, circular)       | 1       | Fica na tampa, ativa o Sensor Hall na base.   |
| **Feedback Visual**   | LED RGB WS2812B (ou comum)         | 1       | Ilumina o _Light Pipe_ externo.               |
| **Feedback Sonoro**   | Buzzer Ativo (5V/3.3V)             | 1       | Alarme de ferramentas perdidas.               |
| **Tags RFID**         | Adesivo Flexível UHF _Anti-Metal_  | 10+     | Identificação única colada nas ferramentas.   |

> **Atenção:** As tags DEVEM ser do tipo "Anti-Metal" (possuem uma malha isolante atrás). Tags UHF comuns param de funcionar se coladas diretamente no aço de chaves e alicates.





Guia de Conexões e Pinagem (Wiring)
===================================

Siga este esquema para conectar os módulos ao ESP32. É altamente recomendado utilizar fios de silicone finos (AWG 28) para facilitar o roteamento dentro do canal (_cable management_) impresso na maleta.
Tabela de Ligações (Pinout)
---------------------------

### YRM100 (Módulo UHF) -> ESP32

* **VCC:** 3.3V / 5V (Verifique a especificação exata da sua placa YRM100)

* **GND:** GND

* **TX:** Pino GPIO 16 (RX2 do ESP32)

* **RX:** Pino GPIO 17 (TX2 do ESP32)

* **EN (se disponível):** Pino GPIO 14 — usado pelo firmware (`YRM100_EN_PIN`) para energizar/desenergizar o módulo no deep sleep. **Confirmar no datasheet da sua placa**; se a sua não tiver pino EN/PE, remova a definição de `YRM100_EN_PIN` em `firmware/include/pins.h` (o firmware desliga apenas o UART, reduzindo parte do consumo).

* _(Nota: O YRM100 se comunica via UART. O ESP32 envia comandos em HEX para iniciar a leitura)._

### Sensor Hall (A3144) -> ESP32

* **VCC:** 3.3V

* **GND:** GND

* **OUT (Sinal):** Pino GPIO 33 (Este pino suporta RTC/Wake-up no modo Deep Sleep).

### Alerta (Buzzer & LED)

* **Buzzer (+):** Pino GPIO 25

* **LED (Data):** Pino GPIO 26 — LED RGB endereçável **WS2812B** (o firmware usa FastLED com o padrão GRB no pino `LED_RGB_PIN`). Se optar por um LED comum, ajuste `TrakLed` em `firmware/lib/TrakLed/`.

### Energia (TP4056 & Bateria 18650)

* **Bateria Positivo (+):** Pino B+ do TP4056

* **Bateria Negativo (-):** Pino B- do TP4056

* **TP4056 OUT (+):** Pino VIN / 5V do ESP32

* **TP4056 OUT (-):** GND do ESP32

Consumo e Autonomia
-------------------

Para medir o consumo de cada estado (deep sleep, varredura, BLE) e estimar a
autonomia da 18650, siga o [Protocolo de Medição de Consumo](power-measurement.md).
