# 🗺️ Roadmap do Trakr

Este documento reúne o **Roadmap Concluído** (tudo que já está implementado no
código) e o **Roadmap de Features** (próximas sugestões). O projeto é
**100% offline-first** — nenhum dado sai do TRK-Finder ou do celular.

---

## ✅ Concluído (entregas no `main`)

| Entrega | O que faz | Referência |
| --- | --- | --- |
| Monorepo | Estrutura inicial: firmware, app, docs, hardware, cad | [`README.md`](../README.md) |
| App Kotlin + Compose + Room | Migração do app para Jetpack Compose, Room v4 (SQLite) e Coroutines/Flow | [`docs/app/README.md`](../app/README.md) |
| BLE end-to-end | Inventário, alertas e sincronização via GATT (4 características, MTU 512) | [`docs/protocol/gatt.md`](../protocol/gatt.md) |
| Cadastro de tags pelo app | `add_tool` / `remove_tool` via GATT Control, com fallback offline | [`docs/protocol/gatt.md`](../protocol/gatt.md) |
| Deep sleep eficiente | Wake-up por botão físico (ext0), `YRM100_EN_PIN` em LOW + `INPUT_PULLDOWN` no sleep | [`docs/firmware/README.md`](../firmware/README.md) |
| Protocolo de medição de consumo | Equipamento, método e estados documentados (a medição real depende de multímetro) | [`docs/hardware/power-measurement.md`](../hardware/power-measurement.md) |
| Múltiplos rastreadores simultâneos | Scan multi-device, sessão GATT por rastreador, reconexão automática | `app/.../core/ble/BleManager.kt` |
| **Modo radar — núcleo** | Botão físico com wake ext0, `TrakYrm100::collectReads` com RSSI, comandos `start_radar`/`stop_radar`, relatório `radar_report` + bipes proporcionais, aba Radar no app com RSSI ao vivo | `firmware/src/main.cpp`, `firmware/lib/TrakYrm100`, `app/.../ui/radar/` |
| **Refatoração radar-only (TRK-Finder)** | Maleta removida do ecossistema (app, firmware, docs): modelo flat no app (DB v6), firmware de produto único (envs `esp32radar`/`esp32radar-sim`), nome de produto **TRK-Finder** no BLE (`TRK-FINDER`), remoção de wizard/perfis/chips | `app/...`, `firmware/`, este documento |

---

## 🚀 Próximas features (sugestões)

### Fase 1 — Robustez e UX (curto prazo)

- [ ] **PIN de acesso do TRK-Finder** — comando `auth` no GATT Control: sessão autenticada de N minutos para `add_tool`/`remove_tool`; o PIN é definido no setup e gravado **hasheado** (SHA-256) no LittleFS; leituras (inventário) continuam abertas — só as ações de gerenciamento exigem o PIN
- [ ] **Estado de conexão na UI** — indicador conectado/desconectado por rastreador e botão de reconexão manual
- [ ] **Notificação de ausência** — se o radar não vê a tag de uma ferramenta por X segundos, o app emite push local
- [ ] **Busca e filtro** na lista de ferramentas (por nome ou tag)
- [ ] **Botão "Varrer agora"** — forçar `rescan` direto da UI, sem esperar o botão físico
- [ ] **Relógio real nos eventos** — RTC (ex.: DS3231) no firmware para timestamps absolutos (hoje `ts` é relativo ao boot)
- [ ] **Rotação do histórico** — histórico paginado por mês, sem perder eventos por limite de 100

### Fase 2 — Inteligência local (médio prazo)

- [ ] **Checkout/checkin com usuário** — registrar localmente quem retirou o quê (sem nuvem)
- [ ] **Alertas configuráveis** — silenciar por rastreador, sons/vibrações diferentes por tipo de evento
- [ ] **Estatísticas locais** — ferramentas mais esquecidas, frequência de varreduras (só dados do Room)
- [ ] **Varredura agendada** — wake-up periódico (timer RTC) para auditoria contínua (modo estação)
- [ ] **Movimento anti-furto local** — acelerômetro detecta remoção do rastreador e dispara alarme local

### Fase 3 — Escala e eficiência (longo prazo)

- [ ] **Backup/restore local** — export/import único (JSON) de inventário + eventos + configurações
- [ ] **Hardware opcional** — display OLED, botão físico secundário, suporte a ESP32-S3
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
2. Preencher o "assistente de setup" com o hardware real do TRK-Finder:
   - microcontrolador (ESP32-WROOM-32, ESP32-S3, ...);
   - display presente? (sim/não);
   - tipo de LED (WS2812B RGB endereçável / LED comum);
   - módulo RFID (YRM100 e variantes);
   - **PIN do rastreador** (exigido no app para gerenciar via BLE);
   - nome do inventário padrão (ex.: "main").
