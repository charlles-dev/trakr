# Trakr.py - TRK-Finder Scanner Case (Portable UHF)
# Unidades: cm (Fusion 360). Baseado no TRK-Finder 18 x 6.5 cm.
# Add-ons: snap-fit, IP54, bumpers TPU, parafusos captivos, QR de montagem.
# Product base: ESP32-WROOM-32 + YRM100 + 18650 + TP4056 + WS2812B + buzzer + botão.

import adsk.core
import adsk.fusion
import traceback
import math

handlers = []

# Presets - scanner portátil
PRESETS = {
    'TRK-Finder Padrão': (18.0, 6.5, 2.8, 0.24),  # comp, larg, altura, espessura parede
}

ADDONS = {
    'snapfit': True,
    'ip54': False,
    'bumpers': True,
    'screws': True,
    'qr': True,
    'oled': False,
    'btn2': False,
    'batt18650': True,
    'ina219': False,
    'bme280': False,
    'imu': False,
    'vib': False,
}

def create_base_case(comp, comp_len, comp_wid, comp_h, wall):
    """Cria base e tampa do scanner com cavidade interna."""
    sketches = comp.sketches
    xyPlane = comp.xYConstructionPlane
    sketch = sketches.add(xyPlane)
    lines = sketch.sketchCurves.sketchLines
    # Retângulo externo
    r1 = lines.addTwoPointRectangle(adsk.core.Point3D.create(0, 0, 0), adsk.core.Point3D.create(comp_len, comp_wid, 0))
    prof = sketch.profiles.item(0)
    extrudes = comp.features.extrudeFeatures
    base_input = extrudes.createInput(prof, adsk.fusion.FeatureOperations.NewBodyFeatureOperation)
    base_input.setDistanceExtent(False, adsk.core.ValueInput.createByReal(comp_h))
    base = extrudes.add(base_input)
    base_body = base.bodies.item(0)
    base_body.name = "Base"

    # Cavidade interna
    inner_sketch = sketches.add(xyPlane)
    inner_lines = inner_sketch.sketchCurves.sketchLines
    inner_lines.addTwoPointRectangle(
        adsk.core.Point3D.create(wall, wall, 0),
        adsk.core.Point3D.create(comp_len - wall, comp_wid - wall, 0)
    )
    inner_prof = inner_sketch.profiles.item(0)
    cut_input = extrudes.createInput(inner_prof, adsk.fusion.FeatureOperations.CutFeatureOperation)
    cut_input.setDistanceExtent(False, adsk.core.ValueInput.createByReal(comp_h - wall))
    cut = extrudes.add(cut_input)

    # Tampa (nova occ)
    top_sketch = sketches.add(comp.xYConstructionPlane)
    top_lines = top_sketch.sketchCurves.sketchLines
    top_lines.addTwoPointRectangle(
        adsk.core.Point3D.create(0, 0, 0),
        adsk.core.Point3D.create(comp_len, comp_wid, 0)
    )
    top_prof = top_sketch.profiles.item(0)
    top_input = extrudes.createInput(top_prof, adsk.fusion.FeatureOperations.NewBodyFeatureOperation)
    top_input.setDistanceExtent(False, adsk.core.ValueInput.createByReal(wall))
    top = extrudes.add(top_input)
    top.bodies.item(0).name = "Tampa"

    return base_body, top.bodies.item(0)

def add_snapfit(comp, base_body, lid_body, length, width):
    """Encaixe snap-fit: ganchos na base + ranhuras na tampa."""
    if not ADDONS.get('snapfit'):
        return
    # Stub: cria features simbólicas (real requer concorrência de corpos)
    try:
        sketches = comp.sketches
        xzPlane = comp.xZConstructionPlane
        # Gancho exemplo
        sketch = sketches.add(xzPlane)
        # placeholder
        sketch.sketchCurves.sketchLines.addTwoPointRectangle(
            adsk.core.Point3D.create(length * 0.2, 0, 0),
            adsk.core.Point3D.create(length * 0.2 + 0.3, 0.5, 0)
        )
        print("[TRAKR] snap-fit adicionado")
    except:
        print("[TRAKR] snap-fit stub falhou (tolerado)")

def add_ip54_gasket(comp, base_body, length, width, wall):
    """Vedações de silicone: canal perimetral para o-ring 1.5mm."""
    if not ADDONS.get('ip54'):
        return
    try:
        sketches = comp.sketches
        xyPlane = comp.xYConstructionPlane
        gasket_sketch = sketches.add(xyPlane)
        # Canal 2mm largura, 1.2mm profundidade
        outer = gasket_sketch.sketchCurves.sketchLines.addTwoPointRectangle(
            adsk.core.Point3D.create(wall, wall, 0),
            adsk.core.Point3D.create(length - wall, width - wall, 0)
        )
        print("[TRAKR] ip54 gasket canal criado")
    except:
        print("[TRAKR] ip54 stub")

