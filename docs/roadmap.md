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

---

## 🚫 Fora de escopo (decisões)

- **Nuvem / sincronização remota** — nada de dados na nuvem; sem telemetria, conta ou login
- **Multiusuário** — sem multi-tenancy; no máximo mire nome local de usuário (Fase 2)

> Status do hardware: builds e código funcionam; validações físicas (consumo
> real, varredura com UHF, impressão/neno CAD) dependem de bancada — ver
> [`docs/cad/validation.md`](./cad/validation.md).