3. Clicar em **Conectar** → o navegador lista as portas seriais/USB → selecionar
   o ESP32.
4. Clicar em **Gravar** → o esptool-js escreve, com barra de progresso:
   - bootloader + tabela de partições;
   - firmware do aplicativo (compilado para as opções escolhidas);
   - filesystem LittleFS com `config.json` (PIN hashado, opções de hardware),
     `inventory.json` vazio e `events.json` vazio.
5. Concluir com instruções: *"desconecte, e no app use o PIN para conectar e
   gerenciar"*.
6. A mesma página serve de **atualizador por USB**: escolher uma release nova
   e regravar (mesma atualização por USB que já existe via PlatformIO, mas sem
   nenhuma ferramenta local instalada).

**Pré-requisitos de código (para implementar):**

- [ ] **Flags de hardware no firmware** — extrair para `#ifdef`/`#define` as
  opções hoje fixas: `TRAKR_HAS_DISPLAY`, `TRAKR_LED_TYPE`, `TRAKR_MCU`,
  `TRAKR_RFID_MODEL` (ver `firmware/include/pins.h`);
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
> 100% no **PIN** do rastreador (sessão autenticada no BLE).

---

## 📡 TRK-Finder — Modo radar (núcleo do produto)

O rastreador portátil (ESP32 + YRM100 + BLE) em uma carcaça de scanner UHF
(~18 x 6,5 cm). Duas formas de uso:

* **Modo Estação** — o rastreador fica deixado no ambiente de trabalho
  (temporário) e executa auditorias periódicas do inventário (agendamento
  planejado — Fase R2);
* **Modo Radar** — quando uma ferramenta falta, o operador percorre o ambiente
  com o rastreador na mão: o YRM100 mede a potência (**RSSI, dBm**) da tag
  faltante e o firmware guia por **bipes** (frequência proporcional ao sinal,
  estilo "detector de metais"); o app mostra a intensidade em tempo real.

O CAD da carcaça portátil já existe como script do Fusion 360 (`Trakr.py`) —
falta portá-lo para o monorepo (`cad/scripts/`).

### Fase R1 — Núcleo do rastreador (curto prazo)

- [ ] **Portar o script CAD para o repo** — copiar `Trakr.py` para `cad/scripts/` (hoje só existe localmente no Fusion 360)
- [x] **RSSI no `TrakYrm100`** — coletar a potência (dBm) por EPC (hoje `collectEpc` descarta o RSSI)
- [x] **Comando `start_radar` no GATT Control** — varre a tag alvo, publica RSSI via notify e aciona o buzzer com padrão proporcional à potência
- [x] **Tela "Localizar" no app** — barra de intensidade em tempo real e comando iniciar/parar (som no celular fica na Fase R2)

### Fase R2 — Estação e usabilidade (médio prazo)

- [ ] **Direção aproximada** — amostragem de RSSI ao caminhar para sugerir sentido (ex.: "+3 dBm no último passo → continue")
- [ ] **Estação inteligente** — varreduras agendadas (timer wake) + notificação no app quando faltar ferramenta no ambiente
- [ ] **Notificação de ausência no app** — push local quando o radar não vê a tag por X s

### Fase R3 — Alcance (longo prazo)

- [ ] **Antena UHF externa** — opção de conector IPEX para antena externa, ampliando o raio de localização no ambiente
- [ ] **Multi-alvo** — radar para várias tags faltantes em sequência, com ranking por RSSI

---

## 🧩 Add-ons e Plugins (opcionais — nada disso é obrigatório)

> ✅ **Status: aprovado em 2026-08-15** — todos os itens desta seção foram
> aprovados e entram no plano como add-ons opcionais. O catálogo
> (`docs/improvements.md`) volta a ser usado apenas para ideias novas em
> discussão.

Melhorias de hardware e software tratadas como **add-ons independentes**: nada
abaixo bloqueia o funcionamento do produto base (TRK-Finder). A regra é
*detecção por flags* (`TRAKR_HAS_*` no firmware) — o firmware e o app rodam com
o produto no mínimo e ganham as funcionalidades extras conforme os módulos são
adicionados (ou removidos) no hardware.

O catálogo completo, com **esforço, impacto e status por item**, vive em
[`docs/improvements.md`](./improvements.md) — é o documento onde discutimos e
priorizamos cada melhoria.

### ⚡ Energia e Bateria

