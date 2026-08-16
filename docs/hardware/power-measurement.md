# 🔋 Medição de Consumo Energético (Deep Sleep)

O TRK-Finder é alimentado por uma **bateria 18650** (carregada via TP4056, USB-C).
Para dimensionar a autonomia real é preciso **medir** (não chutar) o consumo em
cada estado do firmware. Este documento define o protocolo de medição.

> Estado atual: o firmware já desliga o que é possível no deep sleep —
> `YRM100_EN_PIN` (GPIO14) em LOW + pino em `INPUT_PULLDOWN` (trava LOW) e o
> ESP32 entra em `esp_deep_sleep_start()` com wake-up ext0 no **botão físico**
> (GPIO 33). Falta apenas **medir** e registrar os números na tabela abaixo.

## Equipamento

- Multímetro com escala de corrente (µA/mA) ou **uCurrent Gold** (recomendado
  para correntes abaixo de 1 mA);
- Cabos/jumpers e, idealmente, uma fonte USB com display de corrente;
- Celular com o app Trakr (para forçar os estados BLE/RASTREIA);
- Multímetro de tensão para confirmar a bateria.

## Método

1. **Ponto de medição:** coloque o multímetro **em série** na linha de
   alimentação positiva (entre o TP4056 e o ESP32, ou entre a bateria e o
   módulo de carga — anote qual usou).
2. Faça a medição em cada estado, em ciclos de no mínimo 10 segundos por
   estado, e anote o valor **médio** observado.
3. Para estados BLE, mantenha o app conectado; para deep sleep, aguarde o
   firmware dormir (LED apagado, `[TRAKR] Deep sleep...` no serial).

## Estados e valores esperados

| Estado | Como forçar | ESP32 | YRM100 (EN off) | Total esperado |
| --- | --- | --- | --- | --- |
| **DORME** (deep sleep) | Aguardar ~30 s sem interação (ESCUTA expira) | ~10–40 µA | ~1 µA (EN LOW) | **~50 µA** |
| **LEITURA** (~500 ms) | Pressionar o botão (wake) | 200–300 mA | 150–250 mA | ~400 mA (pico) |
| **RASTREIA** (radar, ciclo ~400 ms) | `start_radar` pelo app | 200–300 mA (picos + buzzer) | 150–250 mA | ~400 mA (pico) |
| **SINCRONIZA** (30 s) | App conectado | ~150 mA (BLE TX) | off | ~150 mA |

> Os valores são típicos de datasheet (ESP32-WROOM-32 ~240 mA pico RF,
> YRM100 ~200 mA TX) e **servem só como referência** — preencha a coluna
> "medido" com os valores do seu multímetro.

## Autonomia estimada

Com os valores medidos, use:

```
consumo_dia = (t_sleep × i_sleep) + (t_wake × i_wake_medio) + ...
autonomia_dias = capacidade_mAh / consumo_dia
```

Exemplo ilustrativo com **18650 de 3000 mAh** e 2 varreduras/dia (~31 s ativo):

```
sleep: 23.99 h × 0.05 mA ≈ 1.20 mAh
ativo: 31 s × 150 mA ≈ 1.29 mAh
consumo_dia ≈ 2.5 mAh  →  autonomia ≈ 1200 dias (teórico)
```

A medição real costuma ficar muito abaixo do teórico (reguladores, fuga do
TP4056, RTC); o número real é o que interessa.

## Registro dos resultados

- Preencha a tabela acima com os valores medidos (coloque "medido" na coluna
  Total e commite);
- Registre a autonomia real de campo no **Log de validação** do CAD
  ([`docs/cad/validation.md`](../cad/validation.md));
- Se o consumo em DORME ficar acima de ~100 µA, investigue antes de fechar:
  1. LED/WS2812B com pull-up interno ativo;
  2. BUZZER não drenado (`digitalWrite LOW` antes do sleep);
  3. UART2 do YRM100 ainda energizado (`disablePower()` não encerra o Serial2 —
     o EN em LOW desliga o módulo inteiro na maioria dos módulos com pino PE/EN);
  4. Pinos flutuantes no GPIO33/25/26.