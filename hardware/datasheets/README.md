# 📄 Datasheets do Trakr

Este diretório guarda as folhas de dados (datasheets) dos componentes usados no projeto.

> ⚠️ **Importante:** se a sua placa/versão do módulo for diferente, **valide o protocolo antes de alterar o firmware** — variantes do YRM100 podem usar numeração de comandos diferente.

## Lista de componentes e datasheets

| Componente | Datasheet (link oficial/verificado) | Arquivo local recomendado |
| --- | --- | --- |
| ESP32-WROOM-32 | [Espressif — pdf](https://www.espressif.com/sites/default/files/documentation/esp32-wroom-32_datasheet_en.pdf) | `esp32-wroom-32_datasheet_en.pdf` |
| YRM100 (UHF) | [M5Stack Docs — UHF RFID unit](https://docs.m5stack.com/en/unit/uhf_rfid) (usa o mesmo módulo) | `yrm100_manual.pdf` |
| Sensor Hall A3144 | [Allegro 3141-3144 series — pdf](https://docs.rs-online.com/fb94/0900766b8002bac6.pdf) | `a3144_datasheet.pdf` |
| TP4056 | [Datasheet TP4056 (LCSC mirror)](https://datasheet.lcsc.com/Icsc/1809261820_TOPPOWER-Nanjing-Extension-Microelectronics-TP4056-42-ESOP8_C16581.pdf) | `tp4056_datasheet.pdf` |
| WS2812B | [WorldSemi — V5 data](http://world-semi.com/web/userfiles/productfile/WS2812B_V5WDatasheet_V6.1_EN.pdf) | `ws2812b_datasheet.pdf` |

> Estimativa: baixe os PDFs manualmente e coloque nesta pasta com o nome indicado. Isso garante disponibilidade offline (recomendado para uso em canteiro).

## Como baixar automaticamente (PowerShell)

``` powershell
# Exemplo de script que baixa o datasheet do ESP32
$url = "https://www.espressif.com/sites/default/files/documentation/esp32-wroom-32_datasheet_en.pdf"
Invoke-WebRequest -Uri $url -OutFile "hardware/datasheets/esp32-wroom-32_datasheet_en.pdf"
```

## Notas

* Os links acima são os oficiais ou os mirrors mais estáveis (RS Online, LCSC) dos fabricantes.
* O datasheet **A3144** mencionado foi descontinuado pela Allegro — a documentação está em [341/3144 series docs](https://docs.rs-online.com/fb94/0900766b8002bac6.pdf).
* YRM100 não possui datasheet público oficial do fabricante; o protocolo UART usado no firmware (`lib/TrakYrm100/`) foi validado contra o protocolo M100 (MagicRF), comum a módulos da família.