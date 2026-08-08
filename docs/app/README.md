Guia de Configuração: App Android (Kotlin)
==========================================

Este aplicativo foi desenvolvido nativamente para Android utilizando **Kotlin** e **Jetpack Compose** para a interface (UI). O uso de código nativo garante a máxima estabilidade da conexão Bluetooth LE (BLE) em segundo plano (background).
Pré-requisitos
--------------

* Android Studio (Iguana ou superior)

* SDK Android API 34+

* Celular físico com Android 9.0+ para testar o Bluetooth (Emuladores não suportam BLE nativo bem).

Dependências Principais (build.gradle)
--------------------------------------

O projeto utiliza bibliotecas modernas do ecossistema Android:

* **Jetpack Compose:** Para construção da UI (Dark Mode nativo).

* **Room Database:** Para o cache local do inventário (`inventory.json` espelhado do ESP32).

* **Coroutines / Flow:** Para lidar com a assincronicidade da comunicação BLE.

* **AndroidX Bluetooth:** Ou acesso direto ao `BluetoothGatt` do SDK padrão.

Permissões Necessárias (AndroidManifest.xml)
--------------------------------------------

Para que o app consiga parear com a maleta e manter o serviço rodando, as seguintes permissões são exigidas e devem ser aceitas pelo usuário no primeiro uso:
    <uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
    <uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" /> <!-- Necessário em Androids antigos para BLE -->
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
O Segredo da Estabilidade: Foreground Service
---------------------------------------------

Ao contrário de frameworks híbridos, aqui utilizamos um **Serviço de Primeiro Plano** (`Foreground Service`). O Android exibirá uma notificação silenciosa e fixa ("Trakr conectado à Maleta 01"), o que impede o sistema operacional de "matar" a conexão BLE para economizar bateria. Isso garante que o alerta de ferramenta perdida chegue mesmo se seu celular estiver bloqueado no bolso a tarde inteira.
