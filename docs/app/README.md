# 📱 Guia do Aplicativo Android (Kotlin + Jetpack Compose)

O aplicativo do **Trakr** foi desenvolvido nativamente para a plataforma Android, utilizando **Kotlin**, **Jetpack Compose** (Material 3) e **Room Database** para persistência offline local.

O uso de código nativo puro com um **Foreground Service dedicado** garante máxima estabilidade na conexão Bluetooth Low Energy (BLE) em segundo plano, mesmo com a tela do smartphone desligada ou durante deslocamentos.

---

## 🛠️ Pré-requisitos e Ambiente

* **Android Studio:** Iguana (2023.2.1) ou superior.
* **SDK Android:** API 35 (mínimo suportado: API 26 / Android 8.0).
* **Hardware recomendado:** Dispositivo Android físico com Bluetooth 5.0+ e suporte a NFC (emuladores não possuem stack BLE/NFC completa).

---

## 🔒 Permissões do Sistema (`AndroidManifest.xml`)

Para operação completa e autônoma, o app requer as seguintes permissões:

```xml
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" /> <!-- BLE em Androids legados e detecção de velocidade GPS -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_LOCATION" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.NFC" />
```

---

## 🏗️ Estrutura de Telas e Navegação

O app é estruturado em 5 seções principais na barra de navegação inferior mais a tela de Configurações centralizada:

### 1. 🚀 Setup & Onboarding (`SetupScreen.kt`)
* Fluxo inicial obrigatório para novos usuários.
* Busca e emparelhamento com o rastreador TRK-Finder via filtro inteligente.
* Solicitação e configuração do PIN de segurança (SHA-256) com fallback de falha.

### 2. 🏠 Painel Geral (Dashboard — `DashboardScreen.kt`)
* Exibe a contagem total de ferramentas, presentes e ausentes em tempo real.
* Monitor de status dos rastreadores conectados (tensão e porcentagem de bateria, RSSI de conexão, status de sincronização).
* Botão de disparo de varredura imediata (`rescan`) e atalhos rápidos para Kits e Configurações.

### 2. 🧰 Lista e Detalhes de Ferramentas (`ToolListScreen.kt` & `ToolDetailScreen.kt`)
* Busca rápida e filtros por status (Todas, Presentes, Ausentes, Por Kit).
* **Auto-captura de tags (`capture_tag`):** Permite ler a tag mais próxima para cadastrar uma nova ferramenta em segundos sem digitar o EPC.
* **Gravação de EPC (`write_epc`):** Permite programar um novo código EPC diretamente em tags RFID virgens pelo módulo YRM100.
* **Disparo de Radar Direto:** Clique em qualquer ferramenta ausente para abrir a mira de localização.
* **Personalização de Alertas:** Definição individual de importância, som de notificação (padrão, longo, silencioso) e vibração por ferramenta.

### 3. 📋 Kits de Trabalho (Job Kits — `JobKitsScreen.kt`)
* Permite criar e agrupar ferramentas por tipos de serviço ou maletas de trabalho (ex: "Kit Elétrica", "Kit Hidráulica").
* Checklist de conferência com barra de progresso visual de conclusão do kit.

### 4. 🔔 Central de Alertas (`AlertListScreen.kt`)
* Histórico cronológico de notificações de ausência e eventos de sistema.
* Deep-linking: ao tocar em um alerta, o app navega diretamente para a ferramenta correspondente.

### 5. 📊 Estatísticas e Métricas Locais (`StatsScreen.kt`)
* **Mais Esquecidas:** Ranking com agregação local (`GROUP BY`) das ferramentas com maior índice de ausência.
* **Taxa de Presença:** Porcentagem histórica de ferramentas presentes durante as conferências.
* **Frequência de Uso:** Histórico diário de varreduras e disparos de alertas.
* **Sessões de Varredura:** Registro temporal das auditorias de inventário realizadas.

### 6. ⚙️ Configurações & Diagnóstico (`ConfigScreen.kt`)
* **Calibração de RF:** Ajuste fino de `rssi_offset`, `rssi_threshold` e perfis de ambiente (`env_profile`).
* **Potência de Transmissão UHF:** Ajuste dinâmico de 0 a 33 dBm (`tx_power_dbm`).
* **Segurança por PIN:** Configuração de senha de 4 a 32 dígitos com hash SHA-256 e expiração de sessão de 5 minutos.
* **Diagnóstico de Hardware:** Painel de telemetria dos sensores (tensão/bateria INA219, temperatura/umidade/pressão BME280, acelerômetro MPU6050) e lista de add-ons detectados.
* **Backup e Restauração:** Exportação e importação de toda a base em JSON unificado para migração ou segurança de dados.

---

## ⚡ Módulos Core e Recursos Avançados

### 🎯 Modos de Radar e Localização (`RadarScreen.kt`)
* **Single Target:** Mira radar com estimativa de proximidade em dBm e indicação direcional por delta (`continue`, `turn_around`, `hold`).
* **Multi-Alvo (`start_radar_multi`):** Localização simultânea de múltiplas ferramentas com ranking ordenado de potência.
* **Live Streaming (`start_live`):** Transmissão contínua de todas as tags visíveis no campo do leitor.
* **Sincronia com Display OLED:** O app e o Tactical HUD do rastreador físico operam em perfeita sintonia durante a busca.
* **Feedback Multissensorial:** Cadência de bipes sonoros e vibração háptica no smartphone proporcionais à proximidade.

### 🚗 Alerta de Deslocamento / Anti-Esquecimento em Trânsito (`MovementAlertManager.kt`)
* Monitora a velocidade de deslocamento via GPS integrado do smartphone.
* Caso o usuário inicie deslocamento veicular (> 15 km/h) e o inventário possua ferramentas faltantes, um **Alerta Crítico de Deslocamento** em tela cheia com alarme sonoro é disparado imediatamente.

### 🏷️ Integração NFC (`NfcPairingManager.kt` & `NfcReaderHelper.kt`)
* Pareamento automático por toque com o rastreador físico TRK-Finder.
* Leitura direta de tags NFC para consulta instantânea de status da ferramenta.

### 🔄 Atualização de Firmware OTA BLE (`OtaManager.kt`)
* Gerencia o particionamento duplo do ESP32, transmitindo binários `.bin` em chunks sem fio via GATT com verificação de integridade e feedback de progresso percentual.