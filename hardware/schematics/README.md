# 🔌 Esquemáticos

Este diretório contém os diagramas de referência de hardware do Trakr.

## Conteúdo

* **[`wiring.svg`](./wiring.svg)** — Diagrama de fiação completo do TRK-Finder, gerado a partir de `firmware/include/pins.h` e do guia de wiring (`docs/hardware/README.md`).

> ⚠️ **Status:** os esquemáticos completos em formato EDA (ex: KiCad `.kicad_sch`) ainda não foram gerados. O projeto funciona atualmente com módulos de breakout (YRM100, TP4056, etc.), sem PCB customizado. O `wiring.svg` serve como referência de montagem para o canteiro de obras.

## Como abrir

* Renderização direta no GitHub (basta clicar no arquivo).
* Abra em qualquer navegador ou editor de SVG (Inkscape, draw.io).
* Qualquer alteração **deve** ser feita em ambos: este diagrama e os pinos em `firmware/include/pins.h`.

## Datasheets

Consulte [`hardware/datasheets/README.md`](../datasheets/README.md) para a lista de datasheets de cada componente.