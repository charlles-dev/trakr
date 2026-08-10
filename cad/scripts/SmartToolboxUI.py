# SmartToolboxUI.py
# Smart Toolbox - Industrial Grade (revisado e corrigido)
# Unidades: cm (padrão do Fusion). Ex.: wall = 0.25 -> 2.5 mm.

import adsk.core
import adsk.fusion
import traceback
import math
import os

handlers = []

# ============================================================
# PRESETS E MATERIAIS
# ============================================================

PRESETS = {
    'S (pequena)': (18.0, 8.0, 4.5, 2.5, 0.25),
    'M (média)': (24.0, 11.0, 6.0, 3.0, 0.25),
    'L (grande)': (32.0, 15.0, 8.0, 4.0, 0.3),
}

MATERIAL_TOL = {
    'PETG': 0.025,
    'ABS': 0.03,
    'PLA': 0.02,
    'TPU': 0.03
}


# ============================================================
# HELPERS DE GEOMETRIA
# ============================================================

def arredondar_quinas_verticais(comp, corpo, comprimento, largura, raio):
    """
    Filete somente nas quinas verticais externas do corpo.
    """
    arestas = adsk.core.ObjectCollection.create()

    for edge in corpo.edges:
        p1 = edge.startVertex.geometry
        p2 = edge.endVertex.geometry

        # Aresta vertical: mesmo x, y, variando z.
        if (abs(p1.x - p2.x) < 0.01 and
            abs(p1.y - p2.y) < 0.01 and
            abs(p1.z - p2.z) > 0.1):

            mx = (p1.x + p2.x) / 2.0
            my = (p1.y + p2.y) / 2.0

            # Somente arestas do perímetro externo.
            if (mx < 0.08 or mx > comprimento - 0.08 or
                my < 0.08 or my > largura - 0.08):
                arestas.add(edge)

    if arestas.count > 0:
        try:
            fi = comp.features.filletFeatures.createInput()
            fi.addConstantRadiusEdgeSet(
                arestas,
                adsk.core.ValueInput.createByReal(raio),
                True
            )
            comp.features.filletFeatures.add(fi)
        except Exception:
            print('[TRAKR-CAD] Filete de quina falhou (ignorado)')


def pegar_anel(sketch):
    """
    Retorna o primeiro perfil em formato de anel (2 loops), se existir.
    """
    for p in sketch.profiles:
        if p.profileLoops.count == 2:
            return p
    return None


def criar_passa_cabo(comp, extrudes, bx, by, flo_th=0.25):
    """
    Ponte para abraçadeira (zip-tie) com slot vertical atravessando.
    """
    sk = comp.sketches.add(comp.xYConstructionPlane)
    sk.sketchCurves.sketchLines.addTwoPointRectangle(
        adsk.core.Point3D.create(bx - 0.3, by - 0.2, 0),
        adsk.core.Point3D.create(bx + 0.3, by + 0.2, 0)
    )

    inp = extrudes.createInput(
        sk.profiles.item(0),
        adsk.fusion.FeatureOperations.JoinFeatureOperation
    )
    inp.startExtent = adsk.fusion.OffsetStartDefinition.create(
        adsk.core.ValueInput.createByReal(-flo_th)
    )
    inp.setDistanceExtent(False, adsk.core.ValueInput.createByReal(0.35))
    extrudes.add(inp)

    # Slot vertical atravessando a ponte.
    pl_in = comp.constructionPlanes.createInput()
    pl_in.setByOffset(
        comp.yZConstructionPlane,
        adsk.core.ValueInput.createByReal(bx)
    )
    pl_f = comp.constructionPlanes.add(pl_in)
    sk_f = comp.sketches.add(pl_f)

    p1 = sk_f.modelToSketchSpace(
        adsk.core.Point3D.create(bx, by - 0.12, -0.05)
    )
    p2 = sk_f.modelToSketchSpace(
        adsk.core.Point3D.create(bx, by + 0.12, 0.4)
    )

    sk_f.sketchCurves.sketchLines.addTwoPointRectangle(p1, p2)

    ext_f = extrudes.createInput(
        sk_f.profiles.item(0),
        adsk.fusion.FeatureOperations.CutFeatureOperation
    )
    ext_f.setSymmetricExtent(
        adsk.core.ValueInput.createByReal(0.3),
        True
    )
    extrudes.add(ext_f)


def criar_quinas(comp, extrudes, posicoes_x, largura_parede):
    """
    Quinas da dobradiça: cilindros anelares com eixo paralelo ao X
    na aresta traseira.
    """
    cy = largura_parede + 0.15
    cz = 0.05

    for x in posicoes_x:
        pl_in = comp.constructionPlanes.createInput()
        pl_in.setByOffset(
            comp.yZConstructionPlane,
            adsk.core.ValueInput.createByReal(x)
        )
        pl_f = comp.constructionPlanes.add(pl_in)
        sk = comp.sketches.add(pl_f)

        pc = sk.modelToSketchSpace(
            adsk.core.Point3D.create(x, cy, cz)
        )

        sk.sketchCurves.sketchCircles.addByCenterRadius(pc, 0.55)
        sk.sketchCurves.sketchCircles.addByCenterRadius(pc, 0.24)

        ring = pegar_anel(sk)
        if not ring:
            continue

        inp = extrudes.createInput(
            ring,
            adsk.fusion.FeatureOperations.JoinFeatureOperation
        )
        inp.setSymmetricExtent(
            adsk.core.ValueInput.createByReal(0.45),
            True
        )
        extrudes.add(inp)


def criar_standoffs(comp, extrudes, pontos_xy, start_z, altura):
    """
    Bosses com furo guia para parafuso M3.
    """
    d_ext = 0.6
    d_int = 0.25

    for px, py in pontos_xy:
        sk = comp.sketches.add(comp.xYConstructionPlane)

        sk.sketchCurves.sketchCircles.addByCenterRadius(
            adsk.core.Point3D.create(px, py, 0),
            d_ext / 2.0
        )
        sk.sketchCurves.sketchCircles.addByCenterRadius(
            adsk.core.Point3D.create(px, py, 0),
            d_int / 2.0
        )

        ring = pegar_anel(sk)
        if ring:
            inp = extrudes.createInput(
                ring,
                adsk.fusion.FeatureOperations.JoinFeatureOperation
            )
            inp.startExtent = adsk.fusion.OffsetStartDefinition.create(
                adsk.core.ValueInput.createByReal(start_z)
            )
            inp.setDistanceExtent(
                False,
                adsk.core.ValueInput.createByReal(altura)
            )
            extrudes.add(inp)


def exportar_stls(root, design):
    """
    Exporta cada corpo como STL em exports, ao lado do script.
    """
    try:
        try:
            base_dir = os.path.dirname(os.path.abspath(__file__))
        except NameError:
            base_dir = os.getcwd()

        pasta = os.path.join(base_dir, '..', 'exports')
        os.makedirs(pasta, exist_ok=True)

        em = design.exportManager

        for occ in root.occurrences:
            contagem = {}

            for body in occ.component.bRepBodies:
                base = body.parentComponent.name.replace(' ', '')
                contagem[base] = contagem.get(base, 0) + 1
                nome = '%s%02d.stl' % (base, contagem[base])

                opt = em.createSTLExportOptions(
                    body,
                    os.path.join(pasta, nome)
                )
                opt.meshRefinement = (
                    adsk.fusion.MeshRefinementSettings.MeshRefinementMedium
                )

                em.execute(opt)
                print('[TRAKR-CAD] STL exportado: ' + nome)

    except Exception:
        print('[TRAKR-CAD] Falha ao exportar STLs (ignorado)')


def simular_abertura(t_occ, tray_occ, transform_tr, comprimento, largura):
    """
    Gira a tampa aproximadamente 100 graus no eixo da dobradiça
    e valida as travas de abertura.
    """
    ang = math.radians(-100)
    eixo = adsk.core.Vector3D.create(1, 0, 0)
    origem = adsk.core.Point3D.create(0, largura + 0.15, 0.05)

    mat = adsk.core.Matrix3D.create()
    mat.setToRotation(ang, eixo, origem)

    t_occ.transform = mat

    # Bandeja presa na tampa gira junto.
    mat_tr = mat.copy()
    mat_tr.transformBy(transform_tr)
    tray_occ.transform = mat_tr

    # Validação aproximada da trava de abertura.
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

    trava = (
        x - 0.35,
        largura - 0.45,
        0.0,
        x + 0.35,
        largura - 0.25,
        0.3
    )

    toca = (
        max(xs) > trava[0] and min(xs) < trava[3] and
        max(ys) > trava[1] and min(ys) < trava[4] and
        max(zs) > trava[2] and min(zs) < trava[5]
    )

    if toca:
        print('[TRAKR-CAD] Trava de abertura ENGATA em ~100° (ok)')
    else:
        print('[TRAKR-CAD] ATENÇÃO: asa não alcança a trava em 100° — ajuste posições')


