Lista de Materiais (BoM) e Eletrônica
=====================================

Os componentes foram escolhidos pelo custo-benefício (Mercado Livre e AliExpress).

| **Componente**        | **Especificação**                  | **Qtd** | **Função**                                    |
| --------------------- | ---------------------------------- | ------- | --------------------------------------------- |
| **Módulo RFID**       | YRM100 (UHF Mini)                  | 1       | Motor de leitura de rádio frequência (RF).    |
| **Antena**            | Cerâmica IPEX (2dBi)               | 1       | Irradiação do sinal do rastreador.            |
| **Microcontrolador**  | ESP32-WROOM-32 (30 pinos)          | 1       | Processamento de dados e comunicação BLE.     |
| **Display**           | OLED 1.3" ou 0.96" I2C (SSD1306)   | 1       | Tactical HUD (mostra radar e inventário).     |
| **Sensores**          | MPU6050 + BME280 (I2C)             | 1 de cd | Detecção de movimento (Wake) e clima.         |
| **Gestão de Energia** | Módulo TP4056 (USB-C)              | 1       | Carregamento da bateria de lítio.             |
| **Bateria**           | 18650 Li-ion (ex: Samsung 2600mAh) | 1       | Alimentação autônoma de longo prazo.          |
| **Botão Físico**      | Push button (táctil, 12mm)         | 1       | Wake up (ext0) e disparo de varredura.        |
| **Feedback Visual**   | LED RGB WS2812B (ou comum)         | 1       | Ilumina o _Light Pipe_ externo (status).      |
| **Feedback Sonoro**   | Buzzer Ativo/Passivo (3.3V)        | 1       | Bipes de guia do modo radar e alertas.        |
| **Tags RFID**         | Adesivo Flexível UHF _Anti-Metal_  | 10+     | Identificação única colada nas ferramentas.   |

> **Atenção:** As tags DEVEM ser do tipo "Anti-Metal" (possuem uma malha isolante atrás). Tags UHF comuns param de funcionar se coladas diretamente no aço de chaves e alicates.

---

Links e Referências de Compra (Brasil)
======================================

Para facilitar a montagem, aqui estão as opções mais baratas e acessíveis para importar ou comprar os itens principais aqui no Brasil (via AliExpress, Mercado Livre e Shopee). 

> **Dica:** Ao comprar no AliExpress, sempre procure os anúncios marcados como "Choice" para ter frete grátis em compras acima de R$99.

| **Item** | **Preço Médio (BRL)** | **Onde Comprar (Busca Rápida)** |
| -------- | --------------------- | ------------------------------- |
| **Módulo RFID UHF YRM100** | R$ 120 - 180 | [Buscar no AliExpress](https://pt.aliexpress.com/w/wholesale-uhf-rfid-module-yrm100.html) <br> [Buscar no Mercado Livre](https://lista.mercadolivre.com.br/modulo-rfid-uhf) |
| **Antena Cerâmica IPEX (2dBi)** | R$ 25 - 40 | [Buscar no AliExpress](https://pt.aliexpress.com/w/wholesale-uhf-rfid-ceramic-antenna-ipex-2dbi.html) <br> *(Verifique se o YRM100 escolhido já não inclui a antena!)* |
| **Placa ESP32 (30 pinos)** | R$ 30 - 45 | [Buscar no Shopee](https://shopee.com.br/search?keyword=esp32%2030%20pinos) <br> [Buscar no Mercado Livre](https://lista.mercadolivre.com.br/esp32-30-pinos) |
| **Display OLED I2C** | R$ 15 - 25 | [Buscar no Shopee](https://shopee.com.br/search?keyword=display%20oled%20i2c%200.96) <br> [Buscar no Mercado Livre](https://lista.mercadolivre.com.br/display-oled-i2c) |
| **BME280 + MPU6050** | R$ 20 - 40 | [Buscar no AliExpress](https://pt.aliexpress.com/w/wholesale-bme280-mpu6050.html) <br> *(Procure comprar os dois na mesma loja para economizar frete)* |
| **Botão, LED e Buzzer** | R$ 10 - 15 (Kits) | Compre kits de Arduino básicos no ML ou em lojas de eletrônica locais. |
| **Tags UHF Anti-Metal** | R$ 3 - 8 (un.) | [Buscar no AliExpress](https://pt.aliexpress.com/w/wholesale-uhf-rfid-anti-metal-tag-flexible.html) <br> [Buscar no Mercado Livre](https://lista.mercadolivre.com.br/tag-rfid-uhf-anti-metal) |
| **Módulo TP4056 (USB-C)** | R$ 5 - 10 | [Buscar no Shopee](https://shopee.com.br/search?keyword=tp4056%20usb%20c) <br> [Buscar no Mercado Livre](https://lista.mercadolivre.com.br/tp4056-usb-c) |
| **Bateria 18650** | R$ 15 - 30 | Compre em lojas de eletrônica locais ou vape shops para evitar baterias falsificadas de "10.000 mAh" na internet. |

---

Guia de Conexões e Pinagem (Wiring)
===================================

Siga este esquema para conectar os módulos ao ESP32. É altamente recomendado utilizar fios de silicone finos (AWG 28) para facilitar o roteamento dentro do canal (_cable management_) impresso na carcaça.

Tabela de Ligações (Pinout)
---------------------------

### YRM100 (Módulo UHF) -> ESP32
* **VCC:** 3.3V / 5V (Verifique a especificação exata da sua placa YRM100)
* **GND:** GND
* **TX:** Pino GPIO 16 (RX2 do ESP32)
* **RX:** Pino GPIO 17 (TX2 do ESP32)
* **EN (se disponível):** Pino GPIO 14 (o firmware desliga o UART no deep sleep).

### Botão Físico -> ESP32
* **Terminal 1:** 3.3V
* **Terminal 2:** Pino GPIO 33 (Este pino suporta RTC/Wake-up no modo Deep Sleep, acionando o wake `ext0`).

### Display OLED e Sensores (Barramento I2C)
O Display OLED (SSD1306), o MPU6050 e o BME280 compartilham o mesmo barramento I2C:
* **VCC:** 3.3V
* **GND:** GND
* **SDA:** Pino GPIO 21
* **SCL:** Pino GPIO 22

### Alerta (Buzzer & LED)
* **Buzzer (+):** Pino GPIO 25
* **LED (Data):** Pino GPIO 26 (LED WS2812B usando FastLED, configurável em `TrakLed.h`).

### Energia (TP4056 & Bateria 18650)
* **Bateria Positivo (+):** Pino B+ do TP4056
* **Bateria Negativo (-):** Pino B- do TP4056
* **TP4056 OUT (+):** Pino VIN / 5V do ESP32
* **TP4056 OUT (-):** GND do ESP32

Consumo e Autonomia
-------------------

Para medir o consumo de cada estado (deep sleep, varredura, BLE) e estimar a autonomia da 18650, siga o [Protocolo de Medição de Consumo](power-measurement.md).
