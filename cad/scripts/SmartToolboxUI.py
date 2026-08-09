# Author: Gemini (revisão: correções geométricas e acabamento)

# Description: Smart Toolbox - Industrial Grade (revisado)
#
#   Base + Tampa com encaixe (lip/groove), bandeja modular com insertos M3,
#   zip-ties funcionais, dobradiça tipo piano (quinas + pino), alça na tampa,
#   pocket de ímã + Hall alinhados com a tampa fechada, recorte USB-C, pés TPU
#   e guia de luz.
#
#   Mudanças desta revisão:
#   - Dobradiças com eixo correto (∥ X) na aresta traseira: 4 quinas na base,
#     3 na tampa (intercaladas) + pino de 3.4 mm com cabeça (peça separada);
#   - Tampa posicionada FECHADA (z = gap) para alinhar quinas/ímã no mundo;
#   - Bandeja assentada no ledge interno (z = -tray_h), não mais flutuando;
#   - Zip-tie: ponte com slot de verdade atravessando a ponte (corte no plano yZ);
#   - Filetes de 1.5 mm apenas nas quinas externas (nada de cantos derretidos);
#   - Ímã (8 mm) em boss na base + pilar com pocket do A3144 na base e pocket
#     do ímã na tampa, alinhados (mesmo Y) com a tampa fechada;
#   - Alça na tampa (agora com pilares que alcançam a barra), pés TPU (4 peças)
#     e guia de luz com furo na tampa;
#   - Clipe de fecho frontal (tab + barb na base, trava na tampa) com notch
#     no lip; limitador de abertura (~100-115°) com asas + travas traseiras;
#   - Janela do LED (WS2812B), grade do buzzer, standoffs M3 para a PCB,
#     pocket do módulo RFID YRM100 e furo de passagem de cabos na bandeja;
#   - Validação de sobreposição (AABB) das features da bandeja com avisos;
#   - UI com presets (S/M/L), material (tolera o ajuste de folgas), min/max
#     nos campos, exportação automática de STL e visualização da tampa aberta;
#   - Unidades consistentes em cm; comentários corrigidos.
#
#   Unidades: cm (padrão do Fusion). Ex.: wall = 0.25 → 2.5 mm.

import adsk.core, adsk.fusion, traceback
import math

handlers = []

# ============================================================
# HELPERS DE GEOMETRIA
# ============================================================

def arredondar_quinas_verticais(comp, corpo, comprimento, largura, raio):
    """Filete só nas quinas verticais EXTERNAS do corpo (raio discreto)."""
    arestas = adsk.core.ObjectCollection.create()
    for edge in corpo.edges:
        p1 = edge.startVertex.geometry
        p2 = edge.endVertex.geometry
        # Aresta vertical (mesmo x,y, variando z)
        if (abs(p1.x - p2.x) < 0.01 and abs(p1.y - p2.y) < 0.01
                and abs(p1.z - p2.z) > 0.1):
            mx = (p1.x + p2.x) / 2.0
            my = (p1.y + p2.y) / 2.0
            # Só as arestas do perímetro externo (perto das bordas da caixa)
            if (mx < 0.08 or mx > comprimento - 0.08
                    or my < 0.08 or my > largura - 0.08):
                arestas.add(edge)
    if arestas.count > 0:
        try:
            fi = comp.features.filletFeatures.createInput()
            fi.addConstantRadiusEdgeSet(
                arestas, adsk.core.ValueInput.createByReal(raio), True)
            comp.features.filletFeatures.add(fi)
        except Exception:
            print("[TRAKR-CAD] Filete de quina falhou (ignorado)")


def pegar_anel(sketch):
    """Retorna o primeiro perfil em formato de anel (2 loops), se existir."""
    for p in sketch.profiles:
        if p.profileLoops.count == 2:
            return p
    return None


def criar_torres(comp, extrudes, pontos_xy, altura):
    """Torres com furo para inserto roscado M3 (heat-set)."""
    d_ext = 0.85   # boss de 8.5 mm (parede ~2 mm ao redor do inserto)
    d_int = 0.42   # furo de 4.2 mm p/ inserto M3 (press-fit)
    for px, py in pontos_xy:
        sk = comp.sketches.add(comp.xYConstructionPlane)
        sk.sketchCurves.sketchCircles.addByCenterRadius(
            adsk.core.Point3D.create(px, py, 0), d_ext / 2)
        sk.sketchCurves.sketchCircles.addByCenterRadius(
            adsk.core.Point3D.create(px, py, 0), d_int / 2)
        ring = pegar_anel(sk)
        if ring:
            inp = extrudes.createInput(
                ring, adsk.fusion.FeatureOperations.JoinFeatureOperation)
            inp.setDistanceExtent(False, adsk.core.ValueInput.createByReal(altura))
            extrudes.add(inp)


def criar_passa_cabo(comp, extrudes, bx, by):
    """Ponte para abraçadeira (zip-tie) com slot vertical atravessando."""
    sk = comp.sketches.add(comp.xYConstructionPlane)
    sk.sketchCurves.sketchLines.addTwoPointRectangle(
        adsk.core.Point3D.create(bx - 0.3, by - 0.2, 0),
        adsk.core.Point3D.create(bx + 0.3, by + 0.2, 0))
    inp = extrudes.createInput(
        sk.profiles.item(0), adsk.fusion.FeatureOperations.JoinFeatureOperation)
    inp.setDistanceExtent(False, adsk.core.ValueInput.createByReal(0.35))
    extrudes.add(inp)

    # Slot de 1.6 mm de largura (eixo X), vertical, atravessando a ponte
    sk_f = comp.sketches.add(comp.yZConstructionPlane)
    sk_f.sketchCurves.sketchLines.addTwoPointRectangle(
        adsk.core.Point3D.create(by - 0.12, -0.05, 0),
        adsk.core.Point3D.create(by + 0.12, 0.4, 0))
    ext_f = extrudes.createInput(
        sk_f.profiles.item(0), adsk.fusion.FeatureOperations.CutFeatureOperation)
    ext_f.setSymmetricExtent(adsk.core.ValueInput.createByReal(0.08), True)
    mat = adsk.core.Matrix3D.create()
    mat.translation = adsk.core.Vector3D.create(bx, 0, 0)
    ext_f.transform = mat
    extrudes.add(ext_f)


def criar_quinas(comp, extrudes, posicoes_x, largura_parede):
    """Quinas da dobradiça: cilindros anelares com eixo ∥ X na aresta traseira."""
    # Centro do barril: atrás da aresta traseira (y = largura + 0.15),
    # na altura do aro (z = 0.05) — mesmo local na base e na tampa.
    cy, cz = largura_parede + 0.15, 0.05
    for x in posicoes_x:
        sk = comp.sketches.add(comp.yZConstructionPlane)
        # Este sketch vive em x=0; o transform translada o anel até o x desejado
        sk.sketchCurves.sketchCircles.addByCenterRadius(
            adsk.core.Point3D.create(cy, cz, 0), 0.55)
        sk.sketchCurves.sketchCircles.addByCenterRadius(
            adsk.core.Point3D.create(cy, cz, 0), 0.18)
        ring = pegar_anel(sk)
        if not ring:
            continue
        inp = extrudes.createInput(
            ring, adsk.fusion.FeatureOperations.JoinFeatureOperation)
        inp.setSymmetricExtent(adsk.core.ValueInput.createByReal(0.45), True)
        mat = adsk.core.Matrix3D.create()
        mat.translation = adsk.core.Vector3D.create(x, 0, 0)
        inp.transform = mat
        extrudes.add(inp)


