# 🗺️ Roadmap do Trakr

Este documento reúne o **Roadmap Concluído** (tudo que já está implementado no
código) e o **Roadmap de Features** (próximas sugestões). O projeto é
**100% offline-first** — nenhum dado sai da maleta ou do celular.

---

## ✅ Concluído (entregas no `main`)

| Entrega | O que faz | Referência |
| --- | --- | --- |
| Monorepo | Estrutura inicial: firmware, app, docs, hardware, cad | [`README.md`](../README.md) |
| App Kotlin + Compose + Room | Migração do app para Jetpack Compose, Room v4 (SQLite) e Coroutines/Flow | [`docs/app/README.md`](../app/README.md) |
| BLE end-to-end | Inventário, alertas e sincronização via GATT (4 características, MTU 512) | [`docs/protocol/gatt.md`](../protocol/gatt.md) |
| Cadastro de tags pelo app | `add_tool` / `remove_tool` via GATT Control, com fallback offline | [`docs/protocol/gatt.md`](../protocol/gatt.md) |
| Perfis de maleta | `inventory_<id>.json` por perfil no LittleFS + seletor no app | `firmware/src/main.cpp`, [`docs/protocol/gatt.md`](../protocol/gatt.md) |
| Histórico de eventos | `events.json` (máx. 100) com `tool_missing`, `tool_back`, `lid_open`, `lid_closed`, `boot`; espelhado no Room + export CSV | [`docs/protocol/gatt.md`](../protocol/gatt.md) |
| Deep sleep eficiente | Wake-up magnético (Sensor Hall ext0), `YRM100_EN_PIN` em LOW + `INPUT_PULLDOWN` no sleep | [`docs/firmware/README.md`](../firmware/README.md) |
| Protocolo de medição de consumo | Equipamento, método e estados documentados (a medição real depende de multímetro) | [`docs/hardware/power-measurement.md`](../hardware/power-measurement.md) |
| Múltiplas maletas simultâneas | Scan multi-device, sessão GATT por maleta, roteamento por perfil, reconexão automática | `app/.../core/ble/BleManager.kt` |

---

## 🚀 Próximas features (sugestões)

### Fase 1 — Robustez e UX (curto prazo)

- [ ] **PIN de acesso da maleta** — comando `auth` no GATT Control: sessão autenticada de N minutos para `add_tool`/`remove_tool`/`select_toolbox`; o PIN é definido no setup e gravado **hasheado** (SHA-256) no LittleFS; leituras (inventário) continuam abertas — só as ações de gerenciamento exigem o PIN
- [ ] **Estado de conexão por maleta na UI** — indicador conectado/desconectado por item e botão de reconexão manual
- [ ] **Renomear / excluir maleta** — editar nome e excluir perfil local com confirmação (limpa dados do perfil)
- [ ] **Busca e filtro** na lista de ferramentas (por nome ou tag)
- [ ] **Botão "Varrer agora"** — forçar `rescan` direto da UI, sem esperar o wake magnético
- [ ] **Relógio real nos eventos** — RTC (ex.: DS3231) no firmware para timestamps absolutos (hoje `ts` é relativo ao boot)
- [ ] **Rotação do histórico** — histórico paginado por mês, sem perder eventos por limite de 100

### Fase 2 — Inteligência local (médio prazo)

- [ ] **Checkout/checkin com usuário** — registrar localmente quem retirou o quê (sem nuvem)
- [ ] **Alertas configuráveis** — silenciar por maleta, sons/vibrações diferentes por tipo de evento
- [ ] **Estatísticas locais** — ferramentas mais esquecidas, frequência de aberturas (só dados do Room)
- [ ] **Varredura agendada** — wake-up periódico além do Sensor Hall, para auditoria com a tampa fechada
- [ ] **Movimento anti-furto local** — acelerômetro detecta remoção da maleta e dispara alarme local

### Fase 3 — Escala e eficiência (longo prazo)