# ============================================================
# GERAÇÃO DA MALETA
# ============================================================

def gerar_maleta(length, width, base_depth, lid_depth, wall, acessorios,
                 material='PETG', abrir_tampa=False, exportar=True, display=False):

    app = adsk.core.Application.get()
    design = app.activeProduct
    root = design.rootComponent

    lip_h = 0.25
    lip_t = 0.20
    tol = MATERIAL_TOL.get(material, 0.025)
    gap = 0.05

    # ==========================================================
    # 1. BASE
    # ==========================================================

    b_occ = root.occurrences.addNewComponent(adsk.core.Matrix3D.create())
    b_comp = b_occ.component
    b_comp.name = "Maleta_Base"

    sk_b = b_comp.sketches.add(b_comp.xYConstructionPlane)
    sk_b.sketchCurves.sketchLines.addTwoPointRectangle(
        adsk.core.Point3D.create(0, 0, 0),
        adsk.core.Point3D.create(length, width, 0)
    )

    ext_b = b_comp.features.extrudeFeatures.createInput(
        sk_b.profiles.item(0),
        adsk.fusion.FeatureOperations.NewBodyFeatureOperation
    )

    # Extrusão confiável para baixo: z = 0 até -base_depth.
    ext_b.setDistanceExtent(
        False,
        adsk.core.ValueInput.createByReal(-base_depth)
    )

    body_b = b_comp.features.extrudeFeatures.add(ext_b).bodies.item(0)

    # Casca da base: remove o interior, deixando fundo com espessura wall.
    sk_in = b_comp.sketches.add(b_comp.xYConstructionPlane)
    sk_in.sketchCurves.sketchLines.addTwoPointRectangle(
        adsk.core.Point3D.create(wall, wall, 0),
        adsk.core.Point3D.create(length - wall, width - wall, 0)
    )

    e_in = b_comp.features.extrudeFeatures.createInput(
        sk_in.profiles.item(0),
        adsk.fusion.FeatureOperations.CutFeatureOperation
    )

    # Corte para baixo, deixando fundo com wall.
    e_in.setDistanceExtent(
        False,
        adsk.core.ValueInput.createByReal(-(base_depth - wall))
    )
    b_comp.features.extrudeFeatures.add(e_in)

    # Filetes externos.
    arredondar_quinas_verticais(b_comp, body_b, length, width, 0.15)

    # Lip macho no aro.
    sk_lip = b_comp.sketches.add(b_comp.xYConstructionPlane)

    sk_lip.sketchCurves.sketchLines.addTwoPointRectangle(
        adsk.core.Point3D.create(wall - lip_t, wall - lip_t, 0),
        adsk.core.Point3D.create(length - wall + lip_t, width - wall + lip_t, 0)
    )

    sk_lip.sketchCurves.sketchLines.addTwoPointRectangle(
        adsk.core.Point3D.create(wall, wall, 0),
        adsk.core.Point3D.create(length - wall, width - wall, 0)
    )

    p_lip = pegar_anel(sk_lip)
    if p_lip:
        e_lip = b_comp.features.extrudeFeatures.createInput(
            p_lip,
            adsk.fusion.FeatureOperations.JoinFeatureOperation
        )
        e_lip.startExtent = adsk.fusion.OffsetStartDefinition.create(
            adsk.core.ValueInput.createByReal(-0.1)
        )
        e_lip.setDistanceExtent(
            False,
            adsk.core.ValueInput.createByReal(lip_h)
        )
        b_comp.features.extrudeFeatures.add(e_lip)

    # Notch no lip frontal.
    sk_notch = b_comp.sketches.add(b_comp.xYConstructionPlane)
    sk_notch.sketchCurves.sketchLines.addTwoPointRectangle(
        adsk.core.Point3D.create(length / 2.0 - 0.5, 0.02, 0),
        adsk.core.Point3D.create(length / 2.0 + 0.5, 0.3, 0)
    )

    e_notch = b_comp.features.extrudeFeatures.createInput(
        sk_notch.profiles.item(0),
        adsk.fusion.FeatureOperations.CutFeatureOperation
    )
    e_notch.setDistanceExtent(
        False,
        adsk.core.ValueInput.createByReal(0.3)
    )
    b_comp.features.extrudeFeatures.add(e_notch)

    # Clipe de fecho: tab flexível.
    sk_latch = b_comp.sketches.add(b_comp.xYConstructionPlane)
    sk_latch.sketchCurves.sketchLines.addTwoPointRectangle(
        adsk.core.Point3D.create(length / 2.0 - 0.2, -0.15, 0),
        adsk.core.Point3D.create(length / 2.0 + 0.2, 0.1, 0)
    )

    e_latch = b_comp.features.extrudeFeatures.createInput(
        sk_latch.profiles.item(0),
        adsk.fusion.FeatureOperations.JoinFeatureOperation
    )
    e_latch.setDistanceExtent(
        False,
        adsk.core.ValueInput.createByReal(0.7)
    )
    b_comp.features.extrudeFeatures.add(e_latch)

    # Barb do clipe.
    sk_barb = b_comp.sketches.add(b_comp.xYConstructionPlane)
    sk_barb.sketchCurves.sketchLines.addTwoPointRectangle(
        adsk.core.Point3D.create(length / 2.0 - 0.15, -0.05, 0),
        adsk.core.Point3D.create(length / 2.0 + 0.15, 0.25, 0)
    )

    e_barb = b_comp.features.extrudeFeatures.createInput(
        sk_barb.profiles.item(0),
        adsk.fusion.FeatureOperations.JoinFeatureOperation
    )
    e_barb.startExtent = adsk.fusion.OffsetStartDefinition.create(
        adsk.core.ValueInput.createByReal(-0.3)
    )
    e_barb.setDistanceExtent(
        False,
        adsk.core.ValueInput.createByReal(1.0)
    )
    b_comp.features.extrudeFeatures.add(e_barb)

    # Ímã na parede frontal interna da base.
    mag_y = wall + 0.35

    sk_mag = b_comp.sketches.add(b_comp.xYConstructionPlane)
    sk_mag.sketchCurves.sketchCircles.addByCenterRadius(
        adsk.core.Point3D.create(length / 2.0, mag_y, 0),
        0.5
    )

    e_mag = b_comp.features.extrudeFeatures.createInput(
        sk_mag.profiles.item(0),
        adsk.fusion.FeatureOperations.JoinFeatureOperation
    )
    e_mag.startExtent = adsk.fusion.OffsetStartDefinition.create(
        adsk.core.ValueInput.createByReal(-0.8)
    )
    e_mag.setDistanceExtent(
        False,
        adsk.core.ValueInput.createByReal(0.7)
    )
    b_comp.features.extrudeFeatures.add(e_mag)

    # Furo do ímã.
    pl_mag_h = b_comp.constructionPlanes.createInput()
    pl_mag_h.setByOffset(
        b_comp.xYConstructionPlane,
        adsk.core.ValueInput.createByReal(-0.35)
    )
    pl_mag_hf = b_comp.constructionPlanes.add(pl_mag_h)
    sk_mag_hole = b_comp.sketches.add(pl_mag_hf)

    pc_mag = sk_mag_hole.modelToSketchSpace(
        adsk.core.Point3D.create(length / 2.0, mag_y, -0.35)
    )
    sk_mag_hole.sketchCurves.sketchCircles.addByCenterRadius(pc_mag, 0.42)

    e_mag_hole = b_comp.features.extrudeFeatures.createInput(
        sk_mag_hole.profiles.item(0),
        adsk.fusion.FeatureOperations.CutFeatureOperation
    )
    e_mag_hole.setDistanceExtent(
        False,
        adsk.core.ValueInput.createByReal(0.35)
    )
    b_comp.features.extrudeFeatures.add(e_mag_hole)

    # ==========================================================
    # POCKETS DOS PÉS TPU — CORREÇÃO PRINCIPAL
    # ==========================================================
    # Antes o sketch ficava dentro do fundo e o corte podia não
    # encontrar corpo alvo.
    #
    # Agora o plano fica 0.01 cm abaixo da face inferior da base
    # e o corte sobe 0.21 cm, criando pocket útil de 0.20 cm.
    # ==========================================================

    fm = 0.5
    z_inicio_pes = -base_depth - 0.01

    pl_feet = b_comp.constructionPlanes.createInput()
    pl_feet.setByOffset(
        b_comp.xYConstructionPlane,
        adsk.core.ValueInput.createByReal(z_inicio_pes)
    )
    pl_feet_f = b_comp.constructionPlanes.add(pl_feet)
    sk_feet = b_comp.sketches.add(pl_feet_f)

    posicoes_pes = [
        (fm, fm),
        (length - fm, fm),
        (fm, width - fm),
        (length - fm, width - fm)
    ]

    for fx, fy in posicoes_pes:
        pc = sk_feet.modelToSketchSpace(
            adsk.core.Point3D.create(fx, fy, z_inicio_pes)
        )
        sk_feet.sketchCurves.sketchCircles.addByCenterRadius(pc, 0.42)

    for p in sk_feet.profiles:
        e_feet = b_comp.features.extrudeFeatures.createInput(
            p,
            adsk.fusion.FeatureOperations.CutFeatureOperation
        )

        # Sobe de baixo para dentro da base.
        e_feet.setDistanceExtent(
            False,
            adsk.core.ValueInput.createByReal(0.21)
        )

        b_comp.features.extrudeFeatures.add(e_feet)

    # Quinas da dobradiça na base.
    criar_quinas(
        b_comp,
        b_comp.features.extrudeFeatures,
        [length * 0.2, length * 0.4, length * 0.6, length * 0.8],
        width
    )

    # Travas de abertura.
    for sx in [length * 0.12, length * 0.88]:
        pl_in = b_comp.constructionPlanes.createInput()
        pl_in.setByOffset(
            b_comp.yZConstructionPlane,
            adsk.core.ValueInput.createByReal(sx)
        )
        pl_f = b_comp.constructionPlanes.add(pl_in)
        sk_stop = b_comp.sketches.add(pl_f)

        p1 = sk_stop.modelToSketchSpace(
            adsk.core.Point3D.create(sx, width - 0.45, 0.0)
        )
        p2 = sk_stop.modelToSketchSpace(
            adsk.core.Point3D.create(sx, width - 0.25, 0.3)
        )

        sk_stop.sketchCurves.sketchLines.addTwoPointRectangle(p1, p2)

        e_stop = b_comp.features.extrudeFeatures.createInput(
            sk_stop.profiles.item(0),
            adsk.fusion.FeatureOperations.JoinFeatureOperation
        )
        e_stop.setSymmetricExtent(
            adsk.core.ValueInput.createByReal(0.35),
            True
        )
        b_comp.features.extrudeFeatures.add(e_stop)

    # ==========================================================
    # 2. TAMPA — posicionada FECHADA sobre a base
    # ==========================================================

    matrix_t = adsk.core.Matrix3D.create()
    matrix_t.translation = adsk.core.Vector3D.create(0, 0, gap)

    t_occ = root.occurrences.addNewComponent(matrix_t)
    t_comp = t_occ.component
    t_comp.name = "Maleta_Tampa"

    sk_t = t_comp.sketches.add(t_comp.xYConstructionPlane)
    sk_t.sketchCurves.sketchLines.addTwoPointRectangle(
        adsk.core.Point3D.create(0, 0, 0),
        adsk.core.Point3D.create(length, width, 0)
    )

    ext_t = t_comp.features.extrudeFeatures.createInput(
        sk_t.profiles.item(0),
        adsk.fusion.FeatureOperations.NewBodyFeatureOperation
    )
    ext_t.setDistanceExtent(
        False,
        adsk.core.ValueInput.createByReal(lid_depth)
    )

    body_t = t_comp.features.extrudeFeatures.add(ext_t).bodies.item(0)

    # Casca da tampa.
    sk_in_t = t_comp.sketches.add(t_comp.xYConstructionPlane)
    sk_in_t.sketchCurves.sketchLines.addTwoPointRectangle(
        adsk.core.Point3D.create(wall, wall, 0),
        adsk.core.Point3D.create(length - wall, width - wall, 0)
    )

    e_in_t = t_comp.features.extrudeFeatures.createInput(
        sk_in_t.profiles.item(0),
        adsk.fusion.FeatureOperations.CutFeatureOperation
    )
    e_in_t.setDistanceExtent(
        False,
        adsk.core.ValueInput.createByReal(lid_depth - wall)
    )
    t_comp.features.extrudeFeatures.add(e_in_t)

    # Filetes externos.
    arredondar_quinas_verticais(t_comp, body_t, length, width, 0.15)

    # Groove do encaixe.
    sk_gr = t_comp.sketches.add(t_comp.xYConstructionPlane)

    sk_gr.sketchCurves.sketchLines.addTwoPointRectangle(
        adsk.core.Point3D.create(wall - lip_t - tol, wall - lip_t - tol, 0),
        adsk.core.Point3D.create(length - wall + lip_t + tol, width - wall + lip_t + tol, 0)
    )

    sk_gr.sketchCurves.sketchLines.addTwoPointRectangle(
        adsk.core.Point3D.create(wall + tol, wall + tol, 0),
        adsk.core.Point3D.create(length - wall - tol, width - wall - tol, 0)
    )

    p_gr = pegar_anel(sk_gr)
    if p_gr:
        e_gr = t_comp.features.extrudeFeatures.createInput(
            p_gr,
            adsk.fusion.FeatureOperations.CutFeatureOperation
        )
        e_gr.setDistanceExtent(
            False,
            adsk.core.ValueInput.createByReal(lip_h + 0.05)
        )
        t_comp.features.extrudeFeatures.add(e_gr)

    # Slot do clipe frontal.
    sk_lslot = t_comp.sketches.add(t_comp.xYConstructionPlane)
    sk_lslot.sketchCurves.sketchLines.addTwoPointRectangle(
        adsk.core.Point3D.create(length / 2.0 - 0.25, -0.2, 0),
        adsk.core.Point3D.create(length / 2.0 + 0.25, 0.45, 0)
    )

    e_lslot = t_comp.features.extrudeFeatures.createInput(
        sk_lslot.profiles.item(0),
        adsk.fusion.FeatureOperations.CutFeatureOperation
    )
    e_lslot.setDistanceExtent(
        False,
        adsk.core.ValueInput.createByReal(0.75)
    )
    t_comp.features.extrudeFeatures.add(e_lslot)

    # Trava do clipe.
    sk_tooth = t_comp.sketches.add(t_comp.xYConstructionPlane)
    sk_tooth.sketchCurves.sketchLines.addTwoPointRectangle(
        adsk.core.Point3D.create(length / 2.0 - 0.45, 0.1, 0),
        adsk.core.Point3D.create(length / 2.0 + 0.45, 0.4, 0)
    )

    e_tooth = t_comp.features.extrudeFeatures.createInput(
        sk_tooth.profiles.item(0),
        adsk.fusion.FeatureOperations.JoinFeatureOperation
    )
    e_tooth.setDistanceExtent(
        False,
        adsk.core.ValueInput.createByReal(0.45)
    )
    t_comp.features.extrudeFeatures.add(e_tooth)

    # Pocket do sensor Hall.
    pl_hall = t_comp.constructionPlanes.createInput()
    pl_hall.setByOffset(
        t_comp.yZConstructionPlane,
        adsk.core.ValueInput.createByReal(length / 2.0)
    )
    pl_hall_f = t_comp.constructionPlanes.add(pl_hall)
    sk_hall_t = t_comp.sketches.add(pl_hall_f)

    p1_hall = sk_hall_t.modelToSketchSpace(
        adsk.core.Point3D.create(length / 2.0, 0.15, 0.55)
    )
    p2_hall = sk_hall_t.modelToSketchSpace(
        adsk.core.Point3D.create(length / 2.0, 0.65, 0.95)
    )

    sk_hall_t.sketchCurves.sketchLines.addTwoPointRectangle(p1_hall, p2_hall)

    e_hall_t = t_comp.features.extrudeFeatures.createInput(
        sk_hall_t.profiles.item(0),
        adsk.fusion.FeatureOperations.CutFeatureOperation
    )
    e_hall_t.setSymmetricExtent(
        adsk.core.ValueInput.createByReal(0.3),
        True
    )
    t_comp.features.extrudeFeatures.add(e_hall_t)

    # Standoffs M3.
    criar_standoffs(
        t_comp,
        t_comp.features.extrudeFeatures,
        [
            (length / 2.0 - 2.2, wall + 1.3),
            (length / 2.0 + 2.2, wall + 1.3),
            (length / 2.0 - 2.2, wall + 2.4),
            (length / 2.0 + 2.2, wall + 2.4)
        ],
        lid_depth - wall - 0.55,
        0.55
    )

    # Recortes USB-C / LED / botão na parede frontal da tampa.
    usb_x0 = length * 0.45
    usb_z0 = lid_depth - 1.1

    # USB-C.
    sk_usb = t_comp.sketches.add(t_comp.xZConstructionPlane)

    p_usb1 = sk_usb.modelToSketchSpace(
        adsk.core.Point3D.create(usb_x0, 0, usb_z0)
    )
    p_usb2 = sk_usb.modelToSketchSpace(
        adsk.core.Point3D.create(usb_x0 + 1.3, 0, usb_z0 + 0.6)
    )

    sk_usb.sketchCurves.sketchLines.addTwoPointRectangle(p_usb1, p_usb2)

    e_usb = t_comp.features.extrudeFeatures.createInput(
        sk_usb.profiles.item(0),
        adsk.fusion.FeatureOperations.CutFeatureOperation
    )
    e_usb.setDistanceExtent(
        False,
        adsk.core.ValueInput.createByReal(0.45)
    )
    t_comp.features.extrudeFeatures.add(e_usb)

    # LED.
    sk_led = t_comp.sketches.add(t_comp.xZConstructionPlane)

    p_led1 = sk_led.modelToSketchSpace(
        adsk.core.Point3D.create(usb_x0 - 1.0, 0, usb_z0)
    )
    p_led2 = sk_led.modelToSketchSpace(
        adsk.core.Point3D.create(usb_x0 - 0.45, 0, usb_z0 + 0.6)
    )

    sk_led.sketchCurves.sketchLines.addTwoPointRectangle(p_led1, p_led2)

    e_led = t_comp.features.extrudeFeatures.createInput(
        sk_led.profiles.item(0),
        adsk.fusion.FeatureOperations.CutFeatureOperation
    )
    e_led.setDistanceExtent(
        False,
        adsk.core.ValueInput.createByReal(0.45)
    )
    t_comp.features.extrudeFeatures.add(e_led)

    # Botão.
    sk_btn = t_comp.sketches.add(t_comp.xZConstructionPlane)

    p_btn1 = sk_btn.modelToSketchSpace(
        adsk.core.Point3D.create(usb_x0 + 1.9, 0, usb_z0)
    )
    p_btn2 = sk_btn.modelToSketchSpace(
        adsk.core.Point3D.create(usb_x0 + 2.6, 0, usb_z0 + 0.6)
    )

    sk_btn.sketchCurves.sketchLines.addTwoPointRectangle(p_btn1, p_btn2)

    e_btn = t_comp.features.extrudeFeatures.createInput(
        sk_btn.profiles.item(0),
        adsk.fusion.FeatureOperations.CutFeatureOperation
    )
    e_btn.setDistanceExtent(
        False,
        adsk.core.ValueInput.createByReal(0.45)
    )
    t_comp.features.extrudeFeatures.add(e_btn)

    # Grade do buzzer.
    sk_buzz = t_comp.sketches.add(t_comp.yZConstructionPlane)
    bz0 = lid_depth * 0.5

    for bz in [bz0 - 0.4, bz0, bz0 + 0.4]:
        pc_buzz = sk_buzz.modelToSketchSpace(
            adsk.core.Point3D.create(0, width / 2.0, bz)
        )
        sk_buzz.sketchCurves.sketchCircles.addByCenterRadius(pc_buzz, 0.15)

    for p in sk_buzz.profiles:
        e_buzz = t_comp.features.extrudeFeatures.createInput(
            p,
            adsk.fusion.FeatureOperations.CutFeatureOperation
        )
        e_buzz.setSymmetricExtent(
            adsk.core.ValueInput.createByReal(0.6),
            True
        )
        t_comp.features.extrudeFeatures.add(e_buzz)

    # Pocket do display LCD opcional.
    if display and length >= 22.0 and width >= 10.0:
        dx = length * 0.14
        dy = width - 2.6

        # Plano ligeiramente acima do topo para garantir corte entrando.
        z_lcd = lid_depth + 0.01

        # Janela do LCD.
        pl_lcd_w = t_comp.constructionPlanes.createInput()
        pl_lcd_w.setByOffset(
            t_comp.xYConstructionPlane,
            adsk.core.ValueInput.createByReal(z_lcd)
        )
        pl_lcd_wf = t_comp.constructionPlanes.add(pl_lcd_w)
        sk_lcd_win = t_comp.sketches.add(pl_lcd_wf)

        p_lcd1 = sk_lcd_win.modelToSketchSpace(
            adsk.core.Point3D.create(dx - 2.5, dy - 1.9, z_lcd)
        )
        p_lcd2 = sk_lcd_win.modelToSketchSpace(
            adsk.core.Point3D.create(dx + 2.5, dy + 1.9, z_lcd)
        )

        sk_lcd_win.sketchCurves.sketchLines.addTwoPointRectangle(p_lcd1, p_lcd2)

        e_lcd_win = t_comp.features.extrudeFeatures.createInput(
            sk_lcd_win.profiles.item(0),
            adsk.fusion.FeatureOperations.CutFeatureOperation
        )
        e_lcd_win.setDistanceExtent(
            False,
            adsk.core.ValueInput.createByReal(-(0.45 + 0.01))
        )
        t_comp.features.extrudeFeatures.add(e_lcd_win)

        # Rebaixo do LCD.
        pl_lcd_p = t_comp.constructionPlanes.createInput()
        pl_lcd_p.setByOffset(
            t_comp.xYConstructionPlane,
            adsk.core.ValueInput.createByReal(z_lcd)
        )
        pl_lcd_pf = t_comp.constructionPlanes.add(pl_lcd_p)
        sk_lcd_pk = t_comp.sketches.add(pl_lcd_pf)

        p_lcd_pk1 = sk_lcd_pk.modelToSketchSpace(
            adsk.core.Point3D.create(dx - 2.95, dy - 2.25, z_lcd)
        )
        p_lcd_pk2 = sk_lcd_pk.modelToSketchSpace(
            adsk.core.Point3D.create(dx + 2.95, dy + 2.25, z_lcd)
        )

        sk_lcd_pk.sketchCurves.sketchLines.addTwoPointRectangle(p_lcd_pk1, p_lcd_pk2)

        e_lcd_pk = t_comp.features.extrudeFeatures.createInput(
            sk_lcd_pk.profiles.item(0),
            adsk.fusion.FeatureOperations.CutFeatureOperation
        )
        e_lcd_pk.setDistanceExtent(
            False,
            adsk.core.ValueInput.createByReal(-(0.25 + 0.01))
        )
        t_comp.features.extrudeFeatures.add(e_lcd_pk)

    elif display:
        print(
            '[TRAKR-CAD] Display LCD 2.4" ignorado: maleta pequena demais '
            '(mín. 22 x 10 cm) — não cabe ao lado do pilar da alça'
        )

    # Alça na tampa.
    sk_ha = t_comp.sketches.add(t_comp.xYConstructionPlane)

    for hx in [length * 0.3, length * 0.7]:
        sk_ha.sketchCurves.sketchLines.addTwoPointRectangle(
            adsk.core.Point3D.create(hx - 0.5, width / 2.0 - 0.4, 0),
            adsk.core.Point3D.create(hx + 0.5, width / 2.0 + 0.4, 0)
        )

    for p in sk_ha.profiles:
        e_ha = t_comp.features.extrudeFeatures.createInput(
            p,
            adsk.fusion.FeatureOperations.JoinFeatureOperation
        )
        e_ha.setDistanceExtent(
            False,
            adsk.core.ValueInput.createByReal(3.8)
        )
        t_comp.features.extrudeFeatures.add(e_ha)

    sk_ha2 = t_comp.sketches.add(t_comp.xYConstructionPlane)
    sk_ha2.sketchCurves.sketchLines.addTwoPointRectangle(
        adsk.core.Point3D.create(length * 0.3 - 0.5, width / 2.0 - 0.9, 0),
        adsk.core.Point3D.create(length * 0.7 + 0.5, width / 2.0 + 0.9, 0)
    )

    e_ha2 = t_comp.features.extrudeFeatures.createInput(
        sk_ha2.profiles.item(0),
        adsk.fusion.FeatureOperations.JoinFeatureOperation
    )
    e_ha2.startExtent = adsk.fusion.OffsetStartDefinition.create(
        adsk.core.ValueInput.createByReal(3.7)
    )
    e_ha2.setDistanceExtent(
        False,
        adsk.core.ValueInput.createByReal(0.5)
    )
    t_comp.features.extrudeFeatures.add(e_ha2)

    # Quinas da dobradiça na tampa.
    criar_quinas(
        t_comp,
        t_comp.features.extrudeFeatures,
        [length * 0.3, length * 0.5, length * 0.7],
        width
    )

    # Asas do limitador de abertura.
    for wx in [length * 0.12, length * 0.88]:
        pl_in = t_comp.constructionPlanes.createInput()
        pl_in.setByOffset(
            t_comp.yZConstructionPlane,
            adsk.core.ValueInput.createByReal(wx)
        )
        pl_f = t_comp.constructionPlanes.add(pl_in)
        sk_wing = t_comp.sketches.add(pl_f)

        p1_wing = sk_wing.modelToSketchSpace(
            adsk.core.Point3D.create(wx, width - 0.2, -0.5)
        )
        p2_wing = sk_wing.modelToSketchSpace(
            adsk.core.Point3D.create(wx, width + 0.25, 0.0)
        )

        sk_wing.sketchCurves.sketchLines.addTwoPointRectangle(p1_wing, p2_wing)

        e_wing = t_comp.features.extrudeFeatures.createInput(
            sk_wing.profiles.item(0),
            adsk.fusion.FeatureOperations.JoinFeatureOperation
        )
        e_wing.setSymmetricExtent(
            adsk.core.ValueInput.createByReal(0.35),
            True
        )
        t_comp.features.extrudeFeatures.add(e_wing)

    # ==========================================================
    # 3. BANDEJA — presa na tampa
    # ==========================================================

    tray_occ = root.occurrences.addNewComponent(adsk.core.Matrix3D.create())
    tray_comp = tray_occ.component
    tray_comp.name = "Maleta_Bandeja"

    t_len = length - (wall * 2.0) - (tol * 2.0)
    t_wid = width - (wall * 2.0) - (tol * 2.0)

    flo_th = 0.25

    if t_len < 15.0 or t_wid < 7.4:
        adsk.core.Application.get().userInterface.messageBox(
            'Atencao: bandeja menor que o preset S (18x8 cm) nao comporta '
            'o kit eletronico completo — os compartimentos podem se '
            'sobrepor. Use S, M ou L.'
        )

    # --- Componentes da maleta (cm, medidas oficiais + folga) ---
    # ESP32-WROOM-32 breakout 51.5x25.4 mm -> corte 52.1x26.0 (bolso 8 mm,
    # fixada por 4 insertos de latão M3 em standoffs no centro geométrico);
    # YRM100 UHF 40x30 -> 40.5x30.5 (bolso 6.5 mm); antena cerâmica IPEX
    # 25x25 -> 25.8x25.8 (bolso raso 3 mm + canal do cabo);
    # bateria 18650 diam. 18.2 x 65.0 -> berço semicircular raio 0.95;
    # TP4056 USB-C 28x17 -> 28.5x17.5; Hall A3144 (KY-003) ~21x17 mm;
    # buzzer ativo 12 mm; LED WS2812B 5x5 mm.
    esp_l, esp_w = 5.21, 2.60
    esp_x0, esp_y0 = 0.75, 2.40
    yrm_l, yrm_w = 4.05, 3.05
    yrm_x0, yrm_y0 = 6.40, 4.10
    tp_l, tp_w = 2.85, 1.75
    tp_x0, tp_y0 = 7.75, 0.10
    ant_s = 2.58
    ant_x0, ant_y0 = 10.90, 4.10
    hal_l, hal_w = 2.10, 1.70
    buz_d = 1.3
    bat_l = 6.6            # comprimento do berço (65 mm + folga)
    bat_r = 0.95           # raio interno do semi-berço 18650

    # Standoffs M3 da ESP32 (inserto latão M3: furo 4.2 mm = r 0.21;
    # standoff OD 6.0 mm = r 0.30), cantos com 0.5 cm de recuo.
    esp_ss = [
        (esp_x0 + 0.5, esp_y0 + 0.5),
        (esp_x0 + esp_l - 0.5, esp_y0 + 0.5),
        (esp_x0 + 0.5, esp_y0 + esp_w - 0.5),
        (esp_x0 + esp_l - 0.5, esp_y0 + esp_w - 0.5)
    ]
    ss_r_o, ss_r_i = 0.30, 0.21
    ss_h = 1.05

    # Berços de parede fina ao redor dos componentes (JOIN, sem cortes).
    # YRM100: parede direita aberta (gap 4.55..5.25) para o cabo IPEX.
    yrm_walls = [
        (yrm_x0, yrm_y0, yrm_x0 + yrm_l, yrm_y0 + 0.15),
        (yrm_x0, yrm_y0 + yrm_w - 0.15, yrm_x0 + yrm_l, yrm_y0 + yrm_w),
        (yrm_x0, yrm_y0, yrm_x0 + 0.15, yrm_y0 + yrm_w),
        (yrm_x0 + yrm_l, yrm_y0, yrm_x0 + yrm_l + 0.15, 4.55),
        (yrm_x0 + yrm_l, 5.25, yrm_x0 + yrm_l + 0.15, yrm_y0 + yrm_w)
    ]
    # Antena IPEX: parede esquerda aberta (gap 4.85..5.45) para o cabo.
    ant_walls = [
        (ant_x0, ant_y0, ant_x0 + ant_s, ant_y0 + 0.15),
        (ant_x0, ant_y0 + ant_s - 0.15, ant_x0 + ant_s, ant_y0 + ant_s),
        (ant_x0 + ant_s - 0.15, ant_y0, ant_x0 + ant_s, ant_y0 + ant_s),
        (ant_x0, ant_y0, ant_x0 + 0.15, 4.85),
        (ant_x0, 5.45, ant_x0 + 0.15, ant_y0 + ant_s)
    ]
    # Canal do cabo IPEX entre YRM100 e antena (slot 4.75..5.05).
    ipex_ch = [
        (10.6, 4.6, 10.9, 4.75),
        (10.6, 5.05, 10.9, 5.2)
    ]
    # TP4056: parede direita aberta (gap 0.5..1.05) para os fios.
    tp_walls = [
        (tp_x0 - 0.15, tp_y0 - 0.15, tp_x0 + tp_l + 0.15, tp_y0),
        (tp_x0 - 0.15, tp_y0 + tp_w, tp_x0 + tp_l + 0.15, tp_y0 + tp_w + 0.15),
        (tp_x0 - 0.15, tp_y0 - 0.15, tp_x0, tp_y0 + tp_w + 0.15),
        (tp_x0 + tp_l, tp_y0 - 0.15, tp_x0 + tp_l + 0.15, 0.5),
        (tp_x0 + tp_l, 1.05, tp_x0 + tp_l + 0.15, tp_y0 + tp_w + 0.15)
    ]
    # Hall A3144 (KY-003) 21x17 mm.
    hal_walls = [
        (13.75, 4.75, 16.15, 4.9),
        (13.75, 6.6, 16.15, 6.75),
        (13.75, 4.75, 13.9, 6.75),
        (16.0, 4.75, 16.15, 6.75)
    ]
    # LED WS2812B 5x5 mm.
    led_walls = [
        (14.15, 2.1, 15.5, 2.25),
        (14.15, 2.85, 15.5, 3.0),
        (14.15, 2.1, 14.3, 3.0),
        (15.35, 2.1, 15.5, 3.0)
    ]
    # Anel-cradle do buzzer 12 mm.
    buz_ring = (12.2, 2.2, 0.65, 0.8, 1.0)

    # Pontes para zip-ties (cabo IPEX + fios TP4056-bateria).
    pontes = [(12.4, 3.55), (16.4, 3.6)]

    boss_xy = [
        (0.6, 0.6), (t_len - 0.6, 0.6),
        (0.6, t_wid - 0.6), (t_len - 0.6, t_wid - 0.6)
    ]

    # Piso da bandeja.
    sk_tray = tray_comp.sketches.add(tray_comp.xYConstructionPlane)
    sk_tray.sketchCurves.sketchLines.addTwoPointRectangle(
        adsk.core.Point3D.create(0, 0, 0),
        adsk.core.Point3D.create(t_len, t_wid, 0)
    )

    ext_tr = tray_comp.features.extrudeFeatures.createInput(
        sk_tray.profiles.item(0),
        adsk.fusion.FeatureOperations.NewBodyFeatureOperation
    )
    ext_tr.startExtent = adsk.fusion.OffsetStartDefinition.create(
        adsk.core.ValueInput.createByReal(-flo_th)
    )
    ext_tr.setDistanceExtent(
        False,
        adsk.core.ValueInput.createByReal(flo_th)
    )
    tray_comp.features.extrudeFeatures.add(ext_tr)

    # Pontes para zip-ties (cabo da antena e fiação TP4056-bateria).
    for bx, by in pontes:
        criar_passa_cabo(
            tray_comp,
            tray_comp.features.extrudeFeatures,
            bx,
            by
        )

    # Standoffs M3 da ESP32 (tubo com furo para o inserto de latão —
    # JOIN, crescem para cima a partir do piso).
    for sx, sy in esp_ss:
        sk_ss = tray_comp.sketches.add(tray_comp.xYConstructionPlane)
        sk_ss.sketchCurves.sketchCircles.addByCenterRadius(
            adsk.core.Point3D.create(sx, sy, 0), ss_r_o)
        sk_ss.sketchCurves.sketchCircles.addByCenterRadius(
            adsk.core.Point3D.create(sx, sy, 0), ss_r_i)
        p_ss = pegar_anel(sk_ss)
        if p_ss:
            e_ss = tray_comp.features.extrudeFeatures.createInput(
                p_ss,
                adsk.fusion.FeatureOperations.JoinFeatureOperation
            )
            e_ss.startExtent = adsk.fusion.OffsetStartDefinition.create(
                adsk.core.ValueInput.createByReal(-flo_th)
            )
            e_ss.setDistanceExtent(
                False,
                adsk.core.ValueInput.createByReal(ss_h + flo_th)
            )
            tray_comp.features.extrudeFeatures.add(e_ss)

    # Berços de parede fina (YRM100, antena, canal IPEX, TP4056, sensor
    # Hall e LED).
    for x0, y0, x1, y1 in (
        yrm_walls + ant_walls + ipex_ch + tp_walls + hal_walls + led_walls
    ):
        sk_w = tray_comp.sketches.add(tray_comp.xYConstructionPlane)
        sk_w.sketchCurves.sketchLines.addTwoPointRectangle(
            adsk.core.Point3D.create(x0, y0, 0),
            adsk.core.Point3D.create(x1, y1, 0)
        )
        e_w = tray_comp.features.extrudeFeatures.createInput(
            sk_w.profiles.item(0),
            adsk.fusion.FeatureOperations.JoinFeatureOperation
        )
        e_w.startExtent = adsk.fusion.OffsetStartDefinition.create(
            adsk.core.ValueInput.createByReal(-flo_th)
        )
        e_w.setDistanceExtent(
            False,
            adsk.core.ValueInput.createByReal(0.75)
        )
        tray_comp.features.extrudeFeatures.add(e_w)

    # Berço semicircular da bateria 18650: meio-anele desenhado no plano
    # yZ deslocado para x=0.75 (anel em y=0.95, z=0.95; raios 0.95/1.1),
    # extrudado para +X até x=7.35. Arcos por 3 pontos fechados por 2
    # linhas radiais (perfil em ar, JOIN — padrão validado).
    pl_bt = tray_comp.constructionPlanes.createInput()
    pl_bt.setByOffset(
        tray_comp.yZConstructionPlane,
        adsk.core.ValueInput.createByReal(0.75)
    )
    pl_btf = tray_comp.constructionPlanes.add(pl_bt)
    sk_bt = tray_comp.sketches.add(pl_btf)

    p_i0 = sk_bt.modelToSketchSpace(
        adsk.core.Point3D.create(0.75, 0.0, 0.95))
    p_i1 = sk_bt.modelToSketchSpace(
        adsk.core.Point3D.create(0.75, 0.95, 0.0))
    p_i2 = sk_bt.modelToSketchSpace(
        adsk.core.Point3D.create(0.75, 1.9, 0.95))
    p_o0 = sk_bt.modelToSketchSpace(
        adsk.core.Point3D.create(0.75, -0.15, 0.95))
    p_o1 = sk_bt.modelToSketchSpace(
        adsk.core.Point3D.create(0.75, 0.95, -0.15))
    p_o2 = sk_bt.modelToSketchSpace(
        adsk.core.Point3D.create(0.75, 2.05, 0.95))

    sk_bt.sketchCurves.sketchArcs.addByThreePoints(p_i0, p_i1, p_i2)
    sk_bt.sketchCurves.sketchArcs.addByThreePoints(p_o0, p_o1, p_o2)
    sk_bt.sketchCurves.sketchLines.addByTwoPoints(p_o0, p_i0)
    sk_bt.sketchCurves.sketchLines.addByTwoPoints(p_i2, p_o2)

    e_bt = tray_comp.features.extrudeFeatures.createInput(
        sk_bt.profiles.item(0),
        adsk.fusion.FeatureOperations.JoinFeatureOperation
    )
    e_bt.setDistanceExtent(
        False,
        adsk.core.ValueInput.createByReal(bat_l)
    )
    tray_comp.features.extrudeFeatures.add(e_bt)

    # Tampas das extremidades do berço (45 mm de altura, fecham as pontas).
    for x0, x1 in ((0.6, 0.75), (0.75 + bat_l, 0.75 + bat_l + 0.15)):
        sk_cap = tray_comp.sketches.add(tray_comp.xYConstructionPlane)
        sk_cap.sketchCurves.sketchLines.addTwoPointRectangle(
            adsk.core.Point3D.create(x0, 0, 0),
            adsk.core.Point3D.create(x1, 2.05, 0)
        )
        e_cap = tray_comp.features.extrudeFeatures.createInput(
            sk_cap.profiles.item(0),
            adsk.fusion.FeatureOperations.JoinFeatureOperation
        )
        e_cap.startExtent = adsk.fusion.OffsetStartDefinition.create(
            adsk.core.ValueInput.createByReal(-flo_th)
        )
        e_cap.setDistanceExtent(
            False,
            adsk.core.ValueInput.createByReal(2.05 + flo_th)
        )
        tray_comp.features.extrudeFeatures.add(e_cap)

    # Anel do buzzer.
    for cx, cy, ri, ro, h in [buz_ring]:
        sk_r = tray_comp.sketches.add(tray_comp.xYConstructionPlane)
        sk_r.sketchCurves.sketchCircles.addByCenterRadius(
            adsk.core.Point3D.create(cx, cy, 0), ro)
        sk_r.sketchCurves.sketchCircles.addByCenterRadius(
            adsk.core.Point3D.create(cx, cy, 0), ri)
        p_r = pegar_anel(sk_r)
        if p_r:
            e_r = tray_comp.features.extrudeFeatures.createInput(
                p_r,
                adsk.fusion.FeatureOperations.JoinFeatureOperation
            )
            e_r.startExtent = adsk.fusion.OffsetStartDefinition.create(
                adsk.core.ValueInput.createByReal(-flo_th)
            )
            e_r.setDistanceExtent(
                False,
                adsk.core.ValueInput.createByReal(h + flo_th)
            )
            tray_comp.features.extrudeFeatures.add(e_r)

    # Bossas de fixação da bandeja na tampa (parafuso M2 nos acessórios).
    for bx, by in boss_xy:
        sk_b = tray_comp.sketches.add(tray_comp.xYConstructionPlane)
        sk_b.sketchCurves.sketchCircles.addByCenterRadius(
            adsk.core.Point3D.create(bx, by, 0), 0.09)
        e_b = tray_comp.features.extrudeFeatures.createInput(
            sk_b.profiles.item(0),
            adsk.fusion.FeatureOperations.CutFeatureOperation
        )
        e_b.setDistanceExtent(
            False,
            adsk.core.ValueInput.createByReal(-0.4)
        )
        tray_comp.features.extrudeFeatures.add(e_b)

        sk_b = tray_comp.sketches.add(tray_comp.xYConstructionPlane)
        sk_b.sketchCurves.sketchCircles.addByCenterRadius(
            adsk.core.Point3D.create(bx, by, 0), 0.16)
        sk_b.sketchCurves.sketchCircles.addByCenterRadius(
            adsk.core.Point3D.create(bx, by, 0), 0.09)
        p_b = pegar_anel(sk_b)
        if p_b:
            e_b = tray_comp.features.extrudeFeatures.createInput(
                p_b,
                adsk.fusion.FeatureOperations.JoinFeatureOperation
            )
            e_b.startExtent = adsk.fusion.OffsetStartDefinition.create(
                adsk.core.ValueInput.createByReal(-flo_th)
            )
            e_b.setDistanceExtent(
                False,
                adsk.core.ValueInput.createByReal(-1.1)
            )
            tray_comp.features.extrudeFeatures.add(e_b)

    # Furos-guia M2 na tampa, na LAJE SUPERIOR (a tampa é oca de 0 até
    # lid_depth-wall; a laje superior lid_depth-wall..lid_depth é o único
    # material sob as bossas). Perfil DENTRO da laje — padrão validado.
    pl_lidp = t_comp.constructionPlanes.createInput()
    pl_lidp.setByOffset(
        t_comp.xYConstructionPlane,
        adsk.core.ValueInput.createByReal(lid_depth - wall * 0.5)
    )
    pl_lidpf = t_comp.constructionPlanes.add(pl_lidp)
    sk_lidp = t_comp.sketches.add(pl_lidpf)
    for bx, by in boss_xy:
        pc = sk_lidp.modelToSketchSpace(
            adsk.core.Point3D.create(
                wall + tol + bx,
                wall + tol + by,
                lid_depth - wall * 0.5
            )
        )
        sk_lidp.sketchCurves.sketchCircles.addByCenterRadius(pc, 0.07)
    for p in sk_lidp.profiles:
        e_lidp = t_comp.features.extrudeFeatures.createInput(
            p,
            adsk.fusion.FeatureOperations.CutFeatureOperation
        )
        e_lidp.setDistanceExtent(
            False,
            adsk.core.ValueInput.createByReal(-0.2)
        )
        t_comp.features.extrudeFeatures.add(e_lidp)

    # Posiciona bandeja presa na tampa.
    transform_tr = adsk.core.Matrix3D.create()
    transform_tr.translation = adsk.core.Vector3D.create(
        wall + tol,
        wall + tol,
        1.6
    )
    tray_occ.transform = transform_tr

    # ==========================================================
    # 4. ACESSÓRIOS
    # ==========================================================

    if acessorios:
        # Pino da dobradiça.
        pin_occ = root.occurrences.addNewComponent(adsk.core.Matrix3D.create())
        pin_comp = pin_occ.component
        pin_comp.name = "Maleta_Pino_Dobradica"

        pl_in = pin_comp.constructionPlanes.createInput()
        pl_in.setByOffset(
            pin_comp.yZConstructionPlane,
            adsk.core.ValueInput.createByReal(length * 0.5)
        )
        pl_f = pin_comp.constructionPlanes.add(pl_in)
        sk_pin = pin_comp.sketches.add(pl_f)

        pc_pin = sk_pin.modelToSketchSpace(
            adsk.core.Point3D.create(length * 0.5, width + 0.15, 0.05)
        )

        sk_pin.sketchCurves.sketchCircles.addByCenterRadius(pc_pin, 0.21)

        e_pin = pin_comp.features.extrudeFeatures.createInput(
            sk_pin.profiles.item(0),
            adsk.fusion.FeatureOperations.NewBodyFeatureOperation
        )
        e_pin.setSymmetricExtent(
            adsk.core.ValueInput.createByReal(length * 0.35),
            True
        )
        pin_comp.features.extrudeFeatures.add(e_pin)

        # Cabeça do pino.
        pl_in = pin_comp.constructionPlanes.createInput()
        pl_in.setByOffset(
            pin_comp.yZConstructionPlane,
            adsk.core.ValueInput.createByReal(length * 0.15 + 0.2)
        )
        pl_f = pin_comp.constructionPlanes.add(pl_in)
        sk_pinh = pin_comp.sketches.add(pl_f)

        pc_pinh = sk_pinh.modelToSketchSpace(
            adsk.core.Point3D.create(length * 0.15 + 0.2, width + 0.15, 0.05)
        )

        sk_pinh.sketchCurves.sketchCircles.addByCenterRadius(pc_pinh, 0.3)

        e_pinh = pin_comp.features.extrudeFeatures.createInput(
            sk_pinh.profiles.item(0),
            adsk.fusion.FeatureOperations.JoinFeatureOperation
        )
        e_pinh.setSymmetricExtent(
            adsk.core.ValueInput.createByReal(0.2),
            True
        )
        pin_comp.features.extrudeFeatures.add(e_pinh)

        # Cabeça do pino (outra ponta — impede o pino de escapar).
        pl_in = pin_comp.constructionPlanes.createInput()
        pl_in.setByOffset(
            pin_comp.yZConstructionPlane,
            adsk.core.ValueInput.createByReal(length * 0.85 - 0.2)
        )
        pl_f = pin_comp.constructionPlanes.add(pl_in)
        sk_pinh2 = pin_comp.sketches.add(pl_f)

        pc_pinh2 = sk_pinh2.modelToSketchSpace(
            adsk.core.Point3D.create(length * 0.85 - 0.2, width + 0.15, 0.05)
        )

        sk_pinh2.sketchCurves.sketchCircles.addByCenterRadius(pc_pinh2, 0.3)

        e_pinh2 = pin_comp.features.extrudeFeatures.createInput(
            sk_pinh2.profiles.item(0),
            adsk.fusion.FeatureOperations.JoinFeatureOperation
        )
        e_pinh2.setSymmetricExtent(
            adsk.core.ValueInput.createByReal(0.2),
            True
        )
        pin_comp.features.extrudeFeatures.add(e_pinh2)

        # Pés TPU.
        feet_occ = root.occurrences.addNewComponent(adsk.core.Matrix3D.create())
        feet_comp = feet_occ.component
        feet_comp.name = "Maleta_Pes_TPU"

        sk_ft = feet_comp.sketches.add(feet_comp.xYConstructionPlane)

        for fx, fy in posicoes_pes:
            sk_ft.sketchCurves.sketchCircles.addByCenterRadius(
                adsk.core.Point3D.create(fx, fy, 0),
                0.4
            )

        col_ft = adsk.core.ObjectCollection.create()
        for p in sk_ft.profiles:
            col_ft.add(p)

        if col_ft.count > 0:
            e_ft = feet_comp.features.extrudeFeatures.createInput(
                col_ft,
                adsk.fusion.FeatureOperations.NewBodyFeatureOperation
            )
            e_ft.setDistanceExtent(
                False,
                adsk.core.ValueInput.createByReal(0.5)
            )
            feet_comp.features.extrudeFeatures.add(e_ft)

        mat_ft = adsk.core.Matrix3D.create()
        mat_ft.translation = adsk.core.Vector3D.create(0, 0, -base_depth)
        feet_occ.transform = mat_ft

        # Parafusos M3 da ESP32 (4) — cabeças sobre os standoffs, eixos
        # descendo para dentro do furo do inserto (perfil DENTRO da
        # cabeça + corte para baixo — padrão validado).
        s_occ = root.occurrences.addNewComponent(adsk.core.Matrix3D.create())
        s_comp = s_occ.component
        s_comp.name = "Maleta_Parafusos_ESP32"
        mat_s = adsk.core.Matrix3D.create()
        mat_s.translation = adsk.core.Vector3D.create(0, 0, 1.6 + ss_h - 0.02)
        s_occ.transform = mat_s

        sk_hd = s_comp.sketches.add(s_comp.xYConstructionPlane)
        col_hd = adsk.core.ObjectCollection.create()
        for sx, sy in esp_ss:
            sk_hd.sketchCurves.sketchCircles.addByCenterRadius(
                adsk.core.Point3D.create(
                    wall + tol + sx, wall + tol + sy, 0), 0.28)
        for p in sk_hd.profiles:
            col_hd.add(p)
        if col_hd.count > 0:
            e_hd = s_comp.features.extrudeFeatures.createInput(
                col_hd,
                adsk.fusion.FeatureOperations.NewBodyFeatureOperation
            )
            e_hd.setDistanceExtent(
                False,
                adsk.core.ValueInput.createByReal(0.1)
            )
            s_comp.features.extrudeFeatures.add(e_hd)

        pl_sh = s_comp.constructionPlanes.createInput()
        pl_sh.setByOffset(
            s_comp.xYConstructionPlane,
            adsk.core.ValueInput.createByReal(0.05)
        )
        pl_shf = s_comp.constructionPlanes.add(pl_sh)
        sk_sh = s_comp.sketches.add(pl_shf)
        col_sh = adsk.core.ObjectCollection.create()
        for sx, sy in esp_ss:
            pc = sk_sh.modelToSketchSpace(
                adsk.core.Point3D.create(
                    wall + tol + sx, wall + tol + sy, 0.05)
            )
            sk_sh.sketchCurves.sketchCircles.addByCenterRadius(pc, 0.15)
        for p in sk_sh.profiles:
            col_sh.add(p)
        if col_sh.count > 0:
            e_sh = s_comp.features.extrudeFeatures.createInput(
                col_sh,
                adsk.fusion.FeatureOperations.JoinFeatureOperation
            )
            e_sh.setDistanceExtent(
                False,
                adsk.core.ValueInput.createByReal(-0.9)
            )
            s_comp.features.extrudeFeatures.add(e_sh)

        # Parafusos M2 da bandeja na tampa (4).
        scr_occ = root.occurrences.addNewComponent(adsk.core.Matrix3D.create())
        scr_comp = scr_occ.component
        scr_comp.name = "Maleta_Parafusos_Bandeja"
        mat_scr = adsk.core.Matrix3D.create()
        mat_scr.translation = adsk.core.Vector3D.create(0, 0, 1.6)
        scr_occ.transform = mat_scr

        sk_head = scr_comp.sketches.add(scr_comp.xYConstructionPlane)
        col_head = adsk.core.ObjectCollection.create()
        for bx, by in boss_xy:
            sk_head.sketchCurves.sketchCircles.addByCenterRadius(
                adsk.core.Point3D.create(
                    wall + tol + bx, wall + tol + by, 0
                ), 0.18)
        for p in sk_head.profiles:
            col_head.add(p)
        if col_head.count > 0:
            e_head = scr_comp.features.extrudeFeatures.createInput(
                col_head,
                adsk.fusion.FeatureOperations.NewBodyFeatureOperation
            )
            e_head.setDistanceExtent(
                False,
                adsk.core.ValueInput.createByReal(0.08)
            )
            scr_comp.features.extrudeFeatures.add(e_head)

        # Eixos M2: perfil DENTRO da cabeça (0.01 abaixo do topo) e corte
        # descendo — padrão validado (perfil na face + corte p/ dentro não
        # acha corpo alvo).
        pl_sh = scr_comp.constructionPlanes.createInput()
        pl_sh.setByOffset(
            scr_comp.xYConstructionPlane,
            adsk.core.ValueInput.createByReal(0.07)
        )
        pl_shf = scr_comp.constructionPlanes.add(pl_sh)
        sk_sh = scr_comp.sketches.add(pl_shf)
        col_sh = adsk.core.ObjectCollection.create()
        for bx, by in boss_xy:
            pc = sk_sh.modelToSketchSpace(
                adsk.core.Point3D.create(
                    wall + tol + bx, wall + tol + by, 0.07
                )
            )
            sk_sh.sketchCurves.sketchCircles.addByCenterRadius(pc, 0.09)
        for p in sk_sh.profiles:
            col_sh.add(p)
        if col_sh.count > 0:
            e_sh = scr_comp.features.extrudeFeatures.createInput(
                col_sh,
                adsk.fusion.FeatureOperations.JoinFeatureOperation
            )
            e_sh.setDistanceExtent(
                False,
                adsk.core.ValueInput.createByReal(-1.51)
            )
            scr_comp.features.extrudeFeatures.add(e_sh)

    # Exportação automática.
    if exportar:
        exportar_stls(root, design)

    # Visualização da tampa aberta.
    if abrir_tampa:
        simular_abertura(
            t_occ,
            tray_occ,
            transform_tr,
            length,
            width
        )


