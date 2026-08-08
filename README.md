# Trakr

> A maleta de ferramentas inteligente que sabe o que contém — e avisa quando algo sai sem permissão.

![status](https://img.shields.io/badge/status-Em%20Desenvolvimento-yellow)

## Visão geral

O **Trakr** é um monorepo para uma maleta de ferramentas inteligente com detecção de presença via RFID/NFC. Quando uma ferramenta é retirada, a maleta — e seu celular — ficam sabendo na hora.

| Módulo           | Descrição                                              | Stack                     |
| ---------------- | ------------------------------------------------------ | ------------------------- |
| [`app/`](app/)   | O Visualizador: dashboard, lista de ferramentas e alertas         | Flutter (BLE)             |
| [`firmware/`](firmware/) | O Cérebro da Maleta: leitor RFID, Bluetooth e máquina de estados  | ESP32 + PlatformIO        |
| [`hardware/`](hardware/) | BOM, datasheets e esquemas elétricos                            | KiCad / Fritzing          |
| [`cad/`](cad/)          | Design 3D paramétrico da carcaça                           | Fusion 360 (Python)       |
| [`docs/`](docs/)        | Guias de compilação, gravação, soldagem e impressão              | Markdown                  |

## Como funciona

```
   ESP32 (fonte da verdade)
        │  inventário + eventos BLE
        ▼
   Maleta  ◄─── RFID: ferramenta retirada
        │
        ▼
   App Flutter (visualizador + cache local)
```

- O firmware conhece o inventário real (fonte da verdade) e detecta retiradas/devoluções.
- O app conecta via BLE, visualiza o estado e dispara notificações locais.

## Estrutura

```
trakr/
├── app/            # App Flutter (BLE + notificações locais + cache Isar)
├── firmware/       # Firmware ESP32 (PlatformIO, máquina de estados)
├── hardware/       # BOM, datasheets, schematics
├── cad/            # Design 3D paramétrico + exports
├── docs/           # Guias de sobrevivência por módulo
└── README.md
```

## Primeiros passos

1. Aplicativo: veja [`docs/app/`](docs/app/README.md).
2. Firmware: veja [`docs/firmware/`](docs/firmware/README.md).
3. Hardware: veja [`docs/hardware/`](docs/hardware/README.md).
4. CAD: veja [`docs/cad/`](docs/cad/README.md).

## Licença

[MIT](LICENSE)

---
*Feito com 🔧 (e alguns ímãs).*