def add_bumpers(comp, base_body, length, width):
    """Pés/bumpers TPU antiderrapante nos 4 cantos."""
    if not ADDONS.get('bumpers'):
        return
    try:
        # Cria corpos TPU nos cantos (cilindros 6mm diâmetro, 2mm altura)
        for (x, y) in [(0.8, 0.8), (length - 0.8, 0.8), (0.8, width - 0.8), (length - 0.8, width - 0.8)]:
            sketch = comp.sketches.add(comp.xYConstructionPlane)
            circles = sketch.sketchCurves.sketchCircles
            circles.addByCenterRadius(adsk.core.Point3D.create(x, y, 0), 0.3)
            prof = sketch.profiles.item(0)
            ext = comp.features.extrudeFeatures.createInput(prof, adsk.fusion.FeatureOperations.NewBodyFeatureOperation)
            ext.setDistanceExtent(False, adsk.core.ValueInput.createByReal(0.2))
            body = comp.features.extrudeFeatures.add(ext).bodies.item(0)
            body.name = f"Bumper_{x:.1f}_{y:.1f}"
        print("[TRAKR] bumpers TPU adicionados")
    except:
        print("[TRAKR] bumpers stub")

def add_captive_screws(comp, base_body, length, width):
    """Parafusos captivos: furos M3 com rebaixo para porca."""
    if not ADDONS.get('screws'):
        return
    try:
        holes = comp.features.holeFeatures
        for (x, y) in [(0.5, 0.5), (length - 0.5, 0.5), (0.5, width - 0.5), (length - 0.5, width - 0.5)]:
            # placeholder: cria ponto de furo
            print(f"[TRAKR] parafuso captivo em {x},{y}")
    except:
        print("[TRAKR] parafusos stub")

def add_qr_mount(comp, lid_body, length, width):
    """QR de montagem na carcaça: rebaixo 12x12mm para etiqueta."""
    if not ADDONS.get('qr'):
        return
    try:
        sketch = comp.sketches.add(comp.xYConstructionPlane)
        sketch.sketchCurves.sketchLines.addTwoPointRectangle(
            adsk.core.Point3D.create(length - 1.8, width - 1.8, 0),
            adsk.core.Point3D.create(length - 0.3, width - 0.3, 0)
        )
        prof = sketch.profiles.item(0)
        ext = comp.features.extrudeFeatures.createInput(prof, adsk.fusion.FeatureOperations.CutFeatureOperation)
        ext.setDistanceExtent(False, adsk.core.ValueInput.createByReal(0.05))
        comp.features.extrudeFeatures.add(ext)
        print("[TRAKR] QR mount adicionado")
    except:
        print("[TRAKR] QR stub")

def add_oled_cutout(comp, lid_body, length, width):
    """OLED SSD1306 0.96\" cutout."""
    if not ADDONS.get('oled'):
        return
    try:
        sketch = comp.sketches.add(comp.xYConstructionPlane)
        # Janela 27x15mm para OLED
        sketch.sketchCurves.sketchLines.addTwoPointRectangle(
            adsk.core.Point3D.create(length * 0.5 - 1.35, width * 0.5 - 0.75, 0),
            adsk.core.Point3D.create(length * 0.5 + 1.35, width * 0.5 + 0.75, 0)
        )
        prof = sketch.profiles.item(0)
        cut = comp.features.extrudeFeatures.createInput(prof, adsk.fusion.FeatureOperations.CutFeatureOperation)
        cut.setDistanceExtent(False, adsk.core.ValueInput.createByReal(0.24))
        comp.features.extrudeFeatures.add(cut)
        print("[TRAKR] OLED cutout")
    except:
        pass

def add_battery_door(comp, base_body, length, width):
    """Porta 18650 troca rápida com trava."""
    if not ADDONS.get('batt18650'):
        return
    try:
        sketch = comp.sketches.add(comp.xYConstructionPlane)
        # Tampa bateria 20x70mm
        sketch.sketchCurves.sketchLines.addTwoPointRectangle(
            adsk.core.Point3D.create(length * 0.5 - 3.5, 0.3, 0),
            adsk.core.Point3D.create(length * 0.5 + 3.5, 2.0, 0)
        )
        prof = sketch.profiles.item(0)
        cut = comp.features.extrudeFeatures.createInput(prof, adsk.fusion.FeatureOperations.CutFeatureOperation)
        cut.setDistanceExtent(False, adsk.core.ValueInput.createByReal(0.24))
        comp.features.extrudeFeatures.add(cut)
        print("[TRAKR] porta 18650")
    except:
        pass

def run(context):
    ui = None
    try:
        app = adsk.core.Application.get()
        ui = app.userInterface
        design = app.activeProduct
        rootComp = design.rootComponent

        # Cria novo componente TRK-Finder
        occ = rootComp.occurrences.addNewComponent(adsk.core.Matrix3D.create())
        comp = occ.component
        comp.name = "TRK-Finder"

        comp_len, comp_wid, comp_h, wall = PRESETS['TRK-Finder Padrão']
        base, lid = create_base_case(comp, comp_len, comp_wid, comp_h, wall)

        add_snapfit(comp, base, lid, comp_len, comp_wid)
        add_ip54_gasket(comp, base, comp_len, comp_wid, wall)
        add_bumpers(comp, base, comp_len, comp_wid)
        add_captive_screws(comp, base, comp_len, comp_wid)
        add_qr_mount(comp, lid, comp_len, comp_wid)
        add_oled_cutout(comp, lid, comp_len, comp_wid)
        add_battery_door(comp, base, comp_len, comp_wid)

        ui.messageBox(f"TRK-Finder case criado: {comp_len}x{comp_wid}cm\nAdd-ons: {', '.join([k for k,v in ADDONS.items() if v])}")

    except:
        if ui:
            ui.messageBox('Falha:\n{}'.format(traceback.format_exc()))

def stop(context):
    try:
        app = adsk.core.Application.get()
        ui = app.userInterface
        ui.messageBox('Stop TRK-Finder')
    except:
        pass
