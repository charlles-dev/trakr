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
4. Colecoes de multiplos perfis (ObjectCollection) em extrusoes de corte/join —
   cada corpo alvo e procurado na posicao do perfil e a colecao falha com
   "Nenhum corpo alvo encontrado" (validado no Fusion: so extrusoes de perfil
   unico funcionaram).
5. shellFeatures — o lado fechado da casca ficava fino demais (validado na
   base: pockets dos pes perdiam o corpo alvo). Use a caixa interna explicita
   (retangulo interno + CutFeatureOperation).

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
                elif (isinstance(arg, ast.UnaryOp) and isinstance(arg.op, ast.USub)
                        and isinstance(arg.operand, ast.Name)):
                    # setDistanceExtent(False, -variavel): a distancia deve ser
                    # positiva — o sinal negativo com False extrui para +z
                    # (a base inteira saiu para cima e os cortes abaixo do
                    # aro perderam o corpo alvo)
                    problemas.append(
                        'L%d: setDistanceExtent(False, -%s) — distancia '
                        'negativa; use setDistanceExtent(True, %s) para '
                        'extruir para -z' % (node.lineno, arg.operand.id,
                                             arg.operand.id))
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
        cuts = set(re.findall(r'(?m)^\s*(\w+)\s*=\s*[\w.]+\.extrudeFeatures\.createInput\('
                              r'[^)]*CutFeatureOperation', bloco))
        var_transform = set(re.findall(r'(?m)^\s*(\w+)\.transform\s*=', bloco))
        for v in sorted(cuts & var_transform):
            problemas.append(
                'def %s(): corte "%s" usa transform — o corpo alvo e '
                'procurado na posicao do perfil; use plano de construcao '
                'offset no lugar' % (nome_funcao, v))
    return problemas


def problemas_colecao(src):
    """`ObjectCollection` de perfis passada ao createInput de extrude de corte
    ou join — colecoes de multiplos perfis falham com "nenhum corpo alvo".
    NewBody nao precisa de corpo alvo e fica permitida (preserva 1 corpo/1 STL)."""
    problemas = []
    escopos = re.split(r'(?m)^def ', src)
    for bloco in escopos[1:]:
        nome_funcao = bloco.split('(')[0].strip()
        for m in re.finditer(
                r'(?m)^\s*(\w+)\s*=\s*[\w.]+\.extrudeFeatures\.createInput\('
                r'\s*(\w+)\s*,[^)]*(CutFeatureOperation|JoinFeatureOperation)',
                bloco):
            var_in, var_perfis = m.group(1), m.group(2)
            definido_colecao = re.search(
                r'(?m)^\s*' + re.escape(var_perfis) + r'\s*=\s*'
                r'adsk\.core\.ObjectCollection\.create\(\)', bloco)
            if definido_colecao:
                problemas.append(
                    'def %s(): extrude "%s" usa a colecao de perfis "%s" — '
                    'falha com "nenhum corpo alvo"; faca um extrude por perfil'
                    % (nome_funcao, var_in, var_perfis))
    return problemas


def problemas_shell(src):
    """shellFeatures — o lado fechado da casca fica fino demais para cortes."""
    problemas = []
    for m in re.finditer(r'(?m)^.*shellFeatures\.add\(.*', src):
        problemas.append(
            'L%d: shellFeatures — o lado fechado da casca fica fino demais '
            'para cortes; use a caixa interna explicita' % src[:m.start()].count('\n') + 1)
    return problemas


def main():
    caminho = sys.argv[1] if len(sys.argv) > 1 else os.path.join(
        os.path.dirname(os.path.abspath(__file__)), 'SmartToolboxUI.py')
    with open(caminho, encoding='utf-8') as f:
        src = f.read()

    todos = []
    todos += problemas_ast(src)
    todos += problemas_transform(src)
    todos += problemas_colecao(src)
    todos += problemas_shell(src)

    if todos:
        print('PROBLEMAS ENCONTRADOS:')
        for p in todos:
            print(' - ' + p)
        return 1
    print('OK: nenhum padrao de "nenhum corpo alvo" encontrado')
    return 0


if __name__ == '__main__':
    sys.exit(main())