# ============================================================
# INTERFACE DE USUÁRIO
# ============================================================

class ToolboxCommandExecuteHandler(adsk.core.CommandEventHandler):
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
            display = inputs.itemById('display').value

            gerar_maleta(
                length,
                width,
                base_depth,
                lid_depth,
                wall,
                acessorios,
                material,
                abrir_tampa,
                exportar,
                display
            )

        except:
            app = adsk.core.Application.get()
            app.userInterface.messageBox(traceback.format_exc())


class ToolboxCommandChangedHandler(adsk.core.InputChangedEventHandler):
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
            adsk.core.Application.get().userInterface.messageBox(
                traceback.format_exc()
            )


class ToolboxCommandDestroyHandler(adsk.core.CommandEventHandler):
    def notify(self, args):
        adsk.terminate()


class ToolboxCommandCreatedHandler(adsk.core.CommandCreatedEventHandler):
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

            v_len = inputs.addValueInput(
                'length',
                'Comprimento (cm)',
                'cm',
                adsk.core.ValueInput.createByReal(24.0)
            )
            v_len.minimum = 10.0
            v_len.maximum = 60.0

            v_wid = inputs.addValueInput(
                'width',
                'Largura (cm)',
                'cm',
                adsk.core.ValueInput.createByReal(11.0)
            )
            v_wid.minimum = 6.0
            v_wid.maximum = 30.0

            v_bd = inputs.addValueInput(
                'base_depth',
                'Profundidade da Base (cm)',
                'cm',
                adsk.core.ValueInput.createByReal(6.0)
            )
            v_bd.minimum = 3.0
            v_bd.maximum = 15.0

            v_ld = inputs.addValueInput(
                'lid_depth',
                'Profundidade da Tampa (cm)',
                'cm',
                adsk.core.ValueInput.createByReal(3.0)
            )
            v_ld.minimum = 1.5
            v_ld.maximum = 10.0

            v_wall = inputs.addValueInput(
                'wall',
                'Espessura da Parede (cm)',
                'cm',
                adsk.core.ValueInput.createByReal(0.25)
            )
            v_wall.minimum = 0.15
            v_wall.maximum = 0.6

            material = inputs.addDropDownCommandInput('material', 'Material', 0)
            material.listItems.add('PETG', True)
            material.listItems.add('ABS', False)
            material.listItems.add('PLA', False)
            material.listItems.add('TPU', False)

            inputs.addBoolValueInput(
                'acessorios',
                'Gerar acessórios (pino, pés TPU)',
                True,
                '',
                True
            )

            inputs.addBoolValueInput(
                'display',
                'Display LCD 2.4" no topo da tampa',
                True,
                '',
                False
            )

            inputs.addBoolValueInput(
                'exportar',
                'Exportar STL após gerar',
                True,
                '',
                True
            )

            inputs.addBoolValueInput(
                'abrir_tampa',
                'Visualizar tampa aberta (100°)',
                True,
                '',
                False
            )

        except:
            app = adsk.core.Application.get()
            app.userInterface.messageBox(traceback.format_exc())


def run(context):
    ui = None
    try:
        app = adsk.core.Application.get()
        ui = app.userInterface

        cmdDef = ui.commandDefinitions.itemById('SmartToolboxUI')
        if not cmdDef:
            cmdDef = ui.commandDefinitions.addButtonDefinition(
                'SmartToolboxUI',
                'Configurar Maleta 3D',
                'Gera a maleta com UI'
            )

        onCommandCreated = ToolboxCommandCreatedHandler()
        cmdDef.commandCreated.add(onCommandCreated)
        handlers.append(onCommandCreated)

        cmdDef.execute()
        adsk.autoTerminate(False)

    except:
        if ui:
            ui.messageBox('Erro Inicial:\n{}'.format(traceback.format_exc()))