Diretrizes de Impressão 3D e Montagem Mecânica
==============================================

A carcaça do **TRK-Finder** (scanner UHF portátil ~18 x 6,5 cm) é gerada
parametricamente via Autodesk Fusion 360 e projetada para resistir a impactos
de ferramentas de aço.

Configurações do Fatiador (Cura / PrusaSlicer)
----------------------------------------------

* **Material:** PETG (Recomendado) ou ABS/ASA. **Não utilize PLA** se a carcaça for ser deixada em porta-malas de carros ao sol, pois deformará.

* **Perímetros (Walls):** Mínimo de 4 linhas. A resistência mecânica vem das paredes, não do preenchimento.

* **Preenchimento (Infill):** 20% a 25% padrão **Gyroid (Giroide)**.

* **Suportes:** Necessários apenas nas dobradiças traseiras e nos furos do USB-C / botão.

* **Orientação:** Imprima a base plana contra a mesa (build plate).

Montagem Pós-Impressão
----------------------

### 1. Insertos Térmicos (Heat-set Inserts)

A bandeja modular possui torres projetadas especificamente para insertos M3 de latão.

* Utilize um ferro de solda ajustado em ~220°C (para PETG).

* Pressione o inserto M3 perpendicularmente até ficar nivelado com o plástico. Deixe esfriar por 2 minutos antes de parafusar.

### 2. Guia de Luz (Light Pipe)

A carcaça tem um buraco de ~5mm.

* Corte um pedaço reto de filamento PETG/PLA _transparente_ ou um tubo de acrílico.

* Insira no orifício até travar na flange. O LED ficará posicionado do lado de dentro, iluminando o tubo e brilhando no exterior.

### 3. Pés de Absorção (TPU)

Os rebaixos inferiores (_cutouts_) servem para colar pezinhos de borracha impresso em material flexível (TPU) ou discos anti-derrapantes comerciais (EVA).

### 4. Botão físico

O botão táctil fica acessível pelo corpo da carcaça (GPIO 33): a pressão acorda
o dispositivo do deep sleep e dispara a varredura.

### 5. Validação e Exports

* Os arquivos `.3mf`/`.stl` de referência ficarão em [`cad/exports/`](../../cad/exports/README.md) (aguardando geração do script).
* Antes de imprimir, preencha o [Checklist de Validação](validation.md).