# TrakrScannerUI_Pro.py
# Trakr: Scanner UHF Portátil - Engenharia DFM Blindada e Linha do Tempo Corrigida
# Unidades: cm (padrão do Fusion 360).

import adsk.core
import adsk.fusion
import traceback

handlers = []

# ============================================================
# HELPERS MATEMÁTICOS DE GEOMETRIA
# ============================================================

def desenhar_retangulo_arredondado(sketch, x1, y1, x2, y2, r):
    if r <= 0.01:
        sketch.sketchCurves.sketchLines.addTwoPointRectangle(
            adsk.core.Point3D.create(x1, y1, 0), adsk.core.Point3D.create(x2, y2, 0)
        )
        return
        
    lines = sketch.sketchCurves.sketchLines
    arcs = sketch.sketchCurves.sketchArcs
    
    l1 = lines.addByTwoPoints(adsk.core.Point3D.create(x1+r, y1, 0), adsk.core.Point3D.create(x2-r, y1, 0))
    l2 = lines.addByTwoPoints(adsk.core.Point3D.create(x2, y1+r, 0), adsk.core.Point3D.create(x2, y2-r, 0))
    l3 = lines.addByTwoPoints(adsk.core.Point3D.create(x2-r, y2, 0), adsk.core.Point3D.create(x1+r, y2, 0))
    l4 = lines.addByTwoPoints(adsk.core.Point3D.create(x1, y2-r, 0), adsk.core.Point3D.create(x1, y1+r, 0))
    
    arcs.addByCenterStartEnd(adsk.core.Point3D.create(x2-r, y1+r, 0), l1.endSketchPoint, l2.startSketchPoint)
    arcs.addByCenterStartEnd(adsk.core.Point3D.create(x2-r, y2-r, 0), l2.endSketchPoint, l3.startSketchPoint)
    arcs.addByCenterStartEnd(adsk.core.Point3D.create(x1+r, y2-r, 0), l3.endSketchPoint, l4.startSketchPoint)
    arcs.addByCenterStartEnd(adsk.core.Point3D.create(x1+r, y1+r, 0), l4.endSketchPoint, l1.startSketchPoint)

def desenhar_slot_3d(sketch, center_x, center_y, center_z, w_global_y, h_global_z):
    r = h_global_z / 2.0
    c1_3d = adsk.core.Point3D.create(center_x, center_y - (w_global_y/2.0) + r, center_z)
    c2_3d = adsk.core.Point3D.create(center_x, center_y + (w_global_y/2.0) - r, center_z)
    p1_top_3d = adsk.core.Point3D.create(center_x, center_y - (w_global_y/2.0) + r, center_z + r)
    p2_top_3d = adsk.core.Point3D.create(center_x, center_y + (w_global_y/2.0) - r, center_z + r)
    p1_bot_3d = adsk.core.Point3D.create(center_x, center_y - (w_global_y/2.0) + r, center_z - r)
    p2_bot_3d = adsk.core.Point3D.create(center_x, center_y + (w_global_y/2.0) - r, center_z - r)
    
    c1 = sketch.modelToSketchSpace(c1_3d)
    c2 = sketch.modelToSketchSpace(c2_3d)
    p1_top = sketch.modelToSketchSpace(p1_top_3d)
    p2_top = sketch.modelToSketchSpace(p2_top_3d)
    p1_bot = sketch.modelToSketchSpace(p1_bot_3d)
    p2_bot = sketch.modelToSketchSpace(p2_bot_3d)
    
    sketch.sketchCurves.sketchCircles.addByCenterRadius(c1, r)
    sketch.sketchCurves.sketchCircles.addByCenterRadius(c2, r)
    sketch.sketchCurves.sketchLines.addByTwoPoints(p1_top, p2_top)
    sketch.sketchCurves.sketchLines.addByTwoPoints(p1_bot, p2_bot)

def pegar_anel(sketch):
    if sketch.profiles.count == 0: return None
    for p in sketch.profiles:
        if p.profileLoops.count == 2:
            return p
    return sketch.profiles.item(0)

