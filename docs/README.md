# 🏗️ Visão Geral da Arquitetura (Trakr)

O Trakr opera em uma arquitetura **Edge-Master / Thin-Client**, desenhada especificamente para ambientes industriais e remotos 100% offline. O microcontrolador do rastreador portátil (**TRK-Finder**) é o cérebro autônomo da operação e detentor da fonte da verdade, enquanto o aplicativo Android atua como central de visualização, auditoria, localização guiada e configuração.

---

## 📡 Diagrama do Sistema Completo

```mermaid
graph TD
    subgraph "Hardware (TRK-Finder Edge-Master)"
        TAG[Tags UHF Anti-Metal] <.. RF UHF ..> ANT[Antena Cerâmica / SMA]
        ANT <--> YRM[Leitor UHF YRM100]
        BTN[Botão Físico] --> |ext0 Wake| ESP
        PWR[Bateria 18650] --> INA[Sensor INA219] --> ESP
        SENS[BME280 & MPU6050] --> |I2C| ESP

        subgraph "ESP32 (Cérebro Autônomo)"
            ESP_CORE[Máquina de Estados FSM]
            MEM[(LittleFS: inventory.json & events.json)]
            ESP_CORE <--> MEM
        end

        YRM <-->|UART2| ESP_CORE
        ESP_CORE --> HAPT[Buzzer + Motor Vibratório]
        ESP_CORE --> OLED[OLED SSD1306 Tactical HUD]
    end

    subgraph "Conectividade (BLE GATT MTU 512)"
        ESP_CORE <-->|Inventory / Event / Control / History / Ota| BLE[Bluetooth Low Energy]
    end

    subgraph "Mobile (App Android Kotlin + Compose)"
        BLE <--> SVC[Foreground Service BLE]
        SVC <--> APP[Interface Jetpack Compose]
        APP <--> ROOM[(Room SQLite Database)]
        APP --> NFC[Módulo Pareamento NFC]
        APP --> MOT[Alerta de Trânsito GPS >15km/h]
        SVC --> PUSH[Notificações Push Locais]
    end

    classDef hardware fill:#2b2d42,stroke:#8d99ae,stroke-width:2px,color:#fff;
    classDef mobile fill:#023e8a,stroke:#0077b6,stroke-width:2px,color:#fff;

    class ESP_CORE,MEM,YRM,ANT,BTN,HAPT,TAG,PWR,INA,SENS,OLED hardware;
    class APP,ROOM,PUSH,BLE,SVC,NFC,MOT mobile;
```

---

## ⚙️ Fluxo de Operação Autônoma

1. **Gatilho Físico / Detecção:** O operador pressiona o botão físico no TRK-Finder (ou aciona o comando pelo app, ou o acelerômetro detecta movimento).
2. **Despertar Instantâneo:** O ESP32 sai do *Deep Sleep* (`ext0 wake-up` em menos de 15ms) e energiza o módulo YRM100.
3. **Varredura UHF:** Em ~500ms, o módulo YRM100 lê simultaneamente todos os EPCs das tags anti-metal no raio de alcance.
4. **Resolução Edge-Master:** O microcontrolador cruza os EPCs lidos com seu banco local `inventory.json` na memória Flash (LittleFS).
5. **Feedback Imediato:**
   * Caso falte alguma ferramenta cadastrada: dispara bipes no buzzer, acende LED de alerta e exibe o nome da peça faltante no display OLED.
   * Se um smartphone com o app Trakr estiver por perto, o alerta é transmitido via BLE e o *Foreground Service* emite notificação push local.

---

## 🎯 Modos de Operação do TRK-Finder

* **Auditoria Instantânea (Varredura):** Auditoria rápida de inventário com 1 clique no botão, espelhando status no app e registrando no histórico.
* **Modo Radar de Localização (Single-Target):** Procura uma ferramenta específica medindo a potência do sinal (**RSSI em dBm**), guiando o operador por cadência de bipes, vibração háptica no celular, LED de proximidade e retículo de mira direcional no display OLED.
* **Modo Radar Multi-Alvo:** Rastreamento simultâneo de múltiplos itens com ranking de proximidade em tempo real.
* **Live Streaming:** Transmissão contínua de todas as tags captadas para conferência dinâmica de estoque ou bancada.
* **Find My Finder:** Aciona LED pulsante e alarme sonoro contínuo no rastreador para encontrá-lo dentro de caixas ou veículos.