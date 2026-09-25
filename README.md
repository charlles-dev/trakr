# 🧰 Trakr — Rastreamento de Ferramentas Offline-First

<div align="center">
  <!-- Insira o caminho para a logo vetorizada aqui -->
  <img src="https://repository-images.githubusercontent.com/1327015653/94b704f5-bd71-4273-ba49-e6ea99c3f789" alt="Trakr Logo" width="400">
</div>

<p align="center">
  <br>
  <b>Um ecossistema open-source de hardware e software para rastreamento e gestão de ferramentas, totalmente offline-first.</b>
</p>

<div align="center">

[![Status](https://img.shields.io/badge/Status-Em_Desenvolvimento-orange.svg)]()
[![Hardware](https://img.shields.io/badge/Hardware-ESP32_%26_UHF_RFID-2b2d42.svg)](./hardware)
[![CAD](https://img.shields.io/badge/CAD-Fusion_360_Parametric-red.svg)](./cad)
[![App](https://img.shields.io/badge/App-Kotlin_BLE-02569B.svg)](./app)
[![Firmware](https://img.shields.io/badge/Firmware-PlatformIO-yellow.svg)](./firmware)
[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](./LICENSE)

</div>

---

## 📑 Índice

- [🎯 O que é o Trakr?](#-o-que-é-o-trakr)
- [✨ Principais Funcionalidades](#-principais-funcionalidades)
- [🏗️ Arquitetura do Sistema](#️-arquitetura-do-sistema)
- [🛠️ Stack Tecnológica](#️-stack-tecnológica)
- [📂 Estrutura do Repositório (Monorepo)](#-estrutura-do-repositório-monorepo)
- [🚀 Como Começar](#-como-começar)
- [🔄 Roadmap](#-roadmap)
- [🤝 Contribuição](#-contribuição)
- [📄 Licença](#-licença)

---

## 🎯 O que é o Trakr?

O **Trakr** não é apenas um leitor com Bluetooth; é uma solução completa de IoT focada em **computação na borda (Edge Computing)**. Projetado para suportar as demandas de controle e gestão de ativos em ambientes pesados e desconectados, como canteiros de obras e zonas de manutenção industrial.

Ao contrário de sistemas convencionais dependentes de nuvem, **o rastreador TRK-Finder é a própria fonte da verdade (Master)**. O inventário de ferramentas reside na memória interna do microcontrolador e é escaneado instantaneamente via tecnologia **UHF RFID**. O aplicativo móvel atua como um visualizador de alta performance, garantindo operação 100% autônoma e offline.

O **TRK-Finder** é um rastreador portátil UHF (ESP32 + YRM100) que pode ser deixado *estacionado* no ambiente de trabalho para auditoria contínua das ferramentas, ou usado em *modo radar*: quando uma ferramenta falta, ele varre sua tag e mede a potência do sinal (**RSSI**), guiando o operador por bipes até a posição da peça — tudo gerenciado pelo mesmo app offline-first.

## ✨ Principais Funcionalidades

* 🧠 **Edge Intelligence (Autonomia Total):** O ESP32 armazena o inventário (`inventory.json` via LittleFS). Cada varredura (botão físico ou comando do app) é resolvida localmente, sem precisar do celular ou de nuvem.
* 📡 **Varredura UHF RFID em Lote:** Módulo YRM100 com antena cerâmica/SMA para ler dezenas de tags flexíveis *anti-metal* simultaneamente em ~500ms.
* 🎯 **Radar com Display (Tactical HUD):** O próprio rastreador possui display **OLED SSD1306** com retículo de mira octogonal, setas-guia de aproximação e porcentagem de sinal em tempo real, permitindo localizar ferramentas no escuro ou no campo guiado apenas pela tela e bipes do aparelho.
* 🔍 **Modo Radar Multimodo:** Modos *Single-Target*, *Multi-Alvo* e *Live Stream*, com cadência sonora no buzzer, LED RGB de proximidade e vibração háptica no celular.
* 🚗 **Alerta de Deslocamento em Trânsito:** O app monitora deslocamentos veiculares via GPS (> 15 km/h) e emite alarme crítico imediato se ferramentas forem esquecidas para trás.
* 🏷️ **Gravação e Pareamento NFC/UHF:** Pareamento BLE por toque via NFC e capacidade de reprogramar novos códigos EPC diretamente nas tags físicas.
* 🌡️ **Sensoriamento & Diagnóstico:** Telemetria de bateria real (INA219), temperatura/umidade/pressão (BME280) e acelerômetro (MPU6050).
* 🔄 **Atualização OTA sem Fio:** Upload de novos firmwares `.bin` via Bluetooth LE com particionamento duplo e healthcheck com rollback automático.
* 🔋 **Ultra Low-Power:** Deep Sleep com consumo em microamperes e despertar instantâneo via botão físico (`ext0 wake-up`).

## 🏗️ Arquitetura do Sistema

O fluxo de dados foi desenhado para resiliência e privacidade:

1. **Hardware (TRK-Finder):** Botão físico (ou comando BLE) dispara a varredura -> ESP32 desperta do *Deep Sleep* -> Módulo YRM100 varre as tags RFID -> ESP32 compara a leitura com seu banco Flash interno (LittleFS).
2. **Conectividade (BLE GATT MTU 512):** ESP32 transmite inventário, eventos, relatórios de radar e telemetria de sensores via características dedicadas.
3. **Mobile (App Android):** O app em Kotlin com *Foreground Service* espelha o banco no Room/SQLite local, gerencia Job Kits e emite notificações de ausência.
4. **Modos de Busca:** Single, Multi-alvo e Live stream com cálculo de delta direcional e alertas de trânsito em alta velocidade.

> 📌 Um diagrama Mermaid detalhado do fluxo (Hardware -> BLE -> App) está disponível em [`docs/README.md`](./docs/README.md).

## 🛠️ Stack Tecnológica

| Camada      | Tecnologia                                          |
| ----------- | --------------------------------------------------- |
| **Firmware** | C++ (PlatformIO) · ESP32-WROOM-32 / ESP32-S3 · NimBLE-Arduino · ArduinoJson · LittleFS · FreeRTOS |
| **RFID UHF**| Módulo YRM100 (UHF 865–928 MHz) + Antena cerâmica IPEX 2dBi / SMA + Tags Anti-Metal + medição de RSSI (dBm) + Escrita EPC |
| **Mobile**  | Kotlin · Jetpack Compose · Room (SQLite) · Foreground Service BLE · GPS Movement Detection · PDF Box Generator · NFC |
| **Sensores**| INA219 (Bateria I2C) · BME280 (Temp/Hum/Press) · MPU6050 (IMU) · SSD1306 OLED (Tactical HUD) · Buzzer Passivo |
| **CAD**     | Autodesk Fusion 360 (script paramétrico em Python) · Snap-fit · IP54 Gasket · Bumper TPU |
| **Web Setup**| Astro · TailwindCSS · Web Serial API · ESP Web Tools (`landing/`) |

## 📂 Estrutura do Repositório (Monorepo)

O projeto é dividido em 4 domínios principais mais documentação. Veja o diretório `docs/` para guias detalhados de cada um:

```
trakr/
├── app/          # Aplicativo Android (Kotlin + Jetpack Compose)
├── firmware/     # Código C++ do ESP32 (PlatformIO), LittleFS e lógica de estados
│   ├── data/     # Banco de dados local (inventory.json)
│   ├── include/  # pinagem centralizada (pins.h)
│   └── lib/      # Bibliotecas internas (TrakBle, TrakInventory, TrakYrm100)
├── hardware/     # Esquemáticos eletrônicos, pinagem e BoM
│   ├── bom/      # Lista de materiais
│   ├── schematics/ # Circuitos e documentação de conexão (wiring.svg)
│   └── datasheets/ # Datasheets dos componentes
├── cad/          # Scripts do Fusion 360, peças 3D e exports (.3mf)
└── docs/         # Documentação completa e diagramas de arquitetura
    └── protocol/ # Protocolo BLE/GATT (gatt.md)
```

## 📡 Documentação Técnica

* [Protocolo BLE/GATT](./docs/protocol/gatt.md) — UUIDs, payloads e comandos (interação firmware ↔ app).
* [Catálogo de Melhorias (HW + SW)](./docs/improvements.md) — add-ons opcionais em discussão, com IDs, esforço e impacto.
* [Esquemático de Wiring (SVG)](./hardware/schematics/wiring.svg) — diagrama de fiação do TRK-Finder.
* [Datasheets dos componentes](./hardware/datasheets/README.md) — links verificados para cada módulo.
* [Protocolo de Medição de Consumo](./docs/hardware/power-measurement.md) — como medir a autonomia da bateria.
* [Checklist de Validação do CAD](./docs/cad/validation.md) — o que falta para validar e imprimir.

## 🚀 Como Começar

Para dar os primeiros passos com o ecossistema Trakr, escolha por onde deseja começar:

1. **Se você quer imprimir o TRK-Finder:** Siga o [Guia de CAD e Impressão 3D](./docs/cad/README.md).
2. **Se você quer montar a eletrônica:** Consulte a [Lista de Materiais (BoM), pinagem e esquemáticos](./docs/hardware/README.md).
3. **Se você quer focar no código:** Veja o [Guia do Firmware ESP32](./docs/firmware/README.md) ou a [Configuração do App Kotlin](./docs/app/README.md).

### Pré-requisitos

| Área            | Ferramentas necessárias                                                                    |
| --------------- | ----------------------------------------------------------------------------------------- |
| **Firmware**    | VS Code + [PlatformIO IDE](https://platformio.org/install/ide?install=vscode) |
| **App**         | Android Studio (Android SDK 21+), Kotlin                                                  |
| **CAD**         | Autodesk Fusion 360 (pessoal ou educativo)                                                |
| **Hardware**    | Impressora 3D (FDM), ferro de solda e componentes listados no [BoM](./docs/hardware/README.md) |

## 🔄 Roadmap

O inventário completo está em [`docs/roadmap.md`](./docs/roadmap.md): lá você
encontra o **histórico de entregas concluídas** (monorepo, BLE end-to-end,
deep sleep + protocolo de consumo, refatoração radar-only, TRK-Finder como
único produto), as **próximas features sugeridas** em fases e os **add-ons
opcionais** — sempre 100% offline-first, sem nuvem.

## 🤝 Contribuição

O Trakr é construído por e para engenheiros, makers e desenvolvedores de soluções offline. Aceitamos *Pull Requests* para melhorias no firmware, novas telas no app, refatoração de código e otimizações de usabilidade na carcaça 3D.

Veja nosso arquivo [CONTRIBUTING.md](./CONTRIBUTING.md) para diretrizes de desenvolvimento.

## 📄 Licença

Distribuído sob a licença MIT — livre para usar, modificar e distribuir com atribuição.
Consulte o arquivo [LICENSE](./LICENSE) para mais informações.