def criar_boss_parafuso(comp, plane, extrudes, x, y, start_z, height, d_ext, d_int, op):
    sk = comp.sketches.add(plane)
    sk.sketchCurves.sketchCircles.addByCenterRadius(adsk.core.Point3D.create(x, y, 0), d_ext / 2.0)
    sk.sketchCurves.sketchCircles.addByCenterRadius(adsk.core.Point3D.create(x, y, 0), d_int / 2.0)
    anel = pegar_anel(sk)
    if anel:
        inp = extrudes.createInput(anel, op)
        inp.startExtent = adsk.fusion.OffsetStartDefinition.create(adsk.core.ValueInput.createByReal(start_z))
        inp.setDistanceExtent(False, adsk.core.ValueInput.createByReal(height))
        extrudes.add(inp)

def criar_torre_ancorada(comp, plane, extrudes, x, y, length, width, wall, start_z, height, d_ext, d_int):
    sk_solid = comp.sketches.add(plane)
    sk_solid.sketchCurves.sketchCircles.addByCenterRadius(adsk.core.Point3D.create(x, y, 0), d_ext / 2.0)
    
    wx = wall if x < length / 2 else length - wall
    sk_solid.sketchCurves.sketchLines.addTwoPointRectangle(
        adsk.core.Point3D.create(x, y - 0.15, 0), adsk.core.Point3D.create(wx, y + 0.15, 0)
    )
    
    wy = wall if y < width / 2 else width - wall
    sk_solid.sketchCurves.sketchLines.addTwoPointRectangle(
        adsk.core.Point3D.create(x - 0.15, y, 0), adsk.core.Point3D.create(x + 0.15, wy, 0)
    )
    
    col_solid = adsk.core.ObjectCollection.create()
    for p in sk_solid.profiles: col_solid.add(p)
    
    if col_solid.count > 0:
        inp_solid = extrudes.createInput(col_solid, adsk.fusion.FeatureOperations.JoinFeatureOperation)
        inp_solid.startExtent = adsk.fusion.OffsetStartDefinition.create(adsk.core.ValueInput.createByReal(start_z))
        inp_solid.setDistanceExtent(False, adsk.core.ValueInput.createByReal(height))
        extrudes.add(inp_solid)
        
    sk_hole = comp.sketches.add(plane)
    sk_hole.sketchCurves.sketchCircles.addByCenterRadius(adsk.core.Point3D.create(x, y, 0), d_int / 2.0)
    
    if sk_hole.profiles.count > 0:
        inp_hole = extrudes.createInput(sk_hole.profiles.item(0), adsk.fusion.FeatureOperations.CutFeatureOperation)
        inp_hole.startExtent = adsk.fusion.OffsetStartDefinition.create(adsk.core.ValueInput.createByReal(start_z - 0.1))
        inp_hole.setDistanceExtent(False, adsk.core.ValueInput.createByReal(height + 0.2))
        extrudes.add(inp_hole)

def criar_parede_bolso(comp, extrudes, x, y, l, w, start_z, altura, espessura=0.15):
    sk = comp.sketches.add(comp.xYConstructionPlane)
    sk.sketchCurves.sketchLines.addTwoPointRectangle(
        adsk.core.Point3D.create(x, y, 0), adsk.core.Point3D.create(x + l + (espessura * 2), y + w + (espessura * 2), 0)
    )
    sk.sketchCurves.sketchLines.addTwoPointRectangle(
        adsk.core.Point3D.create(x + espessura, y + espessura, 0), adsk.core.Point3D.create(x + l + espessura, y + w + espessura, 0)
    )
    anel = pegar_anel(sk)
    if anel:
        inp = extrudes.createInput(anel, adsk.fusion.FeatureOperations.JoinFeatureOperation)
        inp.startExtent = adsk.fusion.OffsetStartDefinition.create(adsk.core.ValueInput.createByReal(start_z))
        inp.setDistanceExtent(False, adsk.core.ValueInput.createByReal(altura))
        extrudes.add(inp)

