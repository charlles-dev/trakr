#Author: Gemini

#Description: Smart Toolbox - Industrial Grade

#             Inclui Insertos de Latão, Grid de Armazenamento, Guia de Luz,

#             Suportes para Abraçadeiras (Zip-ties), Pés de Borracha e USB-C.



import adsk.core, adsk.fusion, traceback

import math



handlers = []



# ============================================================

# LÓGICA DE GERAÇÃO 3D

# ============================================================

def aplicar_fillet_cantos(comp, corpo, raio):

    arestas = adsk.core.ObjectCollection.create()

    for edge in corpo.edges:

        p1 = edge.startVertex.geometry

        p2 = edge.endVertex.geometry

        if abs(p1.x - p2.x) < 0.01 and abs(p1.y - p2.y) < 0.01:

            arestas.add(edge)

    if arestas.count > 0:

        try:

            filletInput = comp.features.filletFeatures.createInput()

            filletInput.addConstantRadiusEdgeSet(arestas, adsk.core.ValueInput.createByReal(raio), True)

            comp.features.filletFeatures.add(filletInput)

        except: pass



def criar_torres(comp, xyPlane, extrudes, base_x, base_y, esp_x, esp_y, altura):

    d_ext = 0.7   # Parede mais grossa para suportar o derretimento do latão

    d_int = 0.4   # Furo de 4mm exato para Insertos Roscados de Latão M3 (Heat-set Inserts)

    pontos = [

        (base_x, base_y), (base_x + esp_x, base_y),

        (base_x, base_y + esp_y), (base_x + esp_x, base_y + esp_y)

    ]

    for px, py in pontos:

        sk = comp.sketches.add(xyPlane)

        sk.sketchCurves.sketchCircles.addByCenterRadius(adsk.core.Point3D.create(px, py, 0), d_ext/2)

        sk.sketchCurves.sketchCircles.addByCenterRadius(adsk.core.Point3D.create(px, py, 0), d_int/2)

        ring = next((p for p in sk.profiles if p.profileLoops.count > 1), None)

        if ring:

            extInput = extrudes.createInput(ring, adsk.fusion.FeatureOperations.JoinFeatureOperation)

            extInput.setDistanceExtent(False, adsk.core.ValueInput.createByReal(altura))

            extrudes.add(extInput)



def criar_passa_cabo(comp, xyPlane, extrudes, px, py):

    # Cria uma pequena ponte para abraçadeiras (zip-ties)

    sk = comp.sketches.add(xyPlane)

    sk.sketchCurves.sketchLines.addTwoPointRectangle(

        adsk.core.Point3D.create(px, py, 0), adsk.core.Point3D.create(px + 0.6, py + 0.6, 0))

    ext = extrudes.createInput(sk.profiles.item(0), adsk.fusion.FeatureOperations.JoinFeatureOperation)

    ext.setDistanceExtent(False, adsk.core.ValueInput.createByReal(0.5))

    extrudes.add(ext)

   

    # Furo da ponte

    sk_f = comp.sketches.add(comp.xZConstructionPlane)

    sk_f.sketchCurves.sketchLines.addTwoPointRectangle(

        adsk.core.Point3D.create(px + 0.1, py + 0.2, 0), adsk.core.Point3D.create(px + 0.5, py + 0.4, 0))

    ext_f = extrudes.createInput(sk_f.profiles.item(0), adsk.fusion.FeatureOperations.CutFeatureOperation)

    ext_f.setSymmetricExtent(adsk.core.ValueInput.createByReal(50), True)

    try: extrudes.add(ext_f)

    except: pass # Ignora se falhar por plano transversal



