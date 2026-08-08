# ✅ Checklist de Validação do CAD (Impressão 3D)

Este checklist documenta o **processo de validação física** do projeto. Ele serve para:

* Registrar os resultados de impressões/testes para a comunidade;
* Garantir que mudanças no modelo paramétrico não quebrem a manuteção;
* Transformar o projeto em algo reprodutível (tipo um *build log*).

> **Status:** `cad/exports/` ainda não tem `STL`/`.3mf` de referência e **nenhuma impressão** foi registrada. Preencham este checklist conforme testem.

## Checklist por peça

### 1. Base e Tampa (Trakr Shell)

- [ ] Impressa em PETG/ABS (nunca PLA se vai ser deixada no sol).
- [ ] 4+ paredes (walls), infill 20–25% Gyroid.
- [ ] Suportes removidos sem danos (dobradiças, USB-C, Sensor Hall).
- [ ] Dobradiças funcionais após montagem (não emperram).
- [ ] Tolerância do fecho (clipe) testada com a tampa fechada.

### 2. Bandeja modular

- [ ] Insertos M3 termofixados em ~220°C (PETG) e nivelados.
- [ ] Parafusos M3 roscam sem forçar e sem folga.
- [ ] Ferramentas de teste encaixam nos slots (chaves, alicates, martelo).

### 3. Light Pipe

- [ ] Pedaço de filamento transparente inserido no orifício de ~5mm, trava na flange.
- [ ] LED (WS2812B) ilumina até o exterior com a tampa fechada.

### 4. Pés de TPU

- [ ] Pés colados/impressos nos rebaixos inferiores.
- [ ] Maleta não desliza em bancada inclinada leve.

### 5. Integração eletrônica

- [ ] Módulos encaixam nos canais de cable management.
- [ ] USB-C acessível com tampa fechada.
- [ ] Sensor Hall alinhado com o ímã da tampa (wake-up funciona por magnetismo).

## Checklist de campo (uso real)

- [ ] Inventário completo lido em uma única varredura (< 1s).
- [ ] Falta de ferramenta dispara buzzer + LED + notificação no app.
- [ ] Bateria 18650: duração de uso registrada (ex: X dias) para o log de consumo.

## Log de validação

| Data | Peça | Resultado | Observações | Impressa por |
| --- | --- | --- | --- | --- |

> Data final: quando todo o checklist estiver preenchido e o `cad/exports/` estiver populado, o status do projeto pode mudar de "Em Desenvolvimento" para "Validado".