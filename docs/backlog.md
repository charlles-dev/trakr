# 📌 Backlog TRK-Finder (Ideias Futuras e Validadas)

Este é o documento oficial de melhorias que são fisicamente possíveis, fazem sentido e foram aprovadas para o futuro do Trakr (TRK-Finder), livres de exageros e alucinações.

A arquitetura atual (Fase R1 a R3 + Add-ons) está **100% pronta**. As tarefas aqui descritas focam apenas em maturidade corporativa e polimento extremo do produto.

---

## 💻 1. Firmware (C++ / ESP32)
* **[FW-01] Mover Buffers para a PSRAM:** Atualmente o `TrakInventory` armazena e desserializa o JSON na RAM (SRAM) padrão. Usar a PSRAM do ESP32-S3 para suportar o rastreamento de >1.000 ferramentas simultâneas sem crash.
* **[FW-02] Watchdog Timer Focado (WDT):** Atualmente usamos WDT global de 30s. Podemos isolar tasks (ex: Leitura UART) num core separado com um WDT específico de 3s para lidar com travamentos do módulo UHF.
* **[FW-03] Compressão ZLib/LZ4:** Compactar as mensagens `events.json` antes de enviá-las via BLE ao Android (o MTU de 512 bytes agradece).
* **[FW-04] Criptografia de Arquivos:** Usar encriptação AES-GCM (padrão MbedTLS no ESP32) para encriptar o `inventory.json` e `config.json` e proteger dados corporativos de extração física da memória Flash.

## 📱 2. App Android (Kotlin)
* **[APP-01] Histórico e Máquina do Tempo (Filtros):** A tela de relatórios só envia CSV. Devemos ter uma UI "Time Machine" mostrando (ex: "Qual foi o último lugar em que a ferramenta XYX foi vista há 3 dias?").
* **[APP-02] Testes UI End-to-End E2E:** Aumentar a cobertura (já temos testes unitários para a camada de Banco) focando nos ViewModels do Compose via *UI Automator*.
* **[APP-03] iOS / Kotlin Multiplatform:** Mover as regras de negócios (Room Database, parsers e lógicas de status) para KMM, pavimentando o caminho para compilar o App no iOS usando a mesma base de código central.
* **[APP-04] Logcat e Telemetria Local:** Criar um atalho escondido (ex: bater 7 vezes no número de versão) para mostrar os logs nativos do BLE diretamente na tela para debugar conexões no campo sem conectar cabos.

## 🛠️ 3. Mecânica e Hardware (PCB)
* **[HW-01] Porta USB-C Waterproof NATIVA:** O projeto atual usa cabos ou baterias separadas 18650. Planejar uma PCB "Mainboard" unificada que tenha carregamento TP4056 ou TIs, mais USB Type-C, com um anel de vedação, descartando soldas nos fios.
* **[HW-02] Transdutor Piezoelétrico com Câmara Acústica:** Substituir o buzzer passivo padrão por um transdutor muito mais alto para galpões ruidosos (> 100 dB).
* **[HW-03] Botão Físico com Debounce em Hardware:** Embora o debounce em C++ atual (300ms) esteja funcional, adicionar um circuito RC (Resistor-Capacitor) para proteger as portas do ESP32 de "bounce" puro.
* **[HW-04] Proteção ESD (TVS):** Placa necessita de diodos supressores (TVS) nas linhas de botão e USB, prevenindo a queima do chip em ambientes secos com eletricidade estática.

---

> **Dívidas Técnicas Pagas (Changelog Recente):**
> * ✅ *Lixo de Repositório Limpo* (scripts Python mortos e artefatos de builds velhos foram deletados).
> * ✅ *Stealth Mode* integrado com sucesso no App Android e no Firmware.
> * ✅ *Relatórios e Exportação* agora estão devidamente acoplados em CSV no menu do aplicativo.