- [ ] **Replicação de perfil mestre→maletas** — copiar inventário de uma para outra via BLE
- [ ] **Backup/restore local** — export/import único (JSON) de inventário + eventos + configurações
- [ ] **Hardware opcional** — display OLED na tampa, botão físico de rescan, suporte a ESP32-S3
- [ ] **CI e testes** — GitHub Actions buildando firmware (2 envs) e app em cada PR; `pio test` + unit tests do app

### Fase 4 — Setup Web: `trakr.co/setup` (médio prazo)

Configurar e gravar o firmware pelo **navegador**, com o ESP32 ligado no PC via
USB — sem instalar PlatformIO nem usar o app. A página é **estática** (sem
backend) e usa a **Web Serial API** (Chrome/Edge); a gravação é feita direto na
porta USB padrão da placa (USB-A / Micro / USB-C), pois a grande maioria dos
controladores traz o conversor serial onboard — sem adaptadores e sem se
preocupar com o chip USB-Serial da placa.

**Fluxo do usuário:**

1. Abrir `trakr.co/setup` em um navegador Chromium (Chrome/Edge).
2. Preencher o "assistente de setup" com o hardware real da maleta:
   - microcontrolador (ESP32-WROOM-32, ESP32-S3, ...);
   - sensor Hall presente? (sim/não);
   - display na tampa presente? (sim/não);
   - tipo de LED (WS2812B RGB endereçável / LED comum);
   - módulo RFID (YRM100 e variantes);
   - **PIN da maleta** (exigido no app para gerenciar via BLE);
   - nome do perfil padrão (ex.: "main").
3. Clicar em **Conectar** → o navegador lista as portas seriais/USB → selecionar
   o ESP32.
4. Clicar em **Gravar** → o esptool-js escreve, com barra de progresso:
   - bootloader + tabela de partições;
   - firmware do aplicativo (compilado para as opções escolhidas);
   - filesystem LittleFS com `config.json` (PIN hashado, opções de hardware),
     `inventory.json` vazio e `events.json` vazio.
5. Concluir com instruções: *"desconecte, feche a tampa, e no app use o PIN
   para conectar e gerenciar"*.
6. A mesma página serve de **atualizador por USB**: escolher uma release nova
   e regravar (mesma atualização por USB que já existe via PlatformIO, mas sem
   nenhuma ferramenta local instalada).

**Pré-requisitos de código (para implementar):**

- [ ] **Flags de hardware no firmware** — extrair para `#ifdef`/`#define` as
  opções hoje fixas: `TRAKR_HAS_HALL`, `TRAKR_HAS_DISPLAY`, `TRAKR_LED_TYPE`,
  `TRAKR_MCU`, `TRAKR_RFID_MODEL` (ver `firmware/include/pins.h`);
- [ ] **Novos ambientes no `platformio.ini`** por MCU (ex.: `esp32-s3`);
- [ ] **Comandos `auth` no GATT** — o mesmo PIN da Fase 1: a página não precisa
  de sessão (ela escreve o PIN no `config.json`), mas o app passa a exigir o
  PIN; firmware valida com SHA-256 armazenado;
- [ ] **Pipeline de builds por combinação** — GitHub Actions preparando a matriz
  de firmwares (ou um build único com opções em runtime) e publicando
  `site/setup/manifest.json` com os binários por MCU/variante;
- [ ] **Página estática `site/setup/`** — HTML/JS com esptool-js (Web Serial),
  sem servidor; pode ser servida até por GitHub Pages;
- [ ] **Testes manuais** — gravação em Chrome (Windows/macOS) com placas
  padrão (devkit ESP32 com CP2102, ESP32-S3 USB-C).

> **Decisão:** o QR code de pareamento foi descartado — a segurança fica
> 100% no **PIN** da maleta (sessão autenticada no BLE).

---

## 🚫 Fora de escopo (decisões)

- **Nuvem / sincronização remota** — nada de dados na nuvem; sem telemetria, conta ou login
- **Multiusuário** — sem multi-tenancy; no máximo mire nome local de usuário (Fase 2)

> Status do hardware: builds e código funcionam; validações físicas (consumo
> real, varredura com UHF, impressão e montagem do CAD) dependem de bancada — ver
> [`docs/cad/validation.md`](./cad/validation.md).