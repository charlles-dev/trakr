"""Validador estático do SmartToolboxUI.py.

Detecta os padrões que causam "Nenhum corpo alvo encontrado para cortar" no
Fusion 360 sem precisar executar o script:

1. setDistanceExtent(False, <valor negativo>) — a distancia negativa extrui
   para fora do corpo na maioria dos contextos (corpos da base vao para -z,
   corpos da bandeja/tampa vao para +z; valores negativos gerais sao
   suspeitos e sao a causa de quase todos os erros ja vistos).
2. Extrusao de corte (CutFeatureOperation) com `transform` — o Fusion procura
   o corpo alvo NA POSICAO DO PERFIL (o transform so move o resultado final),
   entao o corte falha se o perfil nao estiver sobre o corpo.
3. Extrusoes de corte com `transform` movidas por variavel (rastreio simples).

Uso:  python check_cad.py [caminho_do_script]
Saida: lista de problemas com numero de linha, ou "OK" se nenhum.
"""
import ast
import os
import re
import sys


def problemas_ast(src):
    tree = ast.parse(src)
    problemas = []

    for node in ast.walk(tree):
        if not isinstance(node, ast.Call):
            continue
        nome = ''
        if isinstance(node.func, ast.Attribute):
            nome = node.func.attr
        elif isinstance(node.func, ast.Name):
            nome = node.func.id

        if nome == 'setDistanceExtent' and len(node.args) >= 2:
            negativo = node.args[0]
            dist = node.args[1]
            valor = None
            if isinstance(dist, ast.Constant):
                valor = dist.value
            elif isinstance(dist, ast.Call) and dist.args:
                arg = dist.args[0]
                if isinstance(arg, ast.Constant):
                    valor = arg.value
                elif isinstance(arg, ast.UnaryOp) and isinstance(arg.operand, ast.Constant):
                    valor = -arg.operand.value
            if isinstance(negativo, ast.Constant) and negativo.value is False:
                if isinstance(valor, (int, float)) and valor < 0:
                    problemas.append(
                        'L%d: setDistanceExtent(False, %s) com distancia '
                        'negativa — extrusao provavelmente sai do corpo' %
                        (node.lineno, valor))
            elif isinstance(negativo, ast.Constant) and negativo.value is True:
                if isinstance(valor, (int, float)) and valor < 0:
                    problemas.append(
                        'L%d: setDistanceExtent(True, %s) — distancia deve '
                        'ser positiva (o booleano ja define a direcao)' %
                        (node.lineno, valor))

    return problemas


def problemas_transform(src):
    """Cortes com transform: `x = createInput(..., CutFeatureOperation)` e
    depois `x.transform = ...` no mesmo escopo (funcao)."""
    problemas = []
    escopos = re.split(r'(?m)^def ', src)
    for bloco in escopos[1:]:
        nome_funcao = bloco.split('(')[0].strip()
        cuts = set(re.findall(r'(?m)^\s*(\w+)\s*=\s*\w+\.extrudeFeatures\.createInput\('
                              r'[^)]*CutFeatureOperation', bloco))
        var_transform = set(re.findall(r'(?m)^\s*(\w+)\.transform\s*=', bloco))
        for v in sorted(cuts & var_transform):
            problemas.append(
                'def %s(): corte "%s" usa transform — o corpo alvo e '
                'procurado na posicao do perfil; use plano de construcao '
                'offset no lugar' % (nome_funcao, v))
    return problemas


def main():
    caminho = sys.argv[1] if len(sys.argv) > 1 else os.path.join(
        os.path.dirname(os.path.abspath(__file__)), 'SmartToolboxUI.py')
    with open(caminho, encoding='utf-8') as f:
        src = f.read()

    todos = []
    todos += problemas_ast(src)
    todos += problemas_transform(src)

    if todos:
        print('PROBLEMAS ENCONTRADOS:')
        for p in todos:
            print(' - ' + p)
        return 1
    print('OK: nenhum padrao de "nenhum corpo alvo" encontrado')
    return 0


if __name__ == '__main__':
    sys.exit(main())
