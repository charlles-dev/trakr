# Trakr Hardware - Guia de Sobrevivência

## Soldagem

- Leitor YRM100: alimentação 3.3V, UART (TX/RX) com divisor de tensão.
- TP4056: bateria LiPo no terminal B+/B-, saída 5V no OUT+.
- **Antena**: posicionar longe de parafusos e trilhas de cobre para não
  degradar o alcance de leitura RFID.

## Materiais de referência
- `datasheets/` — PDFs técnicos (YRM100, TP4056, ESP32)
- `schematics/` — esquemas elétricos (KiCad/Fritzing)

## BOM
Ver [`hardware/bom/bom.csv`](../bom/bom.csv).