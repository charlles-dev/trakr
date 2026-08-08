# Trakr Firmware - Guia de Sobrevivência

## Compilar e gravar (flash)

Pré-requisitos: [PlatformIO Core](https://platformio.org/install).

```bash
cd firmware
pio run -t upload --upload-port COM5   # grava o firmware no ESP32
pio run -t uploadfs                    # envia os arquivos do LittleFS
pio run -t monitor -b 115200          # serial
```

## LittleFS
O arquivo `data/inventory_template.json` é o inventário inicial gravado
no LittleFS. O firmware o carrega na inicialização.

## Roadmap
- [ ] Driver customizado para o YRM100 (UART + handshake)
- [ ] UUIDs BLE / máquina de estados (LEITURA -> MONITOR -> ALERTA)
- [ ] Eventos de retirada/devolução para o app