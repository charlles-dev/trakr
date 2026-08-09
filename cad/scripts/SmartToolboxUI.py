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
#     3 na tampa (intercaladas) + pino de 3.4 mm gerado como peça separada;
#   - Tampa posicionada FECHADA (z = gap) para alinhar quinas/ímã no mundo;
#   - Bandeja assentada no ledge interno (z = -tray_h), não mais flutuando;
#   - Zip-tie: ponte com slot de verdade atravessando a ponte (corte no plano yZ);
#   - Filetes de 1.5 mm apenas nas quinas externas (nada de cantos derretidos);
#   - Ímã (8 mm) em boss na base + pilar com pocket do A3144 na base e pocket
#     do ímã na tampa, alinhados com a tampa fechada;
#   - Alça na tampa, pés TPU (4 peças) e guia de luz com furo na tampa;
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


def criar_quinas(comp, extrudes, comprimento, posicoes_x, largura_parede):
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


# ============================================================
# GERAÇÃO DA MALETA
# ============================================================

def gerar_maleta(length, width, base_depth, lid_depth, wall, acessorios):
    app = adsk.core.Application.get()
    design = app.activeProduct
    root = design.rootComponent

    lip_h = 0.25     # altura do encaixe (lip) acima do aro
    lip_t = 0.20     # espessura do encaixe
    tol = 0.025      # folga de montagem
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

    # Boss + pocket do ímã (8 x 3 mm) na parede frontal interna
    sk_mag = b_comp.sketches.add(b_comp.xYConstructionPlane)
    sk_mag.sketchCurves.sketchCircles.addByCenterRadius(
        adsk.core.Point3D.create(length / 2, wall, 0), 0.5)
    e_mag = b_comp.features.extrudeFeatures.createInput(
        sk_mag.profiles.item(0), adsk.fusion.FeatureOperations.JoinFeatureOperation)
    e_mag.startExtent = adsk.fusion.OffsetStartDefinition.create(
        adsk.core.ValueInput.createByReal(-0.8))
    e_mag.setDistanceExtent(False, adsk.core.ValueInput.createByReal(0.7))
    b_comp.features.extrudeFeatures.add(e_mag)

    sk_mag_hole = b_comp.sketches.add(b_comp.xYConstructionPlane)
    sk_mag_hole.sketchCurves.sketchCircles.addByCenterRadius(
        adsk.core.Point3D.create(length / 2, wall, 0), 0.42)
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

    # Quinas da dobradiça (4 na base, eixo ∥ X na aresta traseira)
    criar_quinas(b_comp, b_comp.features.extrudeFeatures, length,
                 [length * 0.2, length * 0.4, length * 0.6, length * 0.8], width)

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

    # Alça na tampa (2 pilares + barra)
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
        e_ha.setDistanceExtent(False, adsk.core.ValueInput.createByReal(1.6))
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
    criar_quinas(t_comp, t_comp.features.extrudeFeatures, length,
                 [length * 0.3, length * 0.5, length * 0.7], width)

    # ==========================================================
    # 3. BANDEJA MODULAR (assentada no ledge interno)
    # ==========================================================
    tray_occ = root.occurrences.addNewComponent(adsk.core.Matrix3D.create())
    tray_comp = tray_occ.component
    tray_comp.name = "Maleta_Bandeja"

    t_len = length - (wall * 2) - (tol * 2)
    t_wid = width - (wall * 2) - (tol * 2)

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
    criar_torres(tray_comp, tray_comp.features.extrudeFeatures,
                 [(1.0, t_wid / 2 - 1.1), (5.7, t_wid / 2 - 1.1),
                  (1.0, t_wid / 2 + 1.15), (5.7, t_wid / 2 + 1.15)], 0.4)
    criar_torres(tray_comp, tray_comp.features.extrudeFeatures,
                 [(t_len - 3.5, t_wid / 2 - 0.7), (t_len - 1.3, t_wid / 2 - 0.7),
                  (t_len - 3.5, t_wid / 2 + 0.7), (t_len - 1.3, t_wid / 2 + 0.7)], 0.4)

    # Pontes p/ zip-ties (com slot real), entre as colunas de torres
    criar_passa_cabo(tray_comp, tray_comp.features.extrudeFeatures, 3.4, t_wid / 2)
    criar_passa_cabo(tray_comp, tray_comp.features.extrudeFeatures, t_len - 3.4, t_wid / 2)

    # Grid de armazenamento (bolsos no piso, sem sobrepor torres)
    sk_grid = tray_comp.sketches.add(tray_comp.xYConstructionPlane)
    g_size = 2.9
    for gx, gy in [(1.5, 1.5), (1.5, 4.7)]:
        sk_grid.sketchCurves.sketchLines.addTwoPointRectangle(
            adsk.core.Point3D.create(gx, gy, 0),
            adsk.core.Point3D.create(gx + g_size, gy + g_size, 0))
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
    bat_len, bat_w = 6.6, 1.9
    cradle_x = t_len / 2 - bat_len / 2
    cradle_y = t_wid - bat_w - 0.5
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

    # Bandeja assentada no ledge: topo na altura do aro, fundo em z=-tray_h
    transform_tr = adsk.core.Matrix3D.create()
    transform_tr.translation = adsk.core.Vector3D.create(wall + tol, wall + tol, -tray_h)
    tray_occ.transform = transform_tr

    # ==========================================================
    # 4. ACESSÓRIOS (pino, pés TPU, guia de luz)
    # ==========================================================
    if not acessorios:
        return

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


# ============================================================
# INTERFACE DE USUÁRIO (MENU NATIVO)
# ============================================================

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
            gerar_maleta(length, width, base_depth, lid_depth, wall, acessorios)
        except:
            app = adsk.core.Application.get()
            app.userInterface.messageBox(traceback.format_exc())


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

            inputs = cmd.commandInputs
            inputs.addValueInput('length', 'Comprimento (cm)', 'cm',
                                 adsk.core.ValueInput.createByReal(24.0))
            inputs.addValueInput('width', 'Largura (cm)', 'cm',
                                 adsk.core.ValueInput.createByReal(11.0))
            inputs.addValueInput('base_depth', 'Profundidade da Base (cm)', 'cm',
                                 adsk.core.ValueInput.createByReal(6.0))
            inputs.addValueInput('lid_depth', 'Profundidade da Tampa (cm)', 'cm',
                                 adsk.core.ValueInput.createByReal(3.0))
            inputs.addValueInput('wall', 'Espessura da Parede (cm)', 'cm',
                                 adsk.core.ValueInput.createByReal(0.25))
            inputs.addBoolValueInput('acessorios', 'Gerar acessórios '
                                     '(pino, pés TPU, guia de luz)', True, True)
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