def criar_standoffs(comp, extrudes, pontos_xy, start_z, altura):
    """Bosses com furo guia para parafuso M3 (fixação da placa de controle)."""
    d_ext = 0.6   # boss de 6 mm
    d_int = 0.25  # furo de 2.5 mm (M3 auto-roscante)
    for px, py in pontos_xy:
        sk = comp.sketches.add(comp.xYConstructionPlane)
        sk.sketchCurves.sketchCircles.addByCenterRadius(
            adsk.core.Point3D.create(px, py, 0), d_ext / 2)
        sk.sketchCurves.sketchCircles.addByCenterRadius(
            adsk.core.Point3D.create(px, py, 0), d_int / 2)
        ring = pegar_anel(sk)
        if ring:
            inp = extrudes.createInput(
                ring, adsk.fusion.FeatureOperations.JoinFeatureOperation)
            inp.startExtent = adsk.fusion.OffsetStartDefinition.create(
                adsk.core.ValueInput.createByReal(start_z))
            inp.setDistanceExtent(False, adsk.core.ValueInput.createByReal(altura))
            extrudes.add(inp)


def sobrepoe_circulo_rect(cx, cy, r, x0, y0, x1, y1):
    """True se o círculo (torre) encosta no retângulo AABB."""
    nx = max(x0, min(cx, x1))
    ny = max(y0, min(cy, y1))
    return (cx - nx) ** 2 + (cy - ny) ** 2 < r * r


def verificar_sobreposicoes(torres, pontes, celulas, berco):
    """Validações AABB das features da bandeja; imprime avisos de colisão."""
    r_torre = 0.425
    issues = []
    for cx, cy in torres:
        for bx, by in pontes:
            if sobrepoe_circulo_rect(cx, cy, r_torre, bx - 0.3, by - 0.2, bx + 0.3, by + 0.2):
                issues.append('torre (%.2f,%.2f) sobrepõe ponte zip (%.2f,%.2f)' % (cx, cy, bx, by))
        for gx, gy in celulas:
            if sobrepoe_circulo_rect(cx, cy, r_torre, gx, gy, gx + 2.9, gy + 2.9):
                issues.append('torre (%.2f,%.2f) sobrepõe célula do grid (%.2f,%.2f)' % (cx, cy, gx, gy))
        if sobrepoe_circulo_rect(cx, cy, r_torre, berco[0] - 0.2, berco[1] - 0.2,
                                 berco[0] + 6.8, berco[1] + 2.1):
            issues.append('torre (%.2f,%.2f) sobrepõe berço da bateria' % (cx, cy))
    for bx, by in pontes:
        for gx, gy in celulas:
            if (bx - 0.3 < gx + 2.9 and bx + 0.3 > gx
                    and by - 0.2 < gy + 2.9 and by + 0.2 > gy):
                issues.append('ponte (%.2f,%.2f) sobrepõe célula do grid (%.2f,%.2f)' % (bx, by, gx, gy))
    for msg in issues:
        print('[TRAKR-CAD] ATENÇÃO: ' + msg)
    return len(issues) == 0


def exportar_stls(root, design):
    """Exporta cada corpo como STL em cad/exports (ao lado do script)."""
    import os
    try:
        pasta = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'exports')
        os.makedirs(pasta, exist_ok=True)
        em = design.exportManager
        for occ in root.occurrences:
            for body in occ.component.bRepBodies:
                nome = body.parentComponent.name.replace(' ', '_') + '.stl'
                opt = em.createSTLExportOptions(body, os.path.join(pasta, nome))
                opt.meshRefinement = adsk.fusion.MeshRefinementSettings.MeshRefinementMedium
                em.execute(opt)
                print('[TRAKR-CAD] STL exportado: ' + nome)
    except Exception:
        print('[TRAKR-CAD] Falha ao exportar STLs (ignorado)')


def simular_abertura(t_occ, comprimento, largura):
    """Gira a tampa 100° no eixo da dobradiça e valida as travas de abertura."""
    ang = math.radians(-100)
    eixo = adsk.core.Vector3D.create(1, 0, 0)
    origem = adsk.core.Point3D.create(0, largura + 0.15, 0.05)
    mat = adsk.core.Matrix3D.create()
    mat.setToRotation(ang, eixo, origem)
    t_occ.transform = mat

    # Asa da tampa (x 0.12L, y largura+0.05..+0.25, z -0.5..0) vs trava da base
    x = comprimento * 0.12
    pts = []
    for wx in [x - 0.35, x + 0.35]:
        for wy in [largura + 0.05, largura + 0.25]:
            for wz in [-0.5, 0.0]:
                p = adsk.core.Point3D.create(wx, wy, wz)
                p.transformBy(mat)
                pts.append((p.x, p.y, p.z))
    xs = [p[0] for p in pts]
    ys = [p[1] for p in pts]
    zs = [p[2] for p in pts]
    trava = (x - 0.35, largura - 0.45, 0.0, x + 0.35, largura - 0.25, 0.3)
    toca = (max(xs) > trava[0] and min(xs) < trava[3]
            and max(ys) > trava[1] and min(ys) < trava[4]
            and max(zs) > trava[2] and min(zs) < trava[5])
    if toca:
        print('[TRAKR-CAD] Trava de abertura ENGATA em ~100° (ok)')
    else:
        print('[TRAKR-CAD] ATENÇÃO: asa não alcança a trava em 100° — ajuste posições')


# ============================================================
# GERAÇÃO DA MALETA
# ============================================================