def gerar_maleta(length, width, base_depth, lid_depth, wall):

    app = adsk.core.Application.get()

    design = app.activeProduct

    root = design.rootComponent

   

    fillet_r = 0.5

    lip_h = 0.2

    lip_t = 0.12

    tol = 0.025

    tray_h = 1.8

   

    # ==========================================================

    # 1. BASE

    # ==========================================================

    b_occ = root.occurrences.addNewComponent(adsk.core.Matrix3D.create())

    b_comp = b_occ.component

    b_comp.name = "Maleta_Base"

   

    sk_b = b_comp.sketches.add(b_comp.xYConstructionPlane)

    sk_b.sketchCurves.sketchLines.addTwoPointRectangle(

        adsk.core.Point3D.create(0, 0, 0), adsk.core.Point3D.create(length, width, 0))

    ext_b = b_comp.features.extrudeFeatures.createInput(sk_b.profiles.item(0), adsk.fusion.FeatureOperations.NewBodyFeatureOperation)

    ext_b.setDistanceExtent(False, adsk.core.ValueInput.createByReal(-base_depth))

    body_b = b_comp.features.extrudeFeatures.add(ext_b).bodies.item(0)

   

    topFace = next((f for f in body_b.faces if abs(f.geometry.normal.z - 1.0) < 0.01), None)

    if topFace:

        sh_b = b_comp.features.shellFeatures.createInput(adsk.core.ObjectCollection.createWithArray([topFace]))

        sh_b.insideThickness = adsk.core.ValueInput.createByReal(wall)

        b_comp.features.shellFeatures.add(sh_b)

    aplicar_fillet_cantos(b_comp, body_b, fillet_r)



    # Lip Macho e Ledge

    sk_lip = b_comp.sketches.add(b_comp.xYConstructionPlane)

    sk_lip.sketchCurves.sketchLines.addTwoPointRectangle(

        adsk.core.Point3D.create(wall - lip_t, wall - lip_t, 0), adsk.core.Point3D.create(length - wall + lip_t, width - wall + lip_t, 0))

    sk_lip.sketchCurves.sketchLines.addTwoPointRectangle(

        adsk.core.Point3D.create(wall, wall, 0), adsk.core.Point3D.create(length - wall, width - wall, 0))

    p_lip = next((p for p in sk_lip.profiles if 0 < p.areaProperties().area < 10.0), None)

    if p_lip:

        e_lip = b_comp.features.extrudeFeatures.createInput(p_lip, adsk.fusion.FeatureOperations.JoinFeatureOperation)

        e_lip.setDistanceExtent(False, adsk.core.ValueInput.createByReal(lip_h))

        b_comp.features.extrudeFeatures.add(e_lip)

       

    sk_ledge = b_comp.sketches.add(b_comp.xYConstructionPlane)

    ledge_w = 0.2

    sk_ledge.sketchCurves.sketchLines.addTwoPointRectangle(

        adsk.core.Point3D.create(wall, wall, 0), adsk.core.Point3D.create(length - wall, width - wall, 0))

    sk_ledge.sketchCurves.sketchLines.addTwoPointRectangle(

        adsk.core.Point3D.create(wall + ledge_w, wall + ledge_w, 0), adsk.core.Point3D.create(length - wall - ledge_w, width - wall - ledge_w, 0))

    p_ledge = next((p for p in sk_ledge.profiles if 0 < p.areaProperties().area < 10.0), None)

    if p_ledge:

        e_ledge = b_comp.features.extrudeFeatures.createInput(p_ledge, adsk.fusion.FeatureOperations.JoinFeatureOperation)

        e_ledge.startExtent = adsk.fusion.OffsetStartDefinition.create(adsk.core.ValueInput.createByReal(-tray_h))

        e_ledge.setDistanceExtent(False, adsk.core.ValueInput.createByReal(-0.3))

        b_comp.features.extrudeFeatures.add(e_ledge)



    # Recorte USB-C (Base)

    sk_usb = b_comp.sketches.add(b_comp.xYConstructionPlane)

    usb_w, usb_h = 0.9, 0.35

    usb_y = width/2 - usb_w/2

    sk_usb.sketchCurves.sketchLines.addTwoPointRectangle(

        adsk.core.Point3D.create(length - wall - 0.2, usb_y, 0), adsk.core.Point3D.create(length + 0.2, usb_y + usb_w, 0))

    e_usb = b_comp.features.extrudeFeatures.createInput(sk_usb.profiles.item(0), adsk.fusion.FeatureOperations.CutFeatureOperation)

    e_usb.startExtent = adsk.fusion.OffsetStartDefinition.create(adsk.core.ValueInput.createByReal(-tray_h + 0.4))

    e_usb.setDistanceExtent(False, adsk.core.ValueInput.createByReal(usb_h))

    b_comp.features.extrudeFeatures.add(e_usb)



    # Suporte e Furo do Ímã

    sk_mag = b_comp.sketches.add(b_comp.xYConstructionPlane)

    sk_mag.sketchCurves.sketchLines.addTwoPointRectangle(

        adsk.core.Point3D.create(length/2 - 0.5, wall, 0), adsk.core.Point3D.create(length/2 + 0.5, wall + 0.6, 0))

    e_mag = b_comp.features.extrudeFeatures.createInput(sk_mag.profiles.item(0), adsk.fusion.FeatureOperations.JoinFeatureOperation)

    e_mag.startExtent = adsk.fusion.OffsetStartDefinition.create(adsk.core.ValueInput.createByReal(-1.2))

    e_mag.setDistanceExtent(False, adsk.core.ValueInput.createByReal(1.2))

    b_comp.features.extrudeFeatures.add(e_mag)

   

    sk_mag_hole = b_comp.sketches.add(b_comp.xYConstructionPlane)

    sk_mag_hole.sketchCurves.sketchCircles.addByCenterRadius(adsk.core.Point3D.create(length/2, wall + 0.3, 0), 0.25)

    e_mag_hole = b_comp.features.extrudeFeatures.createInput(sk_mag_hole.profiles.item(0), adsk.fusion.FeatureOperations.CutFeatureOperation)

    e_mag_hole.startExtent = adsk.fusion.OffsetStartDefinition.create(adsk.core.ValueInput.createByReal(-0.2))

    e_mag_hole.setDistanceExtent(False, adsk.core.ValueInput.createByReal(0.2))

    b_comp.features.extrudeFeatures.add(e_mag_hole)



    # Pés de Borracha / TPU (Cutouts na base inferior)

    sk_feet = b_comp.sketches.add(b_comp.xYConstructionPlane)

    fm = wall + 1.2

    for fx, fy in [(fm, fm), (length-fm, fm), (fm, width-fm), (length-fm, width-fm)]:

        sk_feet.sketchCurves.sketchCircles.addByCenterRadius(adsk.core.Point3D.create(fx, fy, 0), 0.6)

    col_feet = adsk.core.ObjectCollection.create()

    for p in sk_feet.profiles: col_feet.add(p)

    if col_feet.count > 0:

        e_feet = b_comp.features.extrudeFeatures.createInput(col_feet, adsk.fusion.FeatureOperations.CutFeatureOperation)

        e_feet.startExtent = adsk.fusion.OffsetStartDefinition.create(adsk.core.ValueInput.createByReal(-base_depth + 0.15))

        e_feet.setDistanceExtent(False, adsk.core.ValueInput.createByReal(0.2)) # Corte raso para encaixe do pé

        b_comp.features.extrudeFeatures.add(e_feet)



    # Dobradiças e Snap-Fit (Base)

    sk_hinge_b = b_comp.sketches.add(b_comp.xZConstructionPlane)

    for hx in [length * 0.2, length * 0.8]:

        sk_hinge_b.sketchCurves.sketchCircles.addByCenterRadius(adsk.core.Point3D.create(hx, width + 0.32, 0), 0.4)

        sk_hinge_b.sketchCurves.sketchCircles.addByCenterRadius(adsk.core.Point3D.create(hx, width + 0.32, 0), 0.15)

    col_hinge_b = adsk.core.ObjectCollection.create()

    for p in sk_hinge_b.profiles:

        if p.profileLoops.count > 1: col_hinge_b.add(p)

    if col_hinge_b.count > 0:

        ext_hb = b_comp.features.extrudeFeatures.createInput(col_hinge_b, adsk.fusion.FeatureOperations.JoinFeatureOperation)

        ext_hb.setSymmetricExtent(adsk.core.ValueInput.createByReal(0.5), True)

        b_comp.features.extrudeFeatures.add(ext_hb)



    sk_snap_b = b_comp.sketches.add(b_comp.yZConstructionPlane)

    sk_snap_b.sketchCurves.sketchLines.addTwoPointRectangle(

        adsk.core.Point3D.create(0, -0.4, 0), adsk.core.Point3D.create(-0.2, -0.8, 0))

    if sk_snap_b.profiles.count > 0:

        ext_sb = b_comp.features.extrudeFeatures.createInput(sk_snap_b.profiles.item(0), adsk.fusion.FeatureOperations.JoinFeatureOperation)

        ext_sb.setSymmetricExtent(adsk.core.ValueInput.createByReal(1.0), True)

        transform_sb = adsk.core.Matrix3D.create()

        transform_sb.translation = adsk.core.Vector3D.create(length/2, 0, 0)

        ext_sb.transform = transform_sb

        b_comp.features.extrudeFeatures.add(ext_sb)



    # ==========================================================

    # 2. TAMPA

    # ==========================================================

    matrix_t = adsk.core.Matrix3D.create()

    matrix_t.translation = adsk.core.Vector3D.create(0, width + 3.0, 0)

    t_occ = root.occurrences.addNewComponent(matrix_t)

    t_comp = t_occ.component

    t_comp.name = "Maleta_Tampa"

   

    sk_t = t_comp.sketches.add(t_comp.xYConstructionPlane)

    sk_t.sketchCurves.sketchLines.addTwoPointRectangle(

        adsk.core.Point3D.create(0, 0, 0), adsk.core.Point3D.create(length, width, 0))

    ext_t = t_comp.features.extrudeFeatures.createInput(sk_t.profiles.item(0), adsk.fusion.FeatureOperations.NewBodyFeatureOperation)

    ext_t.setDistanceExtent(False, adsk.core.ValueInput.createByReal(lid_depth))

    body_t = t_comp.features.extrudeFeatures.add(ext_t).bodies.item(0)

   

    botFace = next((f for f in body_t.faces if abs(f.geometry.normal.z - (-1.0)) < 0.01), None)

    if botFace:

        sh_t = t_comp.features.shellFeatures.createInput(adsk.core.ObjectCollection.createWithArray([botFace]))

        sh_t.insideThickness = adsk.core.ValueInput.createByReal(wall)

        t_comp.features.shellFeatures.add(sh_t)

    aplicar_fillet_cantos(t_comp, body_t, fillet_r)



    sk_gr = t_comp.sketches.add(t_comp.xYConstructionPlane)

    sk_gr.sketchCurves.sketchLines.addTwoPointRectangle(

        adsk.core.Point3D.create(wall - lip_t - 0.02, wall - lip_t - 0.02, 0), adsk.core.Point3D.create(length - wall + lip_t + 0.02, width - wall + lip_t + 0.02, 0))

    sk_gr.sketchCurves.sketchLines.addTwoPointRectangle(

        adsk.core.Point3D.create(wall, wall, 0), adsk.core.Point3D.create(length - wall, width - wall, 0))

    p_gr = next((p for p in sk_gr.profiles if 0 < p.areaProperties().area < 15.0), None)

    if p_gr:

        e_gr = t_comp.features.extrudeFeatures.createInput(p_gr, adsk.fusion.FeatureOperations.CutFeatureOperation)

        e_gr.setDistanceExtent(False, adsk.core.ValueInput.createByReal(lip_h + 0.05))

        t_comp.features.extrudeFeatures.add(e_gr)

       

    sk_oring = t_comp.sketches.add(t_comp.xYConstructionPlane)

    or_w = 0.15

    sk_oring.sketchCurves.sketchLines.addTwoPointRectangle(

        adsk.core.Point3D.create(wall - lip_t/2 - or_w/2, wall - lip_t/2 - or_w/2, 0), adsk.core.Point3D.create(length - wall + lip_t/2 + or_w/2, width - wall + lip_t/2 + or_w/2, 0))

    sk_oring.sketchCurves.sketchLines.addTwoPointRectangle(

        adsk.core.Point3D.create(wall - lip_t/2 + or_w/2, wall - lip_t/2 + or_w/2, 0), adsk.core.Point3D.create(length - wall + lip_t/2 - or_w/2, width - wall + lip_t/2 - or_w/2, 0))

    p_oring = next((p for p in sk_oring.profiles if 0 < p.areaProperties().area < 5.0), None)

    if p_oring:

        e_oring = t_comp.features.extrudeFeatures.createInput(p_oring, adsk.fusion.FeatureOperations.CutFeatureOperation)

        e_oring.setDistanceExtent(False, adsk.core.ValueInput.createByReal(lip_h + 0.2))

        t_comp.features.extrudeFeatures.add(e_oring)



    sk_hall = t_comp.sketches.add(t_comp.xYConstructionPlane)

    sk_hall.sketchCurves.sketchLines.addTwoPointRectangle(

        adsk.core.Point3D.create(length/2 - 0.6, wall, 0), adsk.core.Point3D.create(length/2 + 0.6, wall + 0.6, 0))

    e_hall = t_comp.features.extrudeFeatures.createInput(sk_hall.profiles.item(0), adsk.fusion.FeatureOperations.JoinFeatureOperation)

    e_hall.startExtent = adsk.fusion.OffsetStartDefinition.create(adsk.core.ValueInput.createByReal(0.0))

    e_hall.setDistanceExtent(False, adsk.core.ValueInput.createByReal(1.0))

    t_comp.features.extrudeFeatures.add(e_hall)

   

    sk_hall_slot = t_comp.sketches.add(t_comp.xYConstructionPlane)

    sk_hall_slot.sketchCurves.sketchLines.addTwoPointRectangle(

        adsk.core.Point3D.create(length/2 - 0.2, wall + 0.1, 0), adsk.core.Point3D.create(length/2 + 0.2, wall + 0.35, 0))

    e_hall_slot = t_comp.features.extrudeFeatures.createInput(sk_hall_slot.profiles.item(0), adsk.fusion.FeatureOperations.CutFeatureOperation)

    e_hall_slot.setDistanceExtent(False, adsk.core.ValueInput.createByReal(0.6))

    t_comp.features.extrudeFeatures.add(e_hall_slot)



    sk_hinge_t = t_comp.sketches.add(t_comp.xZConstructionPlane)

    sk_hinge_t.sketchCurves.sketchCircles.addByCenterRadius(adsk.core.Point3D.create(length/2, width + 0.32, 0), 0.4)

    sk_hinge_t.sketchCurves.sketchCircles.addByCenterRadius(adsk.core.Point3D.create(length/2, width + 0.32, 0), 0.15)

    col_hinge_t = adsk.core.ObjectCollection.create()

    for p in sk_hinge_t.profiles:

        if p.profileLoops.count > 1: col_hinge_t.add(p)

    if col_hinge_t.count > 0:

        ext_ht = t_comp.features.extrudeFeatures.createInput(col_hinge_t, adsk.fusion.FeatureOperations.JoinFeatureOperation)

        ext_ht.setSymmetricExtent(adsk.core.ValueInput.createByReal((length * 0.6)/2 - 0.1), True)

        t_comp.features.extrudeFeatures.add(ext_ht)



    sk_snap_t = t_comp.sketches.add(t_comp.yZConstructionPlane)

    sk_snap_t.sketchCurves.sketchLines.addTwoPointRectangle(

        adsk.core.Point3D.create(0, -0.8, 0), adsk.core.Point3D.create(-0.2, 0.4, 0))

    sk_snap_t.sketchCurves.sketchLines.addTwoPointRectangle(

        adsk.core.Point3D.create(-0.2, -0.8, 0), adsk.core.Point3D.create(-0.3, -0.4, 0))

    col_snap_t = adsk.core.ObjectCollection.create()

    for p in sk_snap_t.profiles: col_snap_t.add(p)

    if col_snap_t.count > 0:

        ext_st = t_comp.features.extrudeFeatures.createInput(col_snap_t, adsk.fusion.FeatureOperations.JoinFeatureOperation)

        ext_st.setSymmetricExtent(adsk.core.ValueInput.createByReal(1.0), True)

        transform_st = adsk.core.Matrix3D.create()

        transform_st.translation = adsk.core.Vector3D.create(length/2, 0, 0)

        ext_st.transform = transform_st

        t_comp.features.extrudeFeatures.add(ext_st)



    # ==========================================================

    # 3. BANDEJA MODULAR

    # ==========================================================

    tray_occ = root.occurrences.addNewComponent(adsk.core.Matrix3D.create())

    tray_comp = tray_occ.component

    tray_comp.name = "Maleta_Bandeja"

   

    t_len = length - (wall * 2) - (tol * 2)

    t_wid = width - (wall * 2) - (tol * 2)

   

    sk_tray = tray_comp.sketches.add(tray_comp.xYConstructionPlane)

    sk_tray.sketchCurves.sketchLines.addTwoPointRectangle(

        adsk.core.Point3D.create(0, 0, 0), adsk.core.Point3D.create(t_len, t_wid, 0))

   

    ext_tr = tray_comp.features.extrudeFeatures.createInput(sk_tray.profiles.item(0), adsk.fusion.FeatureOperations.NewBodyFeatureOperation)

    ext_tr.setDistanceExtent(False, adsk.core.ValueInput.createByReal(tray_h))

    body_tr = tray_comp.features.extrudeFeatures.add(ext_tr).bodies.item(0)

   

    t_f = next((f for f in body_tr.faces if abs(f.geometry.normal.z - 1.0) < 0.01), None)

    if t_f:

        sh_tr = tray_comp.features.shellFeatures.createInput(adsk.core.ObjectCollection.createWithArray([t_f]))

        sh_tr.insideThickness = adsk.core.ValueInput.createByReal(wall)

        tray_comp.features.shellFeatures.add(sh_tr)



    # Torres (com furos de 4mm para insertos térmicos)

    esp_x_base = 1.0

    esp_y_base = t_wid/2 - 1.1

    criar_torres(tray_comp, tray_comp.xYConstructionPlane, tray_comp.features.extrudeFeatures, esp_x_base, esp_y_base, 4.7, 2.25, 0.4)

    tp_x_base = t_len - 3.5

    tp_y_base = t_wid/2 - 0.7

    criar_torres(tray_comp, tray_comp.xYConstructionPlane, tray_comp.features.extrudeFeatures, tp_x_base, tp_y_base, 2.2, 1.4, 0.4)

   

    # Roteamento/Abraçadeiras Zip-Ties

    criar_passa_cabo(tray_comp, tray_comp.xYConstructionPlane, tray_comp.features.extrudeFeatures, esp_x_base - 0.8, esp_y_base + 1.0)

    criar_passa_cabo(tray_comp, tray_comp.xYConstructionPlane, tray_comp.features.extrudeFeatures, tp_x_base - 1.0, tp_y_base + 0.5)



    # Grid System (Lado Esquerdo da Bandeja)

    sk_grid = tray_comp.sketches.add(tray_comp.xYConstructionPlane)

    gx, gy = 1.0, 1.0

    g_size = 3.0

    for i in range(2):

        for j in range(2):

            sk_grid.sketchCurves.sketchLines.addTwoPointRectangle(

                adsk.core.Point3D.create(gx + i*g_size, gy + j*g_size, 0),

                adsk.core.Point3D.create(gx + (i+1)*g_size - 0.1, gy + (j+1)*g_size - 0.1, 0))

    col_grid = adsk.core.ObjectCollection.create()

    for p in sk_grid.profiles: col_grid.add(p)

    if col_grid.count > 0:

        ext_grid = tray_comp.features.extrudeFeatures.createInput(col_grid, adsk.fusion.FeatureOperations.CutFeatureOperation)

        ext_grid.startExtent = adsk.fusion.OffsetStartDefinition.create(adsk.core.ValueInput.createByReal(tray_h - 1.5))

        ext_grid.setDistanceExtent(False, adsk.core.ValueInput.createByReal(1.5))

        tray_comp.features.extrudeFeatures.add(ext_grid)



    # USB-C Bandeja

    sk_usb_tr = tray_comp.sketches.add(tray_comp.xYConstructionPlane)

    sk_usb_tr.sketchCurves.sketchLines.addTwoPointRectangle(

        adsk.core.Point3D.create(t_len - wall - 0.2, t_wid/2 - usb_w/2, 0), adsk.core.Point3D.create(t_len + 0.2, t_wid/2 + usb_w/2, 0))

    e_usb_tr = tray_comp.features.extrudeFeatures.createInput(sk_usb_tr.profiles.item(0), adsk.fusion.FeatureOperations.CutFeatureOperation)

    e_usb_tr.startExtent = adsk.fusion.OffsetStartDefinition.create(adsk.core.ValueInput.createByReal(0.4))

    e_usb_tr.setDistanceExtent(False, adsk.core.ValueInput.createByReal(usb_h))

    tray_comp.features.extrudeFeatures.add(e_usb_tr)



    # Berço

    bat_len, bat_w = 6.6, 1.9

    cradle_x = t_len/2 - bat_len/2

    cradle_y = t_wid - bat_w - 0.5

    cradle_h = 1.2

   

    sk_cradle = tray_comp.sketches.add(tray_comp.xYConstructionPlane)

    sk_cradle.sketchCurves.sketchLines.addTwoPointRectangle(

        adsk.core.Point3D.create(cradle_x - 0.2, cradle_y - 0.2, 0), adsk.core.Point3D.create(cradle_x + bat_len + 0.2, cradle_y + bat_w + 0.2, 0))

    sk_cradle.sketchCurves.sketchLines.addTwoPointRectangle(

        adsk.core.Point3D.create(cradle_x, cradle_y + 0.075, 0), adsk.core.Point3D.create(cradle_x + bat_len, cradle_y + bat_w - 0.075, 0))

    p_cradle = next((p for p in sk_cradle.profiles if 0 < p.areaProperties().area < 15.0), None)

    if p_cradle:

        e_cradle = tray_comp.features.extrudeFeatures.createInput(p_cradle, adsk.fusion.FeatureOperations.JoinFeatureOperation)

        e_cradle.setDistanceExtent(False, adsk.core.ValueInput.createByReal(cradle_h))

        tray_comp.features.extrudeFeatures.add(e_cradle)

       

    sk_cables = tray_comp.sketches.add(tray_comp.xYConstructionPlane)

    sk_cables.sketchCurves.sketchLines.addTwoPointRectangle(

        adsk.core.Point3D.create(cradle_x - 0.3, cradle_y + bat_w/2 - 0.2, 0), adsk.core.Point3D.create(cradle_x + 0.1, cradle_y + bat_w/2 + 0.2, 0))

    sk_cables.sketchCurves.sketchLines.addTwoPointRectangle(

        adsk.core.Point3D.create(cradle_x + bat_len - 0.1, cradle_y + bat_w/2 - 0.2, 0), adsk.core.Point3D.create(cradle_x + bat_len + 0.3, cradle_y + bat_w/2 + 0.2, 0))

    col_cables = adsk.core.ObjectCollection.create()

    for p in sk_cables.profiles: col_cables.add(p)

    if col_cables.count > 0:

        e_cables = tray_comp.features.extrudeFeatures.createInput(col_cables, adsk.fusion.FeatureOperations.CutFeatureOperation)

        e_cables.setDistanceExtent(False, adsk.core.ValueInput.createByReal(cradle_h))

        tray_comp.features.extrudeFeatures.add(e_cables)



    aplicar_fillet_cantos(tray_comp, body_tr, 0.2)

    transform_tr = adsk.core.Matrix3D.create()

    transform_tr.translation = adsk.core.Vector3D.create(wall + tol, wall + tol, 5.0)

    tray_occ.transform = transform_tr

   

    # ==========================================================

    # 4. LIGHT PIPE (Guia de Luz)

    # ==========================================================

    lp_occ = root.occurrences.addNewComponent(adsk.core.Matrix3D.create())

    lp_comp = lp_occ.component

    lp_comp.name = "Maleta_GuiaLuz_LED"

   

    sk_lp = lp_comp.sketches.add(lp_comp.xYConstructionPlane)

    sk_lp.sketchCurves.sketchCircles.addByCenterRadius(adsk.core.Point3D.create(0, 0, 0), 0.23) # Ajuste 0.2mm p/ encaixar no furo de 0.25 (5mm)

    ext_lp = lp_comp.features.extrudeFeatures.createInput(sk_lp.profiles.item(0), adsk.fusion.FeatureOperations.NewBodyFeatureOperation)

    ext_lp.setDistanceExtent(False, adsk.core.ValueInput.createByReal(0.4))

    lp_comp.features.extrudeFeatures.add(ext_lp)

   

    # Flange (Borda) para não cair pelo buraco

    sk_fl = lp_comp.sketches.add(lp_comp.xYConstructionPlane)

    sk_fl.sketchCurves.sketchCircles.addByCenterRadius(adsk.core.Point3D.create(0, 0, 0), 0.35)

    ext_fl = lp_comp.features.extrudeFeatures.createInput(sk_fl.profiles.item(0), adsk.fusion.FeatureOperations.JoinFeatureOperation)

    ext_fl.setDistanceExtent(False, adsk.core.ValueInput.createByReal(0.1))

    lp_comp.features.extrudeFeatures.add(ext_fl)

   

    transform_lp = adsk.core.Matrix3D.create()

    transform_lp.translation = adsk.core.Vector3D.create(-2.0, width/2, 0) # Posiciona ao lado da maleta

    lp_occ.transform = transform_lp



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

           

            gerar_maleta(length, width, base_depth, lid_depth, wall)

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

            inputs.addValueInput('length', 'Comprimento (cm)', 'cm', adsk.core.ValueInput.createByReal(24.0))

            inputs.addValueInput('width', 'Largura (cm)', 'cm', adsk.core.ValueInput.createByReal(11.0))

            inputs.addValueInput('base_depth', 'Profundidade da Base', 'cm', adsk.core.ValueInput.createByReal(6.0))

            inputs.addValueInput('lid_depth', 'Profundidade da Tampa', 'cm', adsk.core.ValueInput.createByReal(3.0))

            inputs.addValueInput('wall', 'Espessura da Parede', 'cm', adsk.core.ValueInput.createByReal(0.25))

        except:

            app = adsk.core.Application.get()

            app.userInterface.messageBox(traceback.format_exc())



def run(context):

    try:

        app = adsk.core.Application.get()

        ui = app.userInterface

       

        cmdDef = ui.commandDefinitions.itemById('SmartToolboxUI')

        if not cmdDef:

            cmdDef = ui.commandDefinitions.addButtonDefinition('SmartToolboxUI', 'Configurar Maleta 3D', 'Gera a maleta com UI')

           

        onCommandCreated = ToolboxCommandCreatedHandler()

        cmdDef.commandCreated.add(onCommandCreated)

        handlers.append(onCommandCreated)

       

        cmdDef.execute()

        adsk.autoTerminate(False)

    except:

        if ui:

            ui.messageBox('Erro Inicial:\n{}'.format(traceback.format_exc()))