- [ ] **[HW] Monitor de bateria real** — divisor de tensão ou INA219 (I2C); % de carga no app e no dispositivo
- [ ] **[HW] Dock de estação** — base com pogo pins ou carregamento sem fio (Qi); o rastreador para no dock no modo estação
- [ ] **[HW] Backup em pendrive (USB OTG)** — com ESP32-S3 (host USB); exportar inventário + eventos por USB, 100% offline
- [ ] **[HW] LED externo de carga (TP4056)** — sinalização de carga visível na carcaça
- [ ] **[HW] Porta de bateria com troca rápida** — 18650 em suporte com trava (sem solda)

### 📡 RF e Alcance

- [ ] **[HW] Antena UHF externa (U.FL, 3–6 dBi)** — amplia o raio de busca; variante montada no dock para uso fixo
- [ ] **[SW] Potência TX configurável do YRM100** — ajuste de dBm por cenário (dentro de prédio x canteiro aberto)
- [ ] **[SW] Varredura "ao vivo"** — streaming contínuo de EPCs + RSSI via GATT durante a auditoria de ambiente
- [ ] **[SW] Calibração de RSSI por ambiente** — offset de dBm por cenário (campo, galpão metálico, sala)

### 📍 Localização e Navegação

- [ ] **[HW] Bússola QMC5883L** — amostras de RSSI amarradas ao rumo para sugerir direção de busca
- [ ] **[SW] Direção aproximada por passo** — comparação de RSSI entre amostras ("+3 dBm no último passo → continue")
- [ ] **[SW] Alarme de encontro** — triplo bip + LED quando a tag alvo passa do limiar (ex.: RSSI > −40 dBm)
- [ ] **[SW] "Achar meu dispositivo"** — o app usa o RSSI do próprio celular para guiar até o rastreador (sem UHF)
- [ ] **[SW] Histórico de localizações no Room** — por dispositivo, com data/hora (offline)

### 🛡️ Segurança e Acesso

- [ ] **[SW] Pareamento por aproximação NFC** — substitui a digitação inicial do PIN (depende do PN532)
- [ ] **[HW] Motor vibrador** — alerta silencioso (rastreador na mão)

### 🌡️ Sensores e Contexto

- [ ] **[HW] BME280** — temperatura/umidade/pressão; alerta de exposição das ferramentas + pista de andar (pressão)
- [ ] **[HW] IMU (MPU6050 / LIS3DH)** — wake por gesto/movimento
- [ ] **[SW] Alerta de exposição** — calor/umidade das ferramentas (depende do BME280)
- [ ] **[SW] Wake por movimento** — pegar o rastreador já dispara a varredura (depende do IMU)

### 🔔 HMI e Feedback

- [ ] **[HW] OLED SSD1306 0.96"** — status sem app (contagem, bateria, RSSI do radar)
- [ ] **[HW] Buzzer passivo (tons)** — frequência variável para o "detector de metais" do radar (recomendado)
- [ ] **[HW] Botão físico secundário** — alternar modo radar sem o app
- [ ] **[HW] RTC DS3231** — timestamps absolutos nos eventos (complementa a Fase 1)
- [ ] **[SW] Padrões sonoros por evento** — sons/vibrações distintas por tipo de alerta, configuráveis

### 📱 App e UX (software)

- [ ] **[SW] Checklist guiado** — lista de ferramentas; bipa rápido quando lê cada tag ao percorrer o ambiente
- [ ] **[SW] Inspeção de add-ons** — tela de diagnóstico que lista quais módulos o dispositivo detectou
- [ ] **[SW] Limiares de RSSI configuráveis** — ajuste fino do alerta de encontro por tag

### 🛠️ Produção e Montagem (mecânica)

- [ ] **[HW] Encaixe snap-fit da carcaça** — menos parafusos, montagem sem ferramenta
- [ ] **[HW] Vedações de silicone / IP54** — proteção contra poeira e respingo
- [ ] **[HW] Pés/bumpers TPU antiderrapante** — absorção e estabilidade
- [ ] **[HW] Parafusos captivos** — presos na carcaça para não se perderem
- [ ] **[HW] QR de montagem na carcaça** — aponta para a documentação de assemblagem

---

## 🚫 Fora de escopo (decisões)

- **Nuvem / sincronização remota** — nada de dados na nuvem; sem telemetria, conta ou login
- **Multiusuário** — sem multi-tenancy; no máximo mire nome local de usuário (Fase 2)

> Status do hardware: builds e código funcionam; validações físicas (consumo
> real, varredura com UHF, impressão e montagem do CAD) dependem de bancada — ver
> [`docs/cad/validation.md`](./cad/validation.md).