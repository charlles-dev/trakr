# Trakr App - Guia de Sobrevivência

## Como compilar

Pré-requisitos: [Flutter SDK](https://docs.flutter.dev/get-started/install).

```bash
cd app
flutter pub get        # baixa dependências (flutter_blue_plus, isar, ...)
flutter run            # roda no device/conector
```

## Estrutura do cache local

O app usa **Isar** para manter uma cópia do inventário recebido via BLE.
O firmware da maleta permanece como fonte da verdade.

## Roadmap
- [ ] Motor BLE (conexão + GATT com o firmware)
- [ ] Notificações locais de alerta
- [ ] Telas: Dashboard, Lista de Ferramentas, Alertas