Visão Geral da Arquitetura (Trakr)
=================================

O Trakr opera em uma arquitetura **Edge-Master / Thin-Client**, desenhada para ambientes offline. Isso significa que o microcontrolador do rastreador portátil (**TRK-Finder**) é o cérebro autônomo da operação, e o aplicativo Android atua como uma interface de visualização e configuração.

Diagrama do Sistema
-------------------

    ```mermaid
graph TD
    subgraph "Hardware (TRK-Finder Edge-Master)"
        TAG[Tags UHF Anti-Metal] <.. RF ..> ANT[Antena 2dBi]
        ANT <--> YRM[Leitor UHF YRM100]
        BTN[Botão Físico] --> |ext0 Wake| ESP
        PWR[Bateria 18650] --> ESP

        subgraph "ESP32 (Cérebro)"
            ESP_CORE[Máquina de Estados]
            MEM[(LittleFS: inventory.json)]
            ESP_CORE <--> MEM
        end

        YRM <-->|UART| ESP_CORE
        ESP_CORE --> BUZ[Alarme Local]
    end

    subgraph "Conectividade (BLE)"
        ESP_CORE <-->|GATT Server| BLE[Bluetooth Low Energy]
    end

    subgraph "Mobile (App Android Kotlin)"
        BLE <--> SVC[Foreground Service BLE]
        SVC <--> APP[App Jetpack Compose]
        APP <--> ROOM[(Room SQLite Cache)]
        SVC --> PUSH[Notificação Push Local]
    end

    classDef hardware fill:#2b2d42,stroke:#8d99ae,stroke-width:2px,color:#fff;
    classDef mobile fill:#023e8a,stroke:#0077b6,stroke-width:2px,color:#fff;

    class ESP_CORE,MEM,YRM,ANT,BTN,BUZ,TAG,PWR hardware;
    class APP,ROOM,PUSH,BLE,SVC mobile;
```

Fluxo de Operação Autônoma
--------------------------

1. **Gatilho Físico:** O botão do TRK-Finder é pressionado (ou o app envia um comando BLE).

2. **Despertar:** O ESP32 sai do _Deep Sleep_ (wake-up `ext0` pelo botão).

3. **Varredura (Sweep):** O YRM100 é energizado e lê todos os EPCs (IDs) das ferramentas em 500ms.

4. **Resolução Local:** O ESP32 cruza as leituras com seu `inventory.json`. Se faltar um ID, ele aciona o Buzzer interno instantaneamente.

5. **Notificação Remota:** Se houver um celular Android pareado por perto, o ESP32 envia o pacote de erro via BLE. O _Foreground Service_ do Kotlin intercepta e exibe a notificação no celular.

TRK-Finder (Rastreador Portátil)
--------------------------------

Produto único do ecossistema (ESP32 + YRM100 + BLE), com modos de uso:

1. **Modo Estação:** o rastreador fica parado no ambiente e executa auditorias
   do inventário, notificando no app quando faltam ferramentas (agendamento
   periódico planejado — Fase R2 do roadmap).

2. **Modo Radar (localização):** para a tag faltante, o operador caminha pelo
   ambiente com o dispositivo na mão; o YRM100 mede o **RSSI (dBm)** da tag e o
   firmware publica a potência via BLE e emite **bipes** com frequência
   proporcional ao sinal — o app exibe a intensidade em tempo real até a
   ferramenta ser encontrada.

Roadmap de Entregas e Features
------------------------------

O registro do que **já foi entregue** e das **próximas sugestões** (fases,
sempre offline) vive em [`roadmap.md`](./roadmap.md).

O **Catálogo de Melhorias** (add-ons opcionais de hardware e software, com
esforço/impacto/status por item) é o documento de discussão:
[`improvements.md`](./improvements.md).