def gerar_maleta(length, width, base_depth, lid_depth, wall, acessorios,
                 material='PETG', abrir_tampa=False, exportar=True):
    app = adsk.core.Application.get()
    design = app.activeProduct
    root = design.rootComponent

    lip_h = 0.25     # altura do encaixe (lip) acima do aro
    lip_t = 0.20     # espessura do encaixe
    tol = MATERIAL_TOL.get(material, 0.025)   # folga de montagem (por material)
    tray_h = 1.7     # altura da bandeja (assenta no ledge em z=-tray_h)
    gap = 0.05       # folga visual base/tampa (tampa fechada)

    # ==========================================================
    # 1. BASE
    # ==========================================================
    b_occ = root.occurrences.addNewComponent(adsk.core.Matrix3D.create())
    b_comp = b_occ.component
    b_comp.name = "Maleta_Base"

    sk_b = b_comp.sketches.add(b_comp.xYConstructionPlane)
    sk_b.sketchCurves.sketchLines.addTwoPointRectangle(
        adsk.core.Point3D.create(0, 0, 0),
        adsk.core.Point3D.create(length, width, 0))
    ext_b = b_comp.features.extrudeFeatures.createInput(
        sk_b.profiles.item(0), adsk.fusion.FeatureOperations.NewBodyFeatureOperation)
    ext_b.setDistanceExtent(False, adsk.core.ValueInput.createByReal(-base_depth))
    body_b = b_comp.features.extrudeFeatures.add(ext_b).bodies.item(0)

    # Casca (parede + fundo)
    topFace = next((f for f in body_b.faces if abs(f.geometry.normal.z - 1.0) < 0.01), None)
    if topFace:
        sh_b = b_comp.features.shellFeatures.createInput(
            adsk.core.ObjectCollection.createWithArray([topFace]))
        sh_b.insideThickness = adsk.core.ValueInput.createByReal(wall)
        b_comp.features.shellFeatures.add(sh_b)
    arredondar_quinas_verticais(b_comp, body_b, length, width, 0.15)

    # Lip macho (encaixe) no aro
    sk_lip = b_comp.sketches.add(b_comp.xYConstructionPlane)
    sk_lip.sketchCurves.sketchLines.addTwoPointRectangle(
        adsk.core.Point3D.create(wall - lip_t, wall - lip_t, 0),
        adsk.core.Point3D.create(length - wall + lip_t, width - wall + lip_t, 0))
    sk_lip.sketchCurves.sketchLines.addTwoPointRectangle(
        adsk.core.Point3D.create(wall, wall, 0),
        adsk.core.Point3D.create(length - wall, width - wall, 0))
    p_lip = pegar_anel(sk_lip)
    if p_lip:
        e_lip = b_comp.features.extrudeFeatures.createInput(
            p_lip, adsk.fusion.FeatureOperations.JoinFeatureOperation)
        e_lip.setDistanceExtent(False, adsk.core.ValueInput.createByReal(lip_h))
        b_comp.features.extrudeFeatures.add(e_lip)

    # Notch no lip frontal (área do clipe): a trava da tampa encaixa aqui
    sk_notch = b_comp.sketches.add(b_comp.xYConstructionPlane)
    sk_notch.sketchCurves.sketchLines.addTwoPointRectangle(
        adsk.core.Point3D.create(length / 2 - 0.5, 0.02, 0),
        adsk.core.Point3D.create(length / 2 + 0.5, 0.3, 0))
    e_notch = b_comp.features.extrudeFeatures.createInput(
        sk_notch.profiles.item(0), adsk.fusion.FeatureOperations.CutFeatureOperation)
    e_notch.setDistanceExtent(False, adsk.core.ValueInput.createByReal(0.3))
    b_comp.features.extrudeFeatures.add(e_notch)

    # Clipe de fecho: tab flexível + barb, na parede frontal
    sk_latch = b_comp.sketches.add(b_comp.xYConstructionPlane)
    sk_latch.sketchCurves.sketchLines.addTwoPointRectangle(
        adsk.core.Point3D.create(length / 2 - 0.2, -0.15, 0),
        adsk.core.Point3D.create(length / 2 + 0.2, 0.1, 0))
    e_latch = b_comp.features.extrudeFeatures.createInput(
        sk_latch.profiles.item(0), adsk.fusion.FeatureOperations.JoinFeatureOperation)
    e_latch.setDistanceExtent(False, adsk.core.ValueInput.createByReal(0.7))
    b_comp.features.extrudeFeatures.add(e_latch)

    sk_barb = b_comp.sketches.add(b_comp.xYConstructionPlane)
    sk_barb.sketchCurves.sketchLines.addTwoPointRectangle(
        adsk.core.Point3D.create(length / 2 - 0.15, -0.05, 0),
        adsk.core.Point3D.create(length / 2 + 0.15, 0.25, 0))
    e_barb = b_comp.features.extrudeFeatures.createInput(
        sk_barb.profiles.item(0), adsk.fusion.FeatureOperations.JoinFeatureOperation)
    e_barb.startExtent = adsk.fusion.OffsetStartDefinition.create(
        adsk.core.ValueInput.createByReal(0.45))
    e_barb.setDistanceExtent(False, adsk.core.ValueInput.createByReal(0.25))
    b_comp.features.extrudeFeatures.add(e_barb)

    # Ledge interno (apoio da bandeja) em z = -tray_h
    sk_ledge = b_comp.sketches.add(b_comp.xYConstructionPlane)
    sk_ledge.sketchCurves.sketchLines.addTwoPointRectangle(
        adsk.core.Point3D.create(wall, wall, 0),
        adsk.core.Point3D.create(length - wall, width - wall, 0))
    sk_ledge.sketchCurves.sketchLines.addTwoPointRectangle(
        adsk.core.Point3D.create(wall + 0.4, wall + 0.4, 0),
        adsk.core.Point3D.create(length - wall - 0.4, width - wall - 0.4, 0))
    p_ledge = pegar_anel(sk_ledge)
    if p_ledge:
        e_ledge = b_comp.features.extrudeFeatures.createInput(
            p_ledge, adsk.fusion.FeatureOperations.JoinFeatureOperation)
        e_ledge.startExtent = adsk.fusion.OffsetStartDefinition.create(
            adsk.core.ValueInput.createByReal(-tray_h))
        e_ledge.setDistanceExtent(False, adsk.core.ValueInput.createByReal(-0.3))
        b_comp.features.extrudeFeatures.add(e_ledge)

    # Recorte USB-C na parede frontal (passa também pela bandeja)
    sk_usb = b_comp.sketches.add(b_comp.xYConstructionPlane)
    usb_x0 = length * 0.45
    sk_usb.sketchCurves.sketchLines.addTwoPointRectangle(
        adsk.core.Point3D.create(usb_x0, -0.2, 0),
        adsk.core.Point3D.create(usb_x0 + 1.3, wall + 0.2, 0))
    e_usb = b_comp.features.extrudeFeatures.createInput(
        sk_usb.profiles.item(0), adsk.fusion.FeatureOperations.CutFeatureOperation)
    e_usb.startExtent = adsk.fusion.OffsetStartDefinition.create(
        adsk.core.ValueInput.createByReal(-2.3))
    e_usb.setDistanceExtent(False, adsk.core.ValueInput.createByReal(0.6))
    b_comp.features.extrudeFeatures.add(e_usb)

    # Janela do LED (WS2812B, 5.5 x 5.5 mm) ao lado do USB-C
    sk_led = b_comp.sketches.add(b_comp.xYConstructionPlane)
    sk_led.sketchCurves.sketchLines.addTwoPointRectangle(
        adsk.core.Point3D.create(usb_x0 - 1.0, -0.2, 0),
        adsk.core.Point3D.create(usb_x0 - 0.45, wall + 0.2, 0))
    e_led = b_comp.features.extrudeFeatures.createInput(
        sk_led.profiles.item(0), adsk.fusion.FeatureOperations.CutFeatureOperation)
    e_led.startExtent = adsk.fusion.OffsetStartDefinition.create(
        adsk.core.ValueInput.createByReal(-2.3))
    e_led.setDistanceExtent(False, adsk.core.ValueInput.createByReal(0.6))
    b_comp.features.extrudeFeatures.add(e_led)

    # Grade do buzzer na parede esquerda (3 furos de 3 mm)
    sk_buzz = b_comp.sketches.add(b_comp.yZConstructionPlane)
    for bz in [-2.4, -2.8, -3.2]:
        sk_buzz.sketchCurves.sketchCircles.addByCenterRadius(
            adsk.core.Point3D.create(width / 2 - 1.0, bz, 0), 0.15)
    col_buzz = adsk.core.ObjectCollection.create()
    for p in sk_buzz.profiles:
        col_buzz.add(p)
    if col_buzz.count > 0:
        e_buzz = b_comp.features.extrudeFeatures.createInput(
            col_buzz, adsk.fusion.FeatureOperations.CutFeatureOperation)
        e_buzz.startExtent = adsk.fusion.OffsetStartDefinition.create(
            adsk.core.ValueInput.createByReal(0.0))
        e_buzz.setDistanceExtent(False, adsk.core.ValueInput.createByReal(-0.5))
        b_comp.features.extrudeFeatures.add(e_buzz)

    # Boss + pocket do ímã (8 x 3 mm) na parede frontal interna
    # (centro em y = wall + 0.35, alinhado com o Hall do pilar e o pocket da tampa)
    mag_y = wall + 0.35
    sk_mag = b_comp.sketches.add(b_comp.xYConstructionPlane)
    sk_mag.sketchCurves.sketchCircles.addByCenterRadius(
        adsk.core.Point3D.create(length / 2, mag_y, 0), 0.5)
    e_mag = b_comp.features.extrudeFeatures.createInput(
        sk_mag.profiles.item(0), adsk.fusion.FeatureOperations.JoinFeatureOperation)
    e_mag.startExtent = adsk.fusion.OffsetStartDefinition.create(
        adsk.core.ValueInput.createByReal(-0.8))
    e_mag.setDistanceExtent(False, adsk.core.ValueInput.createByReal(0.7))
    b_comp.features.extrudeFeatures.add(e_mag)

    sk_mag_hole = b_comp.sketches.add(b_comp.xYConstructionPlane)
    sk_mag_hole.sketchCurves.sketchCircles.addByCenterRadius(
        adsk.core.Point3D.create(length / 2, mag_y, 0), 0.42)
    e_mag_hole = b_comp.features.extrudeFeatures.createInput(
        sk_mag_hole.profiles.item(0), adsk.fusion.FeatureOperations.CutFeatureOperation)
    e_mag_hole.startExtent = adsk.fusion.OffsetStartDefinition.create(
        adsk.core.ValueInput.createByReal(-0.1))
    e_mag_hole.setDistanceExtent(False, adsk.core.ValueInput.createByReal(-0.35))
    b_comp.features.extrudeFeatures.add(e_mag_hole)

    # Pilar interno que sobe até a tampa (sensor Hall A3144 + fiação)
    sk_pil = b_comp.sketches.add(b_comp.xYConstructionPlane)
    sk_pil.sketchCurves.sketchLines.addTwoPointRectangle(
        adsk.core.Point3D.create(length / 2 - 0.45, wall, 0),
        adsk.core.Point3D.create(length / 2 + 0.45, wall + 0.7, 0))
    e_pil = b_comp.features.extrudeFeatures.createInput(
        sk_pil.profiles.item(0), adsk.fusion.FeatureOperations.JoinFeatureOperation)
    e_pil.startExtent = adsk.fusion.OffsetStartDefinition.create(
        adsk.core.ValueInput.createByReal(0.0))
    e_pil.setDistanceExtent(False, adsk.core.ValueInput.createByReal(2.4))
    b_comp.features.extrudeFeatures.add(e_pil)

    # Pocket do sensor no topo do pilar (4.1 x 3.1 mm do A3144 + folga)
    sk_hall = b_comp.sketches.add(b_comp.xYConstructionPlane)
    sk_hall.sketchCurves.sketchLines.addTwoPointRectangle(
        adsk.core.Point3D.create(length / 2 - 0.25, wall + 0.15, 0),
        adsk.core.Point3D.create(length / 2 + 0.25, wall + 0.55, 0))
    e_hall = b_comp.features.extrudeFeatures.createInput(
        sk_hall.profiles.item(0), adsk.fusion.FeatureOperations.CutFeatureOperation)
    e_hall.startExtent = adsk.fusion.OffsetStartDefinition.create(
        adsk.core.ValueInput.createByReal(2.4))
    e_hall.setDistanceExtent(False, adsk.core.ValueInput.createByReal(-0.3))
    b_comp.features.extrudeFeatures.add(e_hall)

    # Pockets dos pés TPU na base inferior
    sk_feet = b_comp.sketches.add(b_comp.xYConstructionPlane)
    fm = 1.5
    for fx, fy in [(fm, fm), (length - fm, fm), (fm, width - fm), (length - fm, width - fm)]:
        sk_feet.sketchCurves.sketchCircles.addByCenterRadius(
            adsk.core.Point3D.create(fx, fy, 0), 0.42)
    col_feet = adsk.core.ObjectCollection.create()
    for p in sk_feet.profiles:
        col_feet.add(p)
    if col_feet.count > 0:
        e_feet = b_comp.features.extrudeFeatures.createInput(
            col_feet, adsk.fusion.FeatureOperations.CutFeatureOperation)
        e_feet.startExtent = adsk.fusion.OffsetStartDefinition.create(
            adsk.core.ValueInput.createByReal(-base_depth + 0.05))
        e_feet.setDistanceExtent(False, adsk.core.ValueInput.createByReal(0.2))
        b_comp.features.extrudeFeatures.add(e_feet)

    # Pocket do módulo RFID YRM100 (placa 5.6 x 3.4 cm) no fundo da base
    sk_yr = b_comp.sketches.add(b_comp.xYConstructionPlane)
    sk_yr.sketchCurves.sketchLines.addTwoPointRectangle(
        adsk.core.Point3D.create(length / 2 - 2.8, width / 2 - 1.7, 0),
        adsk.core.Point3D.create(length / 2 + 2.8, width / 2 + 1.7, 0))
    e_yr = b_comp.features.extrudeFeatures.createInput(
        sk_yr.profiles.item(0), adsk.fusion.FeatureOperations.CutFeatureOperation)
    e_yr.startExtent = adsk.fusion.OffsetStartDefinition.create(
        adsk.core.ValueInput.createByReal(-base_depth + 0.25))
    e_yr.setDistanceExtent(False, adsk.core.ValueInput.createByReal(-0.2))
    b_comp.features.extrudeFeatures.add(e_yr)

    # Standoffs M3 para a placa de controle (z -4.0 até o fundo)
    criar_standoffs(b_comp, b_comp.features.extrudeFeatures,
                    [(length / 2 - 3.5, width / 2 - 2.25),
                     (length / 2 + 3.5, width / 2 - 2.25),
                     (length / 2 - 3.5, width / 2 + 2.25),
                     (length / 2 + 3.5, width / 2 + 2.25)], -4.0, -1.75)

    # Quinas da dobradiça (4 na base, eixo ∥ X na aresta traseira)
    criar_quinas(b_comp, b_comp.features.extrudeFeatures,
                 [length * 0.2, length * 0.4, length * 0.6, length * 0.8], width)

    # Travas de abertura (tampa para em ~100-115°), coladas no lip traseiro
    for sx in [length * 0.12, length * 0.88]:
        sk_stop = b_comp.sketches.add(b_comp.yZConstructionPlane)
        sk_stop.sketchCurves.sketchLines.addTwoPointRectangle(
            adsk.core.Point3D.create(width - 0.45, 0.0, 0),
            adsk.core.Point3D.create(width - 0.25, 0.3, 0))
        e_stop = b_comp.features.extrudeFeatures.createInput(
            sk_stop.profiles.item(0), adsk.fusion.FeatureOperations.JoinFeatureOperation)
        e_stop.setSymmetricExtent(adsk.core.ValueInput.createByReal(0.35), True)
        mat_s = adsk.core.Matrix3D.create()
        mat_s.translation = adsk.core.Vector3D.create(sx, 0, 0)
        e_stop.transform = mat_s
        b_comp.features.extrudeFeatures.add(e_stop)

    # ==========================================================
    # 2. TAMPA (posicionada FECHADA sobre a base — alinha quinas e ímã)
    # ==========================================================
    matrix_t = adsk.core.Matrix3D.create()
    matrix_t.translation = adsk.core.Vector3D.create(0, 0, gap)
    t_occ = root.occurrences.addNewComponent(matrix_t)
    t_comp = t_occ.component
    t_comp.name = "Maleta_Tampa"

    sk_t = t_comp.sketches.add(t_comp.xYConstructionPlane)
    sk_t.sketchCurves.sketchLines.addTwoPointRectangle(
        adsk.core.Point3D.create(0, 0, 0),
        adsk.core.Point3D.create(length, width, 0))
    ext_t = t_comp.features.extrudeFeatures.createInput(
        sk_t.profiles.item(0), adsk.fusion.FeatureOperations.NewBodyFeatureOperation)
    ext_t.setDistanceExtent(False, adsk.core.ValueInput.createByReal(lid_depth))
    body_t = t_comp.features.extrudeFeatures.add(ext_t).bodies.item(0)

    botFace = next((f for f in body_t.faces if abs(f.geometry.normal.z - (-1.0)) < 0.01), None)
    if botFace:
        sh_t = t_comp.features.shellFeatures.createInput(
            adsk.core.ObjectCollection.createWithArray([botFace]))
        sh_t.insideThickness = adsk.core.ValueInput.createByReal(wall)
        t_comp.features.shellFeatures.add(sh_t)
    arredondar_quinas_verticais(t_comp, body_t, length, width, 0.15)

    # Groove do encaixe (lip da base) com folga de montagem
    sk_gr = t_comp.sketches.add(t_comp.xYConstructionPlane)
    sk_gr.sketchCurves.sketchLines.addTwoPointRectangle(
        adsk.core.Point3D.create(wall - lip_t - tol, wall - lip_t - tol, 0),
        adsk.core.Point3D.create(length - wall + lip_t + tol, width - wall + lip_t + tol, 0))
    sk_gr.sketchCurves.sketchLines.addTwoPointRectangle(
        adsk.core.Point3D.create(wall + tol, wall + tol, 0),
        adsk.core.Point3D.create(length - wall - tol, width - wall - tol, 0))
    p_gr = pegar_anel(sk_gr)
    if p_gr:
        e_gr = t_comp.features.extrudeFeatures.createInput(
            p_gr, adsk.fusion.FeatureOperations.CutFeatureOperation)
        e_gr.setDistanceExtent(False, adsk.core.ValueInput.createByReal(lip_h + 0.05))
        t_comp.features.extrudeFeatures.add(e_gr)

    # Slot do clipe frontal (furo na parede por onde entra o tab da base)
    sk_lslot = t_comp.sketches.add(t_comp.xYConstructionPlane)
    sk_lslot.sketchCurves.sketchLines.addTwoPointRectangle(
        adsk.core.Point3D.create(length / 2 - 0.25, -0.2, 0),
        adsk.core.Point3D.create(length / 2 + 0.25, 0.45, 0))
    e_lslot = t_comp.features.extrudeFeatures.createInput(
        sk_lslot.profiles.item(0), adsk.fusion.FeatureOperations.CutFeatureOperation)
    e_lslot.setDistanceExtent(False, adsk.core.ValueInput.createByReal(0.75))
    t_comp.features.extrudeFeatures.add(e_lslot)

    # Trava (tooth) que prende sob o barb do clipe; ponteia o slot
    sk_tooth = t_comp.sketches.add(t_comp.xYConstructionPlane)
    sk_tooth.sketchCurves.sketchLines.addTwoPointRectangle(
        adsk.core.Point3D.create(length / 2 - 0.45, 0.1, 0),
        adsk.core.Point3D.create(length / 2 + 0.45, 0.4, 0))
    e_tooth = t_comp.features.extrudeFeatures.createInput(
        sk_tooth.profiles.item(0), adsk.fusion.FeatureOperations.JoinFeatureOperation)
    e_tooth.setDistanceExtent(False, adsk.core.ValueInput.createByReal(0.45))
    t_comp.features.extrudeFeatures.add(e_tooth)

    # Pocket do ímã na face interna da tampa (alinhado ao pilar do sensor)
    sk_mag_t = t_comp.sketches.add(t_comp.xYConstructionPlane)
    sk_mag_t.sketchCurves.sketchCircles.addByCenterRadius(
        adsk.core.Point3D.create(length / 2, wall + 0.35, 0), 0.42)
    e_mag_t = t_comp.features.extrudeFeatures.createInput(
        sk_mag_t.profiles.item(0), adsk.fusion.FeatureOperations.CutFeatureOperation)
    e_mag_t.startExtent = adsk.fusion.OffsetStartDefinition.create(
        adsk.core.ValueInput.createByReal(lid_depth - wall))
    e_mag_t.setDistanceExtent(False, adsk.core.ValueInput.createByReal(-0.35))
    t_comp.features.extrudeFeatures.add(e_mag_t)

    # Furo do guia de luz no topo da tampa (5 mm)
    sk_lp_hole = t_comp.sketches.add(t_comp.xYConstructionPlane)
    sk_lp_hole.sketchCurves.sketchCircles.addByCenterRadius(
        adsk.core.Point3D.create(length / 2, width / 2, 0), 0.25)
    e_lp_hole = t_comp.features.extrudeFeatures.createInput(
        sk_lp_hole.profiles.item(0), adsk.fusion.FeatureOperations.CutFeatureOperation)
    e_lp_hole.startExtent = adsk.fusion.OffsetStartDefinition.create(
        adsk.core.ValueInput.createByReal(lid_depth))
    e_lp_hole.setDistanceExtent(False, adsk.core.ValueInput.createByReal(-0.6))
    t_comp.features.extrudeFeatures.add(e_lp_hole)

    # Alça na tampa (2 pilares que alcançam a barra + barra)
    sk_ha = t_comp.sketches.add(t_comp.xYConstructionPlane)
    for hx in [length * 0.3, length * 0.7]:
        sk_ha.sketchCurves.sketchLines.addTwoPointRectangle(
            adsk.core.Point3D.create(hx - 0.5, width / 2 - 0.4, 0),
            adsk.core.Point3D.create(hx + 0.5, width / 2 + 0.4, 0))
    col_ha = adsk.core.ObjectCollection.create()
    for p in sk_ha.profiles:
        col_ha.add(p)
    if col_ha.count > 0:
        e_ha = t_comp.features.extrudeFeatures.createInput(
            col_ha, adsk.fusion.FeatureOperations.JoinFeatureOperation)
        e_ha.setDistanceExtent(False, adsk.core.ValueInput.createByReal(3.8))
        t_comp.features.extrudeFeatures.add(e_ha)

    sk_ha2 = t_comp.sketches.add(t_comp.xYConstructionPlane)
    sk_ha2.sketchCurves.sketchLines.addTwoPointRectangle(
        adsk.core.Point3D.create(length * 0.3 - 0.5, width / 2 - 0.9, 0),
        adsk.core.Point3D.create(length * 0.7 + 0.5, width / 2 + 0.9, 0))
    e_ha2 = t_comp.features.extrudeFeatures.createInput(
        sk_ha2.profiles.item(0), adsk.fusion.FeatureOperations.JoinFeatureOperation)
    e_ha2.startExtent = adsk.fusion.OffsetStartDefinition.create(
        adsk.core.ValueInput.createByReal(3.8))
    e_ha2.setDistanceExtent(False, adsk.core.ValueInput.createByReal(0.5))
    t_comp.features.extrudeFeatures.add(e_ha2)

    # Quinas da dobradiça (3 na tampa, intercaladas com as da base)
    criar_quinas(t_comp, t_comp.features.extrudeFeatures,
                 [length * 0.3, length * 0.5, length * 0.7], width)

    # Asas do limitador de abertura (batem nas travas da base, ~100-115°)
    for wx in [length * 0.12, length * 0.88]:
        sk_wing = t_comp.sketches.add(t_comp.yZConstructionPlane)
        sk_wing.sketchCurves.sketchLines.addTwoPointRectangle(
            adsk.core.Point3D.create(width + 0.05, -0.5, 0),
            adsk.core.Point3D.create(width + 0.25, 0.0, 0))
        e_wing = t_comp.features.extrudeFeatures.createInput(
            sk_wing.profiles.item(0), adsk.fusion.FeatureOperations.JoinFeatureOperation)
        e_wing.setSymmetricExtent(adsk.core.ValueInput.createByReal(0.35), True)
        mat_w = adsk.core.Matrix3D.create()
        mat_w.translation = adsk.core.Vector3D.create(wx, 0, 0)
        e_wing.transform = mat_w
        t_comp.features.extrudeFeatures.add(e_wing)

    # ==========================================================
    # 3. BANDEJA MODULAR (assentada no ledge interno)
    # ==========================================================
    tray_occ = root.occurrences.addNewComponent(adsk.core.Matrix3D.create())
    tray_comp = tray_occ.component
    tray_comp.name = "Maleta_Bandeja"

    t_len = length - (wall * 2) - (tol * 2)
    t_wid = width - (wall * 2) - (tol * 2)

    # Bateria 18650 (dimensões do berço)
    bat_len, bat_w = 6.6, 1.9

    # Listas de features (geração e validação de overlap usam as mesmas)
    torres_g1 = [(1.0, t_wid / 2 - 1.1), (5.7, t_wid / 2 - 1.1),
                 (1.0, t_wid / 2 + 1.15), (5.7, t_wid / 2 + 1.15)]
    torres_g2 = [(t_len - 3.5, t_wid / 2 - 0.7), (t_len - 1.3, t_wid / 2 - 0.7),
                 (t_len - 3.5, t_wid / 2 + 0.7), (t_len - 1.3, t_wid / 2 + 0.7)]
    pontes = [(0.8, t_wid / 2), (t_len - 0.8, t_wid / 2)]
    celulas = [(1.5, 1.5), (1.5, 4.7)]
    berco = (t_len / 2 - bat_len / 2, t_wid - bat_w - 0.5)
    verificar_sobreposicoes(torres_g1 + torres_g2, pontes, celulas, berco)

    sk_tray = tray_comp.sketches.add(tray_comp.xYConstructionPlane)
    sk_tray.sketchCurves.sketchLines.addTwoPointRectangle(
        adsk.core.Point3D.create(0, 0, 0),
        adsk.core.Point3D.create(t_len, t_wid, 0))
    ext_tr = tray_comp.features.extrudeFeatures.createInput(
        sk_tray.profiles.item(0), adsk.fusion.FeatureOperations.NewBodyFeatureOperation)
    ext_tr.setDistanceExtent(False, adsk.core.ValueInput.createByReal(tray_h))
    body_tr = tray_comp.features.extrudeFeatures.add(ext_tr).bodies.item(0)

    t_f = next((f for f in body_tr.faces if abs(f.geometry.normal.z - 1.0) < 0.01), None)
    if t_f:
        sh_tr = tray_comp.features.shellFeatures.createInput(
            adsk.core.ObjectCollection.createWithArray([t_f]))
        sh_tr.insideThickness = adsk.core.ValueInput.createByReal(wall)
        tray_comp.features.shellFeatures.add(sh_tr)
    arredondar_quinas_verticais(tray_comp, body_tr, t_len, t_wid, 0.1)

    # Torres p/ insertos M3
    criar_torres(tray_comp, tray_comp.features.extrudeFeatures, torres_g1, 0.4)
    criar_torres(tray_comp, tray_comp.features.extrudeFeatures, torres_g2, 0.4)

    # Pontes p/ zip-ties (com slot real), perto das paredes laterais
    for bx, by in pontes:
        criar_passa_cabo(tray_comp, tray_comp.features.extrudeFeatures, bx, by)

    # Grid de armazenamento (bolsos no piso, sem sobrepor torres)
    sk_grid = tray_comp.sketches.add(tray_comp.xYConstructionPlane)
    for gx, gy in celulas:
        sk_grid.sketchCurves.sketchLines.addTwoPointRectangle(
            adsk.core.Point3D.create(gx, gy, 0),
            adsk.core.Point3D.create(gx + 2.9, gy + 2.9, 0))
    col_grid = adsk.core.ObjectCollection.create()
    for p in sk_grid.profiles:
        col_grid.add(p)
    if col_grid.count > 0:
        ext_grid = tray_comp.features.extrudeFeatures.createInput(
            col_grid, adsk.fusion.FeatureOperations.CutFeatureOperation)
        ext_grid.setDistanceExtent(False, adsk.core.ValueInput.createByReal(-1.2))
        tray_comp.features.extrudeFeatures.add(ext_grid)

    # Recorte USB-C alinhado com o da base (mesmo X em coordenadas de mundo)
    sk_usb_tr = tray_comp.sketches.add(tray_comp.xYConstructionPlane)
    usb_tr_x0 = usb_x0 - (wall + tol)
    sk_usb_tr.sketchCurves.sketchLines.addTwoPointRectangle(
        adsk.core.Point3D.create(usb_tr_x0, -0.2, 0),
        adsk.core.Point3D.create(usb_tr_x0 + 1.3, wall + 0.2, 0))
    e_usb_tr = tray_comp.features.extrudeFeatures.createInput(
        sk_usb_tr.profiles.item(0), adsk.fusion.FeatureOperations.CutFeatureOperation)
    e_usb_tr.setDistanceExtent(False, adsk.core.ValueInput.createByReal(-0.5))
    tray_comp.features.extrudeFeatures.add(e_usb_tr)

    # Berço da bateria 18650 (U: paredes laterais + traseira, aberto na frente)
    cradle_x, cradle_y = berco
    cradle_h = 1.2
    sk_cradle = tray_comp.sketches.add(tray_comp.xYConstructionPlane)
    sk_cradle.sketchCurves.sketchLines.addTwoPointRectangle(
        adsk.core.Point3D.create(cradle_x - 0.2, cradle_y - 0.2, 0),
        adsk.core.Point3D.create(cradle_x + bat_len + 0.2, cradle_y + bat_w + 0.2, 0))
    sk_cradle.sketchCurves.sketchLines.addTwoPointRectangle(
        adsk.core.Point3D.create(cradle_x, cradle_y - 0.19, 0),
        adsk.core.Point3D.create(cradle_x + bat_len, cradle_y + bat_w - 0.1, 0))
    p_cradle = pegar_anel(sk_cradle)
    if p_cradle:
        e_cradle = tray_comp.features.extrudeFeatures.createInput(
            p_cradle, adsk.fusion.FeatureOperations.JoinFeatureOperation)
        e_cradle.setDistanceExtent(False, adsk.core.ValueInput.createByReal(cradle_h))
        tray_comp.features.extrudeFeatures.add(e_cradle)

    # Saídas de cabo laterais no berço
    sk_cables = tray_comp.sketches.add(tray_comp.xYConstructionPlane)
    sk_cables.sketchCurves.sketchLines.addTwoPointRectangle(
        adsk.core.Point3D.create(cradle_x - 0.3, cradle_y + bat_w / 2 - 0.2, 0),
        adsk.core.Point3D.create(cradle_x + 0.1, cradle_y + bat_w / 2 + 0.2, 0))
    sk_cables.sketchCurves.sketchLines.addTwoPointRectangle(
        adsk.core.Point3D.create(cradle_x + bat_len - 0.1, cradle_y + bat_w / 2 - 0.2, 0),
        adsk.core.Point3D.create(cradle_x + bat_len + 0.3, cradle_y + bat_w / 2 + 0.2, 0))
    col_cables = adsk.core.ObjectCollection.create()
    for p in sk_cables.profiles:
        col_cables.add(p)
    if col_cables.count > 0:
        e_cables = tray_comp.features.extrudeFeatures.createInput(
            col_cables, adsk.fusion.FeatureOperations.CutFeatureOperation)
        e_cables.setDistanceExtent(False, adsk.core.ValueInput.createByReal(-1.2))
        tray_comp.features.extrudeFeatures.add(e_cables)

    # Furo de passagem de cabos no piso, na frente do berço (para a placa)
    sk_pass = tray_comp.sketches.add(tray_comp.xYConstructionPlane)
    sk_pass.sketchCurves.sketchCircles.addByCenterRadius(
        adsk.core.Point3D.create(cradle_x + bat_len / 2, cradle_y - 0.7, 0), 0.25)
    e_pass = tray_comp.features.extrudeFeatures.createInput(
        sk_pass.profiles.item(0), adsk.fusion.FeatureOperations.CutFeatureOperation)
    e_pass.setDistanceExtent(False, adsk.core.ValueInput.createByReal(-1.75))
    tray_comp.features.extrudeFeatures.add(e_pass)

    # Bandeja assentada no ledge: topo na altura do aro, fundo em z=-tray_h
    transform_tr = adsk.core.Matrix3D.create()
    transform_tr.translation = adsk.core.Vector3D.create(wall + tol, wall + tol, -tray_h)
    tray_occ.transform = transform_tr

    # ==========================================================
    # 4. ACESSÓRIOS (pino, pés TPU, guia de luz)
    # ==========================================================
    if acessorios:
        # Pino da dobradiça (3.4 mm, percorre todas as quinas)
        pin_occ = root.occurrences.addNewComponent(adsk.core.Matrix3D.create())
        pin_comp = pin_occ.component
        pin_comp.name = "Maleta_Pino_Dobradica"
        sk_pin = pin_comp.sketches.add(pin_comp.yZConstructionPlane)
        sk_pin.sketchCurves.sketchCircles.addByCenterRadius(
            adsk.core.Point3D.create(width + 0.15, 0.05, 0), 0.17)
        e_pin = pin_comp.features.extrudeFeatures.createInput(
            sk_pin.profiles.item(0), adsk.fusion.FeatureOperations.NewBodyFeatureOperation)
        e_pin.setSymmetricExtent(adsk.core.ValueInput.createByReal(length * 0.35), True)
        mat_pin = adsk.core.Matrix3D.create()
        mat_pin.translation = adsk.core.Vector3D.create(length * 0.5, 0, 0)
        e_pin.transform = mat_pin
        pin_comp.features.extrudeFeatures.add(e_pin)

        # Cabeça do pino (r 3 mm) na ponta em x = 0.15L, retém o pino
        sk_pinh = pin_comp.sketches.add(pin_comp.yZConstructionPlane)
        sk_pinh.sketchCurves.sketchCircles.addByCenterRadius(
            adsk.core.Point3D.create(width + 0.15, 0.05, 0), 0.3)
        e_pinh = pin_comp.features.extrudeFeatures.createInput(
            sk_pinh.profiles.item(0), adsk.fusion.FeatureOperations.JoinFeatureOperation)
        e_pinh.setDistanceExtent(False, adsk.core.ValueInput.createByReal(-0.2))
        mat_pinh = adsk.core.Matrix3D.create()
        mat_pinh.translation = adsk.core.Vector3D.create(length * 0.15 + 0.2, 0, 0)
        e_pinh.transform = mat_pinh
        pin_comp.features.extrudeFeatures.add(e_pinh)

        # Pés TPU (4 cilindros, encaixam nos pockets da base — fundo rente)
        feet_occ = root.occurrences.addNewComponent(adsk.core.Matrix3D.create())
        feet_comp = feet_occ.component
        feet_comp.name = "Maleta_Pes_TPU"
        sk_ft = feet_comp.sketches.add(feet_comp.xYConstructionPlane)
        for fx, fy in [(fm, fm), (length - fm, fm), (fm, width - fm), (length - fm, width - fm)]:
            sk_ft.sketchCurves.sketchCircles.addByCenterRadius(
                adsk.core.Point3D.create(fx, fy, 0), 0.4)
        col_ft = adsk.core.ObjectCollection.create()
        for p in sk_ft.profiles:
            col_ft.add(p)
        if col_ft.count > 0:
            e_ft = feet_comp.features.extrudeFeatures.createInput(
                col_ft, adsk.fusion.FeatureOperations.NewBodyFeatureOperation)
            e_ft.setDistanceExtent(False, adsk.core.ValueInput.createByReal(0.5))
            feet_comp.features.extrudeFeatures.add(e_ft)
        mat_ft = adsk.core.Matrix3D.create()
        mat_ft.translation = adsk.core.Vector3D.create(0, 0, -base_depth)
        feet_occ.transform = mat_ft

        # Guia de luz (cilindro + flange), posicionado ao lado p/ impressão
        lp_occ = root.occurrences.addNewComponent(adsk.core.Matrix3D.create())
        lp_comp = lp_occ.component
        lp_comp.name = "Maleta_GuiaLuz_LED"
        sk_lp = lp_comp.sketches.add(lp_comp.xYConstructionPlane)
        sk_lp.sketchCurves.sketchCircles.addByCenterRadius(
            adsk.core.Point3D.create(0, 0, 0), 0.22)
        ext_lp = lp_comp.features.extrudeFeatures.createInput(
            sk_lp.profiles.item(0), adsk.fusion.FeatureOperations.NewBodyFeatureOperation)
        ext_lp.setDistanceExtent(False, adsk.core.ValueInput.createByReal(0.9))
        lp_comp.features.extrudeFeatures.add(ext_lp)
        sk_fl = lp_comp.sketches.add(lp_comp.xYConstructionPlane)
        sk_fl.sketchCurves.sketchCircles.addByCenterRadius(
            adsk.core.Point3D.create(0, 0, 0), 0.35)
        ext_fl = lp_comp.features.extrudeFeatures.createInput(
            sk_fl.profiles.item(0), adsk.fusion.FeatureOperations.JoinFeatureOperation)
        ext_fl.setDistanceExtent(False, adsk.core.ValueInput.createByReal(0.12))
        lp_comp.features.extrudeFeatures.add(ext_fl)
        mat_lp = adsk.core.Matrix3D.create()
        mat_lp.translation = adsk.core.Vector3D.create(length - 3.5, width / 2, 0)
        lp_occ.transform = mat_lp

    # Exportação automática (antes de girar a tampa) e visualização aberta
    if exportar:
        exportar_stls(root, design)
    if abrir_tampa:
        simular_abertura(t_occ, length, width)


