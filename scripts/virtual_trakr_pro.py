import asyncio
import threading
import json
import logging
import uuid
import sys
import datetime
import os
import time

import customtkinter as ctk
import winrt.windows.devices.bluetooth.genericattributeprofile as gatt
import winrt.windows.storage.streams as streams

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("TrakrPro")

SERVICE_UUID = uuid.UUID("60c1f000-1b2e-4d0f-9aeb-0fbe3c2a4b71")
INVENTORY_UUID = uuid.UUID("60c1f001-1b2e-4d0f-9aeb-0fbe3c2a4b71")
EVENT_UUID = uuid.UUID("60c1f002-1b2e-4d0f-9aeb-0fbe3c2a4b71")
CONTROL_UUID = uuid.UUID("60c1f003-1b2e-4d0f-9aeb-0fbe3c2a4b71")

ctk.set_appearance_mode("Dark")
ctk.set_default_color_theme("blue")

class SimFS:
    def __init__(self, base_dir="./sim_fs"):
        self.base_dir = base_dir
        os.makedirs(self.base_dir, exist_ok=True)
        self.config_path = os.path.join(self.base_dir, "config.json")
        self.inventory_path = os.path.join(self.base_dir, "inventory.json")
        self.events_path = os.path.join(self.base_dir, "events.json")

    def read_json(self, path, default=None):
        if not os.path.exists(path):
            return default if default is not None else {}
        try:
            with open(path, "r", encoding="utf-8") as f:
                return json.load(f)
        except:
            return default if default is not None else {}

    def write_json(self, path, data):
        with open(path, "w", encoding="utf-8") as f:
            json.dump(data, f, indent=2, ensure_ascii=False)

    def push_event(self, event_type, epc, name):
        events = self.read_json(self.events_path, [])
        events.append({
            "ts": int(time.time() * 1000),
            "type": event_type,
            "id": epc,
            "name": name
        })
        self.write_json(self.events_path, events)
        return events[-1]