def criar_passa_cabo(comp, extrudes, x, y, l, w, start_z, depth):
    sk = comp.sketches.add(comp.xYConstructionPlane)
    sk.sketchCurves.sketchLines.addTwoPointRectangle(
        adsk.core.Point3D.create(x, y, 0), adsk.core.Point3D.create(x + l, y + w, 0)
    )
    inp = extrudes.createInput(sk.profiles.item(0), adsk.fusion.FeatureOperations.CutFeatureOperation)
    inp.startExtent = adsk.fusion.OffsetStartDefinition.create(adsk.core.ValueInput.createByReal(start_z))
    inp.setDistanceExtent(False, adsk.core.ValueInput.createByReal(depth))
    extrudes.add(inp)

# ============================================================
# LÓGICA DE GERAÇÃO 3D 
# ============================================================
def gerar_scanner(length, width, depth_base, depth_top, wall, fillet_r):
    app = adsk.core.Application.get()
    design = app.activeProduct
    root = design.rootComponent

    # ==========================================================
    # 1. BASE (Concha Inferior)
    # ==========================================================
    b_occ = root.occurrences.addNewComponent(adsk.core.Matrix3D.create())
    b_comp = b_occ.component
    b_comp.name = "Trakr_Scanner_Base"

    # -- A. CARCAÇA EXTERNA E INTERNA --
    sk_b = b_comp.sketches.add(b_comp.xYConstructionPlane)
    desenhar_retangulo_arredondado(sk_b, 0, 0, length, width, fillet_r)
    ext_b = b_comp.features.extrudeFeatures.createInput(sk_b.profiles.item(0), adsk.fusion.FeatureOperations.NewBodyFeatureOperation)
    ext_b.setDistanceExtent(False, adsk.core.ValueInput.createByReal(-depth_base))
    b_comp.features.extrudeFeatures.add(ext_b)

    sk_in = b_comp.sketches.add(b_comp.xYConstructionPlane)
    desenhar_retangulo_arredondado(sk_in, wall, wall, length - wall, width - wall, fillet_r - wall)
    e_in = b_comp.features.extrudeFeatures.createInput(sk_in.profiles.item(0), adsk.fusion.FeatureOperations.CutFeatureOperation)
    e_in.setDistanceExtent(False, adsk.core.ValueInput.createByReal(-(depth_base - wall)))
    b_comp.features.extrudeFeatures.add(e_in)

    lip_t = wall / 2.0
    sk_lip = b_comp.sketches.add(b_comp.xYConstructionPlane)
    desenhar_retangulo_arredondado(sk_lip, wall - lip_t, wall - lip_t, length - wall + lip_t, width - wall + lip_t, fillet_r - wall + lip_t)
    desenhar_retangulo_arredondado(sk_lip, wall, wall, length - wall, width - wall, fillet_r - wall)
    p_lip = pegar_anel(sk_lip)
    if p_lip:
        e_lip = b_comp.features.extrudeFeatures.createInput(p_lip, adsk.fusion.FeatureOperations.JoinFeatureOperation)
        e_lip.startExtent = adsk.fusion.OffsetStartDefinition.create(adsk.core.ValueInput.createByReal(-0.1))
        e_lip.setDistanceExtent(False, adsk.core.ValueInput.createByReal(0.2))
        b_comp.features.extrudeFeatures.add(e_lip)

    z_start_internal = -(depth_base - wall)

    # -- B. TORRES DE PARAFUSO --
    boss_pos = [
        (fillet_r, fillet_r), (length - fillet_r, fillet_r),
        (fillet_r, width - fillet_r), (length - fillet_r, width - fillet_r)
    ]
    for bx, by in boss_pos:
        criar_torre_ancorada(b_comp, b_comp.xYConstructionPlane, b_comp.features.extrudeFeatures, 
                            bx, by, length, width, wall, z_start_internal, depth_base-wall, 0.9, 0.42)

    # -- C. BLOCO SÓLIDO LANYARD --
    sk_lan = b_comp.sketches.add(b_comp.xYConstructionPlane)
    sk_lan.sketchCurves.sketchLines.addTwoPointRectangle(
        adsk.core.Point3D.create(1.6, width - 1.2, 0), adsk.core.Point3D.create(3.2, width, 0)
    )
    e_lan = b_comp.features.extrudeFeatures.createInput(sk_lan.profiles.item(0), adsk.fusion.FeatureOperations.JoinFeatureOperation)
    e_lan.startExtent = adsk.fusion.OffsetStartDefinition.create(adsk.core.ValueInput.createByReal(z_start_internal))
    e_lan.setDistanceExtent(False, adsk.core.ValueInput.createByReal(depth_base - wall))
    b_comp.features.extrudeFeatures.add(e_lan)

    # -- D. BOLSOS INTERNOS (CRIADOS ANTES DOS CORTES) --
    criar_parede_bolso(b_comp, b_comp.features.extrudeFeatures, 0.2, width/2 - 0.875, 2.85, 1.75, z_start_internal, 0.5)
    criar_parede_bolso(b_comp, b_comp.features.extrudeFeatures, 3.5, 0.6, 5.21, 2.60, z_start_internal, 0.8)
    criar_parede_bolso(b_comp, b_comp.features.extrudeFeatures, 3.5, 3.6, 6.60, 1.90, z_start_internal, 0.9)
    criar_parede_bolso(b_comp, b_comp.features.extrudeFeatures, 10.5, (width / 2.0) - 1.525, 4.05, 3.05, z_start_internal, 0.65)
    criar_parede_bolso(b_comp, b_comp.features.extrudeFeatures, 14.8, (width / 2.0) - 1.29, 2.58, 2.58, z_start_internal, 0.3)

    # -- E. PASSADORES DE CABO --
    criar_passa_cabo(b_comp, b_comp.features.extrudeFeatures, 2.5, 3.5, 1.0, 0.5, z_start_internal, 0.6)
    criar_passa_cabo(b_comp, b_comp.features.extrudeFeatures, 5.0, 3.2, 0.5, 1.0, z_start_internal, 0.6)
    criar_passa_cabo(b_comp, b_comp.features.extrudeFeatures, 8.5, width/2 - 0.5, 2.5, 1.0, z_start_internal, 0.6)
    criar_passa_cabo(b_comp, b_comp.features.extrudeFeatures, 14.5, width/2 - 0.3, 0.5, 0.6, z_start_internal, 0.8)

    # -- F. CORTES GLOBAIS (USB-C, LED, Lanyard Furos e Ventilação) --
    # Tudo abaixo corta material. Colocado no final para perfurar os bolsos.

    # Corte do USB-C e LED (Blindado via Plano Global YZ em X=0)
    sk_usb = b_comp.sketches.add(b_comp.yZConstructionPlane)
    cy_usb = width / 2.0
    cz_usb = z_start_internal + 0.28
    w_usb = 0.9
    h_usb = 0.32
    
    desenhar_slot_3d(sk_usb, 0.0, cy_usb, cz_usb, w_usb, h_usb)
    
    p_led = sk_usb.modelToSketchSpace(adsk.core.Point3D.create(0.0, cy_usb + 0.7, cz_usb))
    sk_usb.sketchCurves.sketchCircles.addByCenterRadius(p_led, 0.075) 
    
    col_usb = adsk.core.ObjectCollection.create()
    for p in sk_usb.profiles: col_usb.add(p)
    if col_usb.count > 0:
        e_usb = b_comp.features.extrudeFeatures.createInput(col_usb, adsk.fusion.FeatureOperations.CutFeatureOperation)
        e_usb.setSymmetricExtent(adsk.core.ValueInput.createByReal(4.0), True) # Perfora da origem até X=4.0
        b_comp.features.extrudeFeatures.add(e_usb)

    # Cortes do Lanyard (Blindado via Plano Global XZ em Y=0)
    sk_h = b_comp.sketches.add(b_comp.xZConstructionPlane)
    p1 = sk_h.modelToSketchSpace(adsk.core.Point3D.create(2.0, width, z_start_internal + 0.6))
    sk_h.sketchCurves.sketchCircles.addByCenterRadius(p1, 0.15) 
    p2 = sk_h.modelToSketchSpace(adsk.core.Point3D.create(2.8, width, z_start_internal + 0.6))
    sk_h.sketchCurves.sketchCircles.addByCenterRadius(p2, 0.15) 
    
    col_h = adsk.core.ObjectCollection.create()
    for p in sk_h.profiles: col_h.add(p)
    if col_h.count > 0:
        e_h = b_comp.features.extrudeFeatures.createInput(col_h, adsk.fusion.FeatureOperations.CutFeatureOperation)
        e_h.setSymmetricExtent(adsk.core.ValueInput.createByReal(width + 2.0), True) # Fura a maleta inteira no eixo Y
        b_comp.features.extrudeFeatures.add(e_h)

    # Curva Interna U-Turn do Lanyard
    sk_c = b_comp.sketches.add(b_comp.xYConstructionPlane)
    sk_c.sketchCurves.sketchLines.addTwoPointRectangle(
        adsk.core.Point3D.create(1.9, width - 0.9, 0), adsk.core.Point3D.create(2.9, width - 0.5, 0)
    )
    e_c = b_comp.features.extrudeFeatures.createInput(sk_c.profiles.item(0), adsk.fusion.FeatureOperations.CutFeatureOperation)
    e_c.startExtent = adsk.fusion.OffsetStartDefinition.create(adsk.core.ValueInput.createByReal(z_start_internal + 0.4))
    e_c.setDistanceExtent(False, adsk.core.ValueInput.createByReal(0.4))
    b_comp.features.extrudeFeatures.add(e_c)

    # Grelha de Ventilação
    sk_vent = b_comp.sketches.add(b_comp.xYConstructionPlane)
    for i in range(4): 
        vx = 1.0 + (i * 0.4)
        sk_vent.sketchCurves.sketchLines.addTwoPointRectangle(adsk.core.Point3D.create(vx, 1.5, 0), adsk.core.Point3D.create(vx + 0.15, width - 1.5, 0))
    for i in range(8): 
        vx = 9.0 + (i * 0.4)
        sk_vent.sketchCurves.sketchLines.addTwoPointRectangle(adsk.core.Point3D.create(vx, 1.5, 0), adsk.core.Point3D.create(vx + 0.15, width - 1.5, 0))
    
    col_vent = adsk.core.ObjectCollection.create()
    for p in sk_vent.profiles: col_vent.add(p)
    if col_vent.count > 0:
        e_vent = b_comp.features.extrudeFeatures.createInput(col_vent, adsk.fusion.FeatureOperations.CutFeatureOperation)
        e_vent.startExtent = adsk.fusion.OffsetStartDefinition.create(adsk.core.ValueInput.createByReal(-depth_base - 0.1))
        e_vent.setDistanceExtent(False, adsk.core.ValueInput.createByReal(wall + 0.2))
        b_comp.features.extrudeFeatures.add(e_vent)


    # ==========================================================
    # 2. TAMPA (Concha Superior)
    # ==========================================================
    matrix_t = adsk.core.Matrix3D.create()
    matrix_t.translation = adsk.core.Vector3D.create(0, 0, 0.5)
    t_occ = root.occurrences.addNewComponent(matrix_t)
    t_comp = t_occ.component
    t_comp.name = "Trakr_Scanner_Top"

    sk_t = t_comp.sketches.add(t_comp.xYConstructionPlane)
    desenhar_retangulo_arredondado(sk_t, 0, 0, length, width, fillet_r)
    ext_t = t_comp.features.extrudeFeatures.createInput(sk_t.profiles.item(0), adsk.fusion.FeatureOperations.NewBodyFeatureOperation)
    ext_t.setDistanceExtent(False, adsk.core.ValueInput.createByReal(depth_top))
    t_comp.features.extrudeFeatures.add(ext_t)

    sk_in_t = t_comp.sketches.add(t_comp.xYConstructionPlane)
    desenhar_retangulo_arredondado(sk_in_t, wall, wall, length - wall, width - wall, fillet_r - wall)
    e_in_t = t_comp.features.extrudeFeatures.createInput(sk_in_t.profiles.item(0), adsk.fusion.FeatureOperations.CutFeatureOperation)
    e_in_t.setDistanceExtent(False, adsk.core.ValueInput.createByReal(depth_top - wall))
    t_comp.features.extrudeFeatures.add(e_in_t)

    sk_gr = t_comp.sketches.add(t_comp.xYConstructionPlane)
    desenhar_retangulo_arredondado(sk_gr, wall - lip_t - 0.02, wall - lip_t - 0.02, length - wall + lip_t + 0.02, width - wall + lip_t + 0.02, fillet_r - wall + lip_t + 0.02)
    desenhar_retangulo_arredondado(sk_gr, wall + 0.02, wall + 0.02, length - wall - 0.02, width - wall - 0.02, fillet_r - wall - 0.02)
    p_gr = pegar_anel(sk_gr)
    if p_gr:
        e_gr = t_comp.features.extrudeFeatures.createInput(p_gr, adsk.fusion.FeatureOperations.CutFeatureOperation)
        e_gr.setDistanceExtent(False, adsk.core.ValueInput.createByReal(0.25))
        t_comp.features.extrudeFeatures.add(e_gr)

    for bx, by in boss_pos:
        criar_torre_ancorada(t_comp, t_comp.xYConstructionPlane, t_comp.features.extrudeFeatures, 
                            bx, by, length, width, wall, 0, depth_top-wall, 0.9, 0.32)
        sk_hole = t_comp.sketches.add(t_comp.xYConstructionPlane)
        sk_hole.sketchCurves.sketchCircles.addByCenterRadius(adsk.core.Point3D.create(bx, by, 0), 0.3) 
        e_hole = t_comp.features.extrudeFeatures.createInput(sk_hole.profiles.item(0), adsk.fusion.FeatureOperations.CutFeatureOperation)
        e_hole.setDistanceExtent(False, adsk.core.ValueInput.createByReal(depth_top + 0.1))
        t_comp.features.extrudeFeatures.add(e_hole)

    btn_x, btn_y = 6.0, width / 2.0
    sk_btn_base = t_comp.sketches.add(t_comp.xYConstructionPlane)
    sk_btn_base.sketchCurves.sketchCircles.addByCenterRadius(adsk.core.Point3D.create(btn_x, btn_y, 0), 0.9)
    sk_btn_base.sketchCurves.sketchCircles.addByCenterRadius(adsk.core.Point3D.create(btn_x, btn_y, 0), 0.7)
    p_btn_base = pegar_anel(sk_btn_base)
    if p_btn_base:
        e_btn_base = t_comp.features.extrudeFeatures.createInput(p_btn_base, adsk.fusion.FeatureOperations.JoinFeatureOperation)
        e_btn_base.startExtent = adsk.fusion.OffsetStartDefinition.create(adsk.core.ValueInput.createByReal(0))
        e_btn_base.setDistanceExtent(False, adsk.core.ValueInput.createByReal(depth_top - wall))
        t_comp.features.extrudeFeatures.add(e_btn_base)
    
    sk_btn = t_comp.sketches.add(t_comp.xYConstructionPlane)
    sk_btn.sketchCurves.sketchCircles.addByCenterRadius(adsk.core.Point3D.create(btn_x, btn_y, 0), 0.5)
    e_btn = t_comp.features.extrudeFeatures.createInput(sk_btn.profiles.item(0), adsk.fusion.FeatureOperations.CutFeatureOperation)
    e_btn.startExtent = adsk.fusion.OffsetStartDefinition.create(adsk.core.ValueInput.createByReal(depth_top - wall - 0.1))
    e_btn.setDistanceExtent(False, adsk.core.ValueInput.createByReal(wall + 0.2))
    t_comp.features.extrudeFeatures.add(e_btn)

    led_x, led_y = 9.0, width / 2.0
    sk_led_base = t_comp.sketches.add(t_comp.xYConstructionPlane)
    sk_led_base.sketchCurves.sketchCircles.addByCenterRadius(adsk.core.Point3D.create(led_x, led_y, 0), 0.4)
    sk_led_base.sketchCurves.sketchCircles.addByCenterRadius(adsk.core.Point3D.create(led_x, led_y, 0), 0.2)
    p_led_base = pegar_anel(sk_led_base)
    if p_led_base:
        e_led_base = t_comp.features.extrudeFeatures.createInput(p_led_base, adsk.fusion.FeatureOperations.JoinFeatureOperation)
        e_led_base.startExtent = adsk.fusion.OffsetStartDefinition.create(adsk.core.ValueInput.createByReal(depth_top - wall - 0.2))
        e_led_base.setDistanceExtent(False, adsk.core.ValueInput.createByReal(0.2))
        t_comp.features.extrudeFeatures.add(e_led_base)
        
    sk_led = t_comp.sketches.add(t_comp.xYConstructionPlane)
    sk_led.sketchCurves.sketchCircles.addByCenterRadius(adsk.core.Point3D.create(led_x, led_y, 0), 0.15)
    e_led = t_comp.features.extrudeFeatures.createInput(sk_led.profiles.item(0), adsk.fusion.FeatureOperations.CutFeatureOperation)
    e_led.startExtent = adsk.fusion.OffsetStartDefinition.create(adsk.core.ValueInput.createByReal(depth_top - wall - 0.1))
    e_led.setDistanceExtent(False, adsk.core.ValueInput.createByReal(wall + 0.2))
    t_comp.features.extrudeFeatures.add(e_led)