# ============================================================
# INTERFACE DE USUÁRIO (MENU NATIVO)
# ============================================================

PRESETS = {
    'S (pequena)': (18.0, 8.0, 4.5, 2.5, 0.25),
    'M (média)': (24.0, 11.0, 6.0, 3.0, 0.25),
    'L (grande)': (32.0, 15.0, 8.0, 4.0, 0.3),
}

MATERIAL_TOL = {'PETG': 0.025, 'ABS': 0.03, 'PLA': 0.02, 'TPU': 0.03}


class ToolboxCommandExecuteHandler(adsk.core.CommandEventHandler):
    def __init__(self):
        super().__init__()

    def notify(self, args):
        try:
            inputs = args.firingEvent.sender.commandInputs
            length = inputs.itemById('length').value
            width = inputs.itemById('width').value
            base_depth = inputs.itemById('base_depth').value
            lid_depth = inputs.itemById('lid_depth').value
            wall = inputs.itemById('wall').value
            acessorios = inputs.itemById('acessorios').value
            material = inputs.itemById('material').selectedItem.name
            exportar = inputs.itemById('exportar').value
            abrir_tampa = inputs.itemById('abrir_tampa').value
            gerar_maleta(length, width, base_depth, lid_depth, wall, acessorios,
                         material, abrir_tampa, exportar)
        except:
            app = adsk.core.Application.get()
            app.userInterface.messageBox(traceback.format_exc())


