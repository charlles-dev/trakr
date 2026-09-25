# 🧊 Exports CAD (STL/3MF)

Este diretório armazena os **arquivos de referência** exportados dos scripts do Fusion 360, para quem quiser imprimir sem abrir o Fusion.

## 🛑 O Último Passo para o 100% (Mundo Físico)

> **ATENÇÃO:** O software (App) e o Firmware (ESP32) já estão **100% concluídos, compilados e validados**. A documentação de arquitetura e wiring também está entregue. 
> A **ÚNICA** coisa que separa este projeto de ser um produto físico completo no mundo real é a geração destas malhas 3D e a montagem na bancada!

Como o modelo 3D é gerado por um script paramétrico Python (`cad/scripts/Trakr.py`), **você (o usuário)** deve rodá-lo dentro do seu Autodesk Fusion 360 localmente para gerar as malhas finais. 

## Como gerar os STLs finais (Seu dever de casa):

1. Abra o Autodesk Fusion 360.
2. Pressione `Shift + S` para abrir a janela de Scripts e Add-ins.
3. Clique no ícone de `+` e adicione a pasta `cad/scripts/`.
4. Selecione o script `Trakr` e clique em **Run**.
5. O scanner será desenhado na sua frente. 
6. Clique com o botão direito no componente principal -> **Save As Mesh** (Salvar como Malha).
7. Salve nesta pasta (`cad/exports/`) com os seguintes nomes:
   * `trakr_shell_base.stl` (Carcaça principal)
   * `trakr_shell_lid.stl` (Tampa)
   * `trakr_tray_modular.stl` (Bandeja interna)
8. Imprima em PETG, insira os componentes conforme o [wiring.svg](../../hardware/schematics/wiring.svg) e pronto. **100% concluído.**