class VirtualTrakrPro(ctk.CTk):
    def __init__(self):
        super().__init__()
        
        self.title("TRK-Finder | Emulador Profissional v2.0")
        self.geometry("950x700")
        
        self.fs = SimFS()
        self.firmware_state = "ESCUTA"
        self.radar_target_epc = ""
        self.battery = 95
        self.findme_end_at = 0

        # Sync from FS
        self.config = self.fs.read_json(self.fs.config_path, {
            "listen_ms": 30000, "radar_ms": 120000, "beep": True,
            "tx_power_dbm": 26, "rssi_offset": 0, "rssi_threshold": -70,
            "env_profile": "default", "pin_hash": ""
        })
        
        # Tools in the environment
        inv_data = self.fs.read_json(self.fs.inventory_path, {"tools": []})
        self.env_tools = []
        for t in inv_data.get("tools", []):
            self.env_tools.append({"id": t.get("id"), "name": t.get("name"), "epc": t.get("epc"), "rssi": -80})
            
        if not self.env_tools:
            self.env_tools = [
                {"id": "t1", "name": "Furadeira Bosch", "epc": "E200000000000001", "rssi": -80},
                {"id": "t2", "name": "Parafusadeira DeWalt", "epc": "E200000000000002", "rssi": -60}
            ]

        self.sliders = {}
        
        self.build_ui()
        
        self.loop = asyncio.new_event_loop()
        self.ble_thread = threading.Thread(target=self.run_ble_loop, daemon=True)
        self.ble_thread.start()
        
        self.firmware_state_machine_loop()

    def build_ui(self):
        self.grid_columnconfigure(0, weight=1)
        self.grid_columnconfigure(1, weight=1)
        
        # --- LEFT PANEL (Hardware) ---
        self.hw_frame = ctk.CTkFrame(self, corner_radius=15, fg_color="#1e1e24")
        self.hw_frame.grid(row=0, column=0, padx=10, pady=10, sticky="nsew")
        
        ctk.CTkLabel(self.hw_frame, text="HARDWARE (ESP32-S3)", font=ctk.CTkFont(size=16, weight="bold"), text_color="#aaaaaa").pack(pady=(15, 5))
        
        # OLED
        self.oled_border = ctk.CTkFrame(self.hw_frame, corner_radius=10, fg_color="#333333", border_width=2, border_color="#555555")
        self.oled_border.pack(pady=10, padx=20, fill="x")
        self.oled_canvas = ctk.CTkCanvas(self.oled_border, width=280, height=140, bg="#000000", highlightthickness=0)
        self.oled_canvas.pack(pady=10, padx=10)
        self.oled_text = self.oled_canvas.create_text(
            140, 70, text="INICIANDO...\nTRK-FINDER OS", fill="#00FFFF", font=("Consolas", 16, "bold"), justify="center"
        )
        
        # Physical Buttons
        btn_frame = ctk.CTkFrame(self.hw_frame, fg_color="transparent")
        btn_frame.pack(pady=10)
        ctk.CTkButton(btn_frame, text="PAIR / RESET", width=120, fg_color="#c42b2b", hover_color="#9e1f1f", command=self.simular_reset).pack(side="left", padx=5)
        ctk.CTkButton(btn_frame, text="ACTION", width=120, fg_color="#444444", hover_color="#666666", command=self.simular_action).pack(side="left", padx=5)
        
        # Battery Simulator
        batt_frame = ctk.CTkFrame(self.hw_frame, fg_color="transparent")
        batt_frame.pack(pady=5, fill="x", padx=20)
        ctk.CTkLabel(batt_frame, text="Bateria %:", font=("Arial", 12)).pack(side="left")
        self.batt_slider = ctk.CTkSlider(batt_frame, from_=1, to=100, command=self.update_battery)
        self.batt_slider.set(self.battery)
        self.batt_slider.pack(side="left", fill="x", expand=True, padx=10)
        self.batt_label = ctk.CTkLabel(batt_frame, text=f"{self.battery}%", font=("Consolas", 12))
        self.batt_label.pack(side="right")
        
        # Terminal Log
        ctk.CTkLabel(self.hw_frame, text="TERMINAL SERIAL (USB)", font=ctk.CTkFont(size=12, weight="bold")).pack(pady=(10, 0), anchor="w", padx=20)
        self.log_box = ctk.CTkTextbox(self.hw_frame, height=200, font=("Consolas", 11), fg_color="#0d0d0d", text_color="#00ff00")
        self.log_box.pack(pady=5, padx=20, fill="both", expand=True)
        self.log_box.configure(state="disabled")

        # --- RIGHT PANEL (Environment) ---
        self.env_frame = ctk.CTkFrame(self, corner_radius=15)
        self.env_frame.grid(row=0, column=1, padx=10, pady=10, sticky="nsew")
        
        ctk.CTkLabel(self.env_frame, text="AMBIENTE UHF & TAGS", font=ctk.CTkFont(size=16, weight="bold")).pack(pady=(15, 5))
        
        # Add Tool Form
        add_frame = ctk.CTkFrame(self.env_frame, fg_color="#2b2b36", corner_radius=8)
        add_frame.pack(fill="x", padx=20, pady=10)
        self.new_name = ctk.CTkEntry(add_frame, placeholder_text="Nome da Ferramenta")
        self.new_name.grid(row=0, column=0, padx=10, pady=10, sticky="we")
        self.new_epc = ctk.CTkEntry(add_frame, placeholder_text="EPC Hexadecimal")
        self.new_epc.grid(row=0, column=1, padx=10, pady=10, sticky="we")
        ctk.CTkButton(add_frame, text="Injetar Tool", width=80, command=self.inject_tool).grid(row=0, column=2, padx=10, pady=10)
        add_frame.grid_columnconfigure(0, weight=1)
        add_frame.grid_columnconfigure(1, weight=1)
        
        # Tool Sliders (Scrollable)
        self.tools_scroll = ctk.CTkScrollableFrame(self.env_frame, fg_color="transparent")
        self.tools_scroll.pack(fill="both", expand=True, padx=10, pady=5)
        
        self.render_tool_sliders()

    def update_battery(self, val):
        self.battery = int(val)
        self.batt_label.configure(text=f"{self.battery}%")

    def render_tool_sliders(self):
        for widget in self.tools_scroll.winfo_children():
            widget.destroy()
        self.sliders.clear()
        
        for t in self.env_tools:
            t_frame = ctk.CTkFrame(self.tools_scroll, fg_color="#2b2b36", corner_radius=8)
            t_frame.pack(fill="x", padx=10, pady=5)
            
            header = ctk.CTkFrame(t_frame, fg_color="transparent")
            header.pack(fill="x", padx=10, pady=(5, 0))
            
            ctk.CTkLabel(header, text=t["name"], font=ctk.CTkFont(weight="bold", size=13)).pack(side="left")
            val_label = ctk.CTkLabel(header, text=f"{t['rssi']} dBm", text_color="#00ffff", font=("Consolas", 12))
            val_label.pack(side="right")
            
            def make_cmd(epc, lbl):
                def cmd(val):
                    lbl.configure(text=f"{int(val)} dBm")
                    # Update internal rssi array
                    for tool in self.env_tools:
                        if tool["epc"] == epc:
                            tool["rssi"] = int(val)
                return cmd
                
            slider = ctk.CTkSlider(t_frame, from_=-100, to=-30, number_of_steps=70)
            slider.set(t["rssi"])
            slider.configure(command=make_cmd(t["epc"], val_label))
            slider.pack(fill="x", padx=15, pady=(5, 10))
            self.sliders[t["epc"]] = slider

    def inject_tool(self):
        name = self.new_name.get() or "Ferramenta Injetada"
        epc = self.new_epc.get() or f"E28011606000ABCD{int(time.time())}"
        self.env_tools.append({"id": epc, "name": name, "epc": epc, "rssi": -80})
        self.write_log(f"AMBIENTE: Ferramenta {name} ({epc}) injetada no rádio.")
        self.render_tool_sliders()

    def write_log(self, msg):
        self.log_box.configure(state="normal")
        ts = datetime.datetime.now().strftime("%H:%M:%S")
        self.log_box.insert("end", f"[{ts}] {msg}\n")
        self.log_box.see("end")
        self.log_box.configure(state="disabled")

    def update_oled(self, text):
        self.oled_canvas.itemconfig(self.oled_text, text=text)

    def simular_reset(self):
        self.write_log("SYS: Hard Reset / Reboot")
        self.firmware_state = "ESCUTA"
        self.radar_target_epc = ""
        self.update_oled("SISTEMA PRONTO\nBLE LIGADO")

    def simular_action(self):
        self.write_log("SYS: Botão ACTION (Leitura manual)")
        self.firmware_state = "LEITURA"

    def firmware_state_machine_loop(self):
        # Firmware Main Loop
        if self.firmware_state == "ESCUTA":
            self.update_oled("CONECTADO\nOcioso")
        
        elif self.firmware_state == "FINDME":
            rem = int((self.findme_end_at - time.time()))
            if rem <= 0:
                self.firmware_state = "ESCUTA"
                self.write_log("FIND ME: Concluído.")
            else:
                self.update_oled(f"ALARME BEACON!!\n{rem}s restantes\nBIP BIP BIP")
                
        elif self.firmware_state == "RASTREIA":
            epc = self.radar_target_epc
            rssi = -100
            name = "TAG"
            for t in self.env_tools:
                if t["epc"] == epc:
                    rssi = t["rssi"]
                    name = t["name"]
            
            p = max(0, min(10, int((rssi + 100) / 70 * 10)))
            bar = "█" * p + "_" * (10 - p)
            self.update_oled(f"🔎 BUSCANDO...\n{name[:14]}\n[{bar}]\n{rssi} dBm")
            
            report = {"type": "radar_report", "tag": epc, "rssi": rssi, "present": rssi > -95}
            asyncio.run_coroutine_threadsafe(self.notify_event(json.dumps(report)), self.loop)
            
        elif self.firmware_state == "LEITURA":
            self.update_oled("⟳ VARRENDO...\nAguarde")
            inv = {"tools": []}
            for t in self.env_tools:
                if t["rssi"] > -95:
                    inv["tools"].append({"id": t["id"], "name": t["name"], "epc": t["epc"], "present": True})
            self.fs.write_json(self.fs.inventory_path, inv)
            
            asyncio.run_coroutine_threadsafe(self.notify_inventory(json.dumps(inv)), self.loop)
            self.write_log(f"LEITURA: {len(inv['tools'])} tags lidas.")
            self.firmware_state = "ESCUTA"
            
        self.after(500, self.firmware_state_machine_loop)

    # ================= BLE WINRT =================
    def run_ble_loop(self):
        asyncio.set_event_loop(self.loop)
        self.loop.run_until_complete(self.ble_server_task())
        
    async def ble_server_task(self):
        try:
            res = await gatt.GattServiceProvider.create_async(SERVICE_UUID)
            self.provider = res.service_provider
            
            inv_params = gatt.GattLocalCharacteristicParameters()
            inv_params.characteristic_properties = gatt.GattCharacteristicProperties.NOTIFY | gatt.GattCharacteristicProperties.READ
            cres = await self.provider.service.create_characteristic_async(INVENTORY_UUID, inv_params)
            self.char_inventory = cres.characteristic

            evt_params = gatt.GattLocalCharacteristicParameters()
            evt_params.characteristic_properties = gatt.GattCharacteristicProperties.NOTIFY | gatt.GattCharacteristicProperties.READ
            cres = await self.provider.service.create_characteristic_async(EVENT_UUID, evt_params)
            self.char_event = cres.characteristic

            ctrl_params = gatt.GattLocalCharacteristicParameters()
            ctrl_params.characteristic_properties = gatt.GattCharacteristicProperties.WRITE | gatt.GattCharacteristicProperties.WRITE_WITHOUT_RESPONSE
            cres = await self.provider.service.create_characteristic_async(CONTROL_UUID, ctrl_params)
            self.char_ctrl = cres.characteristic
            self.char_ctrl.add_write_requested(self.on_ble_write)

            adv_params = gatt.GattServiceProviderAdvertisingParameters()
            adv_params.is_discoverable = True
            adv_params.is_connectable = True
            self.provider.start_advertising_with_parameters(adv_params)
            
            self.root_after(0, self.write_log, "GATT Server iniciado com sucesso!")
            
            while True:
                await asyncio.sleep(1) # Keep thread alive
                
        except Exception as e:
            self.root_after(0, self.write_log, f"Exceção BLE: {str(e)}")

    def root_after(self, ms, func, *args):
        self.after(ms, lambda: func(*args))

    def on_ble_write(self, sender, args):
        deferral = args.get_deferral()
        async def process():
            try:
                req = await args.get_request_async()
                if req.value and req.value.length > 0:
                    reader = streams.DataReader.from_buffer(req.value)
                    reader.unicode_encoding = streams.UnicodeEncoding.UTF8
                    doc = json.loads(reader.read_string(req.value.length))
                    self.root_after(0, self.process_command, doc)
                if req.option == gatt.GattWriteOption.WRITE_WITH_RESPONSE:
                    req.respond()
            except Exception as e:
                self.root_after(0, self.write_log, f"GATT Write Error: {e}")
            finally:
                deferral.complete()
        asyncio.run_coroutine_threadsafe(process(), self.loop)

    def process_command(self, doc):
        cmd = doc.get("cmd")
        self.write_log(f"BLE RX: {cmd}")

        if cmd == "rescan":
            self.firmware_state = "LEITURA"
            
        elif cmd == "sync_inventory":
            self.write_log("Sincronizando inventário local...")
            inv = {"tools": []}
            for t in doc.get("tools", []):
                inv["tools"].append({"id": t.get("id"), "name": t.get("name"), "epc": t.get("epc")})
            self.fs.write_json(self.fs.inventory_path, inv)
            reply = {"type": "cmd_reply", "cmd": "sync_inventory", "status": "ok"}
            asyncio.run_coroutine_threadsafe(self.notify_event(json.dumps(reply)), self.loop)

        elif cmd == "start_radar":
            self.firmware_state = "RASTREIA"
            self.radar_target_epc = doc.get("tag", "")
            
        elif cmd == "stop_radar":
            self.firmware_state = "ESCUTA"
            
        elif cmd == "auth":
            reply = {"type": "cmd_reply", "cmd": "auth", "status": "ok"}
            asyncio.run_coroutine_threadsafe(self.notify_event(json.dumps(reply)), self.loop)
            
        elif cmd == "get_config":
            out = dict(self.config)
            out.update({"type": "cmd_reply", "cmd": "get_config", "status": "ok", "fw_version": "2.0-SIM"})
            asyncio.run_coroutine_threadsafe(self.notify_event(json.dumps(out)), self.loop)
            
        elif cmd == "set_config":
            for k in ["tx_power_dbm", "beep", "rssi_offset", "listen_ms", "radar_ms"]:
                if k in doc: self.config[k] = doc[k]
            self.fs.write_json(self.fs.config_path, self.config)
            out = dict(self.config)
            out.update({"type": "cmd_reply", "cmd": "set_config", "status": "ok"})
            asyncio.run_coroutine_threadsafe(self.notify_event(json.dumps(out)), self.loop)
            
        elif cmd == "capture_tag":
            # Simulate picking up the strongest tag in env not yet configured? Or just a random one.
            self.write_log("Capturando Tag na Antena...")
            self.update_oled("LENDO TAG\nAproxime agora")
            # Encontrar tag mais forte no ambiente
            best_tag, best_rssi = "E200000000000000", -100
            for t in self.env_tools:
                if t["rssi"] > best_rssi:
                    best_rssi = t["rssi"]
                    best_tag = t["epc"]
            
            def send_capture():
                reply = {"type": "cmd_reply", "cmd": "capture_tag", "status": "ok", "tag": best_tag, "rssi": best_rssi}
                asyncio.run_coroutine_threadsafe(self.notify_event(json.dumps(reply)), self.loop)
                self.update_oled("TAG GRAVADA\nOK!")
            self.after(1500, send_capture)

        elif cmd == "find_device":
            self.findme_end_at = time.time() + doc.get("duration_sec", 5)
            self.firmware_state = "FINDME"
            reply = {"type": "cmd_reply", "cmd": "find_device", "status": "ok"}
            asyncio.run_coroutine_threadsafe(self.notify_event(json.dumps(reply)), self.loop)

        elif cmd == "get_history":
            events = self.fs.read_json(self.fs.events_path, [])
            reply = {"type": "cmd_reply", "cmd": "get_history", "status": "ok", "total": len(events), "has_more": False, "history": events}
            asyncio.run_coroutine_threadsafe(self.notify_event(json.dumps(reply)), self.loop)

        elif cmd == "get_sensors":
            reply = {
                "type": "cmd_reply", "cmd": "get_sensors", "status": "ok",
                "payload": {"has_oled": True, "has_bme280": True, "has_ina219": True, "batt_pct": self.battery}
            }
            asyncio.run_coroutine_threadsafe(self.notify_event(json.dumps(reply)), self.loop)

    async def notify_inventory(self, json_str):
        writer = streams.DataWriter()
        writer.unicode_encoding = streams.UnicodeEncoding.UTF8
        writer.write_string(json_str)
        await self.char_inventory.notify_value_async(writer.detach_buffer())

    async def notify_event(self, json_str):
        writer = streams.DataWriter()
        writer.unicode_encoding = streams.UnicodeEncoding.UTF8
        writer.write_string(json_str)
        await self.char_event.notify_value_async(writer.detach_buffer())

if __name__ == "__main__":
    app = VirtualTrakrPro()
    app.mainloop()
