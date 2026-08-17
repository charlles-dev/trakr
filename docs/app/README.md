Guia de Configuração: App Android (Kotlin)
==========================================

Este aplicativo foi desenvolvido nativamente para Android utilizando **Kotlin** e **Jetpack Compose** para a interface (UI). O uso de código nativo garante a máxima estabilidade da conexão Bluetooth LE (BLE) em segundo plano (background).

Pré-requisitos
--------------

* Android Studio (Iguana ou superior)

* SDK Android API 35+

* Celular físico com Android 8.0+ para testar o Bluetooth (Emuladores não suportam BLE nativo bem).

Dependências Principais (build.gradle)
--------------------------------------

O projeto utiliza bibliotecas modernas do ecossistema Android:

* **Jetpack Compose:** Para construção da UI (Dark Mode nativo).

* **Room Database:** Para o cache local do inventário (`inventory.json` espelhado do ESP32).

* **Coroutines / Flow:** Para lidar com a assincronicidade da comunicação BLE.

* **AndroidX Bluetooth:** Ou acesso direto ao `BluetoothGatt` do SDK padrão.

Permissões Necessárias (AndroidManifest.xml)
--------------------------------------------

Para que o app consiga parear com o TRK-Finder e manter o serviço rodando, as seguintes permissões são exigidas e devem ser aceitas pelo usuário no primeiro uso:
    <uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
    <uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" /> <!-- Necessário em Androids antigos para BLE -->
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

O Segredo da Estabilidade: Foreground Service
---------------------------------------------

Ao contrário de frameworks híbridos, aqui utilizamos um **Serviço de Primeiro Plano** (`Foreground Service`). O Android exibirá uma notificação silenciosa e fixa ("Trakr conectado ao TRK-Finder"), o que impede o sistema operacional de "matar" a conexão BLE para economizar bateria. Isso garante que o alerta de ferramenta não encontrada chegue mesmo se seu celular estiver bloqueado no bolso a tarde inteira.

Conexão com o TRK-Finder
------------------------

O app escaneia por dispositivos com nome iniciado em **`TRK-`** (firmware
publica `TRK-FINDER`) e conecta-se a todos os rastreadores encontrados na
mesma sessão:

* **Aba Ferramentas / Kits:** As abas principais do app permitem visualizar o inventário
  e agrupar as ferramentas por kits. É possível clicar em uma ferramenta específica
  (mesmo as não cadastradas no inventário local, passando a Tag EPC diretamente) 
  para acionar a tela de Busca.
* **Tela de Busca (Radar):** Abre de forma sobreposta ao clicar em uma ferramenta 
  ausente. O app envia o comando de radar para o rastreador, que passa a publicar 
  relatórios `radar_report` (via Event notify) com o **RSSI em dBm**; a tela mostra 
  a intensidade em tempo real (barra de proximidade) e o estado da busca.
* O radar também faz a varredura de inventário normal (botão físico ou
  comando `rescan`), atualizando em tempo real o Dashboard e os Kits.