import argparse
import os

HW_FLAGS = {
    "oled": "TRAKR_HAS_OLED",
    "btn2": "TRAKR_HAS_BTN2",
    "batt": "TRAKR_HAS_INA219",
    "batt18650": "TRAKR_HAS_18650",
    "vib": "TRAKR_HAS_VIBRATOR",
    "bme280": "TRAKR_HAS_BME280",
    "imu": "TRAKR_HAS_MPU6050",
    "buzpass": "TRAKR_HAS_PASSIVE_BUZZER",
}

SW_FLAGS = {}

ANTENNAS = ["interna", "dip2", "patch3", "panel45", "circ6", "panel8", "sim"]

def main():
    p = argparse.ArgumentParser(description="Gera platformio.ini com o build customizado do TRK-Finder")
    p.add_argument("--antenna", default="interna", choices=ANTENNAS)
    p.add_argument("--mcu", default="esp32", choices=["esp32", "esp32-s3"], help="MCU (esp32 ou esp32-s3)")
    p.add_argument("--hw", default="", help="Addons de hardware separados por espaço (valores do wizard)")
    p.add_argument("--sw", default="", help="Addons de software separados por espaço (valores do wizard)")
    args = p.parse_args()

    hw = args.hw.split()
    sw = args.sw.split()

    flags = []
    if args.antenna == "sim":
        flags.append("-DTRAKR_SIM")
    for h in hw:
        if h in HW_FLAGS:
            flags.append("-D" + HW_FLAGS[h])
    for s in sw:
        if s in SW_FLAGS:
            flags.append("-D" + SW_FLAGS[s])

    if args.mcu == "esp32-s3":
        env = "esp32s3radar-sim" if args.antenna == "sim" else "esp32s3radar"
        board = "esp32-s3-devkitc-1"
    else:
        env = "esp32radar-sim" if args.antenna == "sim" else "esp32radar"
        board = "esp32dev"

    flag_block = "\n".join("  " + f for f in flags) if flags else "  ; nenhum addon customizado"
    flash_block = (
        "board_build.flash_size = 4MB\nboard_build.partitions = partitions/ota_4mb.csv"
        if args.mcu == "esp32"
        else "board_build.flash_size = 8MB\nboard_build.partitions = partitions/ota_8mb.csv"
    )

    ini = f"""; TRK-Finder - Firmware {args.mcu} (gerado pelo setup customizado)
; Antena: {args.antenna} | MCU: {args.mcu} | HW: {", ".join(hw) or "nenhum"} | SW: {", ".join(sw) or "nenhum"}

[env:{env}]
platform = espressif32
board = {board}
framework = arduino
monitor_speed = 115200

; Upload dos arquivos (inventory.json) para o LittleFS
board_build.filesystem = littlefs
upload_filesystem = pio run --target uploadfs

; OTA: tabela com otadata + app0/app1 (rollback automático se o FW travar)
{flash_block}

build_flags =
  -DCORE_DEBUG_LEVEL=1
  -Iinclude/
{flag_block}

lib_deps =
  bblanchon/ArduinoJson@^7.0.0
  h2zero/NimBLE-Arduino@^2.2.0
  fastled/FastLED@^3.6.0
  adafruit/Adafruit GFX Library@^1.11.9
  adafruit/Adafruit SSD1306@^2.5.7
  adafruit/Adafruit INA219@^1.2.0
  adafruit/Adafruit BME280 Library@^2.2.4
  adafruit/Adafruit Unified Sensor@^1.1.14
  ElectronicCats/MPU6050@^0.5.0
"""

    here = os.path.dirname(os.path.abspath(__file__))
    path = os.path.join(here, "..", "platformio.ini")
    with open(path, "w", encoding="utf-8") as f:
        f.write(ini)
    print(f"platformio.ini gerado em {os.path.normpath(path)} (env: {env})")

if __name__ == "__main__":
    main()