class ToolboxCommandChangedHandler(adsk.core.InputChangedEventHandler):
    def __init__(self):
        super().__init__()

    def notify(self, args):
        try:
            if args.input.id == 'preset':
                nome = args.input.selectedItem.name
                if nome in PRESETS:
                    vals = PRESETS[nome]
                    inputs = args.inputs
                    inputs.itemById('length').value = vals[0]
                    inputs.itemById('width').value = vals[1]
                    inputs.itemById('base_depth').value = vals[2]
                    inputs.itemById('lid_depth').value = vals[3]
                    inputs.itemById('wall').value = vals[4]
        except:
            adsk.core.Application.get().userInterface.messageBox(traceback.format_exc())


class ToolboxCommandDestroyHandler(adsk.core.CommandEventHandler):
    def __init__(self):
        super().__init__()

    def notify(self, args):
        adsk.terminate()


class ToolboxCommandCreatedHandler(adsk.core.CommandCreatedEventHandler):
    def __init__(self):
        super().__init__()

    def notify(self, args):
        try:
            cmd = args.command

            onExecute = ToolboxCommandExecuteHandler()
            cmd.execute.add(onExecute)
            handlers.append(onExecute)

            onDestroy = ToolboxCommandDestroyHandler()
            cmd.destroy.add(onDestroy)
            handlers.append(onDestroy)

            onChanged = ToolboxCommandChangedHandler()
            cmd.inputChanged.add(onChanged)
            handlers.append(onChanged)

            inputs = cmd.commandInputs
            preset = inputs.addDropDownCommandInput('preset', 'Preset', 0)
            preset.listItems.add('M (média)', True)
            preset.listItems.add('S (pequena)', False)
            preset.listItems.add('L (grande)', False)
            inputs.addSeparatorCommandInput('sep1')

            v_len = inputs.addValueInput('length', 'Comprimento (cm)', 'cm',
                                         adsk.core.ValueInput.createByReal(24.0))
            v_len.minimum = 10.0
            v_len.maximum = 60.0
            v_wid = inputs.addValueInput('width', 'Largura (cm)', 'cm',
                                         adsk.core.ValueInput.createByReal(11.0))
            v_wid.minimum = 6.0
            v_wid.maximum = 30.0
            v_bd = inputs.addValueInput('base_depth', 'Profundidade da Base (cm)', 'cm',
                                        adsk.core.ValueInput.createByReal(6.0))
            v_bd.minimum = 3.0
            v_bd.maximum = 15.0
            v_ld = inputs.addValueInput('lid_depth', 'Profundidade da Tampa (cm)', 'cm',
                                        adsk.core.ValueInput.createByReal(3.0))
            v_ld.minimum = 1.5
            v_ld.maximum = 10.0
            v_wall = inputs.addValueInput('wall', 'Espessura da Parede (cm)', 'cm',
                                          adsk.core.ValueInput.createByReal(0.25))
            v_wall.minimum = 0.15
            v_wall.maximum = 0.6

            material = inputs.addDropDownCommandInput('material', 'Material', 0)
            material.listItems.add('PETG', True)
            material.listItems.add('ABS', False)
            material.listItems.add('PLA', False)
            material.listItems.add('TPU', False)

            inputs.addBoolValueInput('acessorios', 'Gerar acessórios '
                                     '(pino, pés TPU, guia de luz)', True, True)
            inputs.addBoolValueInput('exportar', 'Exportar STL após gerar', True, True)
            inputs.addBoolValueInput('abrir_tampa', 'Visualizar tampa aberta (100°)',
                                     True, False)
        except:
            app = adsk.core.Application.get()
            app.userInterface.messageBox(traceback.format_exc())


def run(context):
    try:
        app = adsk.core.Application.get()
        ui = app.userInterface

        cmdDef = ui.commandDefinitions.itemById('SmartToolboxUI')
        if not cmdDef:
            cmdDef = ui.commandDefinitions.addButtonDefinition(
                'SmartToolboxUI', 'Configurar Maleta 3D', 'Gera a maleta com UI')

        onCommandCreated = ToolboxCommandCreatedHandler()
        cmdDef.commandCreated.add(onCommandCreated)
        handlers.append(onCommandCreated)

        cmdDef.execute()
        adsk.autoTerminate(False)
    except:
        if ui:
            ui.messageBox('Erro Inicial:\n{}'.format(traceback.format_exc()))