# 🧊 Exports CAD (STL/3MF)

Este diretório armazena os **arquivos de referência** exportados dos scripts do Fusion 360, para quem quiser imprimir sem abrir o Fusion.

## Estado atual

> ⚠️ **Pendente de validação física.** O CAD foi gerado parametricamente (`cad/scripts/SmartToolboxUI.py`), mas **nenhuma peça foi exportada nem impressa ainda**. Os arquivos `.3mf`/`.stl` de referência serão adicionados aqui assim que o script for executado no Fusion 360. Antes de produzir uma maleta completa, leia o [checklist de validação](../../docs/cad/validation.md).

## Estrutura esperada (quando exportado)

```
cad/exports/
├── trakr_shell_base.stl       # Base da maleta (PETG)
├── trakr_shell_lid.stl        # Tampa
├── trakr_tray_modular.stl     # Bandeja modular (insertos M3)
├── trakr_light_pipe.stl       # Guia de luz (filamento transparente)
└── trakr_feet_tpu.stl         # Pés de borracha (TPU)
```

* Boas práticas: exporte em **mm**, orientado para a cama de impressão (base plana para baixo), unidades métricas.
* Formato recomendado: **3MF** (metadata de cores/material) e **STL** (compatibilidade máxima com slicers).

## Como gerar

1. Abra o script no Fusion 360 (script add-in `SmartToolboxUI.py`).
2. Execute e inspecione o design paramétrico.
3. **Exportar** → **STL/3MF** → salve aqui com os nomes acima.
4. Reporte qualquer falha em uma issue (com screenshot) — o projeto ainda é "Em Desenvolvimento" e o CAD paramétrico precisa de ajustes reais.