# ============================================================
# INTERFACE DE USUÁRIO
# ============================================================
class ScannerCommandExecuteHandler(adsk.core.CommandEventHandler):
    def notify(self, args):
        try:
            inputs = args.firingEvent.sender.commandInputs
            gerar_scanner(
                inputs.itemById('length').value,
                inputs.itemById('width').value,
                inputs.itemById('depth_base').value,
                inputs.itemById('depth_top').value,
                inputs.itemById('wall').value,
                inputs.itemById('fillet_r').value
            )
        except:
            adsk.core.Application.get().userInterface.messageBox(traceback.format_exc())

class ScannerCommandDestroyHandler(adsk.core.CommandEventHandler):
    def notify(self, args): adsk.terminate()

class ScannerCommandCreatedHandler(adsk.core.CommandCreatedEventHandler):
    def notify(self, args):
        try:
            cmd = args.command
            onExecute = ScannerCommandExecuteHandler()
            cmd.execute.add(onExecute)
            handlers.append(onExecute)

            onDestroy = ScannerCommandDestroyHandler()
            cmd.destroy.add(onDestroy)
            handlers.append(onDestroy)

            inputs = cmd.commandInputs
            inputs.addValueInput('length', 'Comprimento', 'cm', adsk.core.ValueInput.createByReal(18.0))
            inputs.addValueInput('width', 'Largura', 'cm', adsk.core.ValueInput.createByReal(6.5))
            inputs.addValueInput('depth_base', 'Profundidade Base', 'cm', adsk.core.ValueInput.createByReal(1.6))
            inputs.addValueInput('depth_top', 'Profundidade Tampa', 'cm', adsk.core.ValueInput.createByReal(1.2))
            inputs.addValueInput('wall', 'Parede', 'cm', adsk.core.ValueInput.createByReal(0.25))
            inputs.addValueInput('fillet_r', 'Arredondamento', 'cm', adsk.core.ValueInput.createByReal(1.2))
        except:
            adsk.core.Application.get().userInterface.messageBox(traceback.format_exc())

def run(context):
    try:
        ui = adsk.core.Application.get().userInterface
        cmdDef = ui.commandDefinitions.itemById('TrakrScannerUI')
        if not cmdDef:
            cmdDef = ui.commandDefinitions.addButtonDefinition('TrakrScannerUI', 'Gerar Scanner Trakr', '')
            
        onCommandCreated = ScannerCommandCreatedHandler()
        cmdDef.commandCreated.add(onCommandCreated)
        handlers.append(onCommandCreated)
        cmdDef.execute()
        adsk.autoTerminate(False)
    except: pass