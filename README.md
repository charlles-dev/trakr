# 🧰 Trakr: The Autonomous Smart Toolbox

<div align="center">
  <!-- Insira o caminho para a logo vetorizada aqui -->
  <img src="https://repository-images.githubusercontent.com/1327015653/0a6bcd2f-e1b1-449b-9da8-ee9157fb2588" alt="Trakr Logo" width="400">
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

O **Trakr** não é apenas uma caixa com Bluetooth; é uma solução completa de IoT focada em **computação na borda (Edge Computing)**. Projetado para suportar as demandas de controle e gestão de ativos em ambientes pesados e desconectados, como canteiros de obras e zonas de manutenção industrial.

Ao contrário de sistemas convencionais dependentes de nuvem, **a maleta Trakr é a própria fonte da verdade (Master)**. O inventário de ferramentas reside na memória interna do microcontrolador e é escaneado instantaneamente via tecnologia **UHF RFID**. O aplicativo móvel atua como um visualizador de alta performance, garantindo operação 100% autônoma e offline.

## ✨ Principais Funcionalidades

* 🧠 **Edge Intelligence (Autonomia Total):** O ESP32 armazena o inventário (`inventory.json` via LittleFS). Se uma ferramenta faltar ao fechar a tampa, a maleta processa a falha e dispara alarmes (Buzzer/LED) imediatamente, sem precisar do celular.
* 📡 **Varredura UHF RFID em Massa:** Utiliza o módulo YRM100 com antena cerâmica para ler o interior da maleta de uma só vez, captando múltiplas tags flexíveis *anti-metal* simultaneamente.
* 🔋 **Ultra Low-Power:** Deep Sleep com wake-up via Sensor Hall (despertar apenas por mudança magnética da tampa), alimentado por bateria 18650 carregada via TP4056 (USB-C).
* 📱 **App Offline-First (Thin Client):** Aplicativo Kotlin com interface *tech-oriented* em *Dark Mode*. Ele se conecta via Bluetooth LE (BLE), espelha o banco de dados da maleta no cache local do celular (Room/SQLite) e emite notificações push locais (ex: *"Ferramenta retirada: Chave Phillips 1/4"*).
* ⚙️ **Design Mecânico Paramétrico:** Gerado via script nativo no Autodesk Fusion 360. A estrutura inclui suportes para insertos térmicos de latão (M3), *cable management* integrado, guias de luz (*Light Pipe*) para o LED de status interno e cutouts para pés em TPU.

## 🏗️ Arquitetura do Sistema

O fluxo de dados foi desenhado para resiliência e privacidade:

1. **Hardware (Maleta):** Sensor Hall detecta a abertura/fechamento -> ESP32 desperta do *Deep Sleep* -> Módulo YRM100 varre as tags RFID -> ESP32 compara a leitura com seu banco Flash interno (LittleFS).
2. **Conectividade (BLE):** ESP32 transmite o status e o array de IDs via Bluetooth GATT (baixo consumo) e recebe novos cadastros de tags do app.
3. **Mobile (App):** O app em Kotlin recebe a carga via BLE, cruza com seu cache (Room/SQLite) e apresenta a interface para o usuário, permitindo também cadastrar novas tags, que são enviadas de volta para o ESP32.

> 📌 Um diagrama Mermaid detalhado do fluxo (Hardware -> BLE -> App) está disponível em [`docs/README.md`](./docs/README.md).

## 🛠️ Stack Tecnológica

| Camada      | Tecnologia                                          |
| ----------- | --------------------------------------------------- |
| **Firmware** | C++ (PlatformIO) · ESP32-WROOM-32 · NimBLE-Arduino · ArduinoJson |
| **RFID**    | Módulo YRM100 (UHF) + Antena cerâmica IPEX 2dBi + Tags Anti-Metal |
| **Mobile**  | Kotlin · Jetpack Compose · Room (SQLite) · Foreground Service BLE |
| **CAD**     | Autodesk Fusion 360 (script paramétrico em Python)  |
| **Docs**    | Markdown + Mermaid                                  |

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
* [Esquemático de Wiring (SVG)](./hardware/schematics/wiring.svg) — diagrama de fiação da maleta.
* [Datasheets dos componentes](./hardware/datasheets/README.md) — links verificados para cada módulo.
* [Checklist de Validação do CAD](./docs/cad/validation.md) — o que falta para validar e imprimir.

## 🚀 Como Começar

Para dar os primeiros passos com o ecossistema Trakr, escolha por onde deseja começar:

1. **Se você quer imprimir a maleta:** Siga o [Guia de CAD e Impressão 3D](./docs/cad/README.md).
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

- [x] Estrutura inicial do monorepo (firmware, app, hardware, CAD)
- [x] Migração do app para Kotlin + Jetpack Compose + Room
- [x] BLE end-to-end: inventário, alertas e sincronização via GATT
- [x] Cadastro direto de novas tags no app (add/remove via GATT Control)
- [x] Múltiplos perfis de inventário (por canteiro de obra) e seleção no app
- [x] Histórico de eventos (aberturas, tool_missing/tool_back, boot) com export CSV
- [ ] Medição de consumo energético em deep sleep (varredura com EN + UART off)
- [ ] Envio opcional de relatórios para nuvem (pós-opcional)
- [ ] Suporte a múltiplas maletas simultâneas (BLE multi-device)

## 🤝 Contribuição

O Trakr é construído por e para engenheiros, makers e desenvolvedores de soluções offline. Aceitamos *Pull Requests* para melhorias no firmware, novas telas no app, refatoração de código e otimizações de usabilidade na maleta 3D.

Veja nosso arquivo [CONTRIBUTING.md](./CONTRIBUTING.md) para diretrizes de desenvolvimento.

## 📄 Licença

Distribuído sob a licença MIT — livre para usar, modificar e distribuir com atribuição.
Consulte o arquivo [LICENSE](./LICENSE) para mais informações.