#!/usr/bin/env python3
"""
Converte os frames da animacao em um unico arquivo frames.h
(JPEGs 240x240 embutidos direto no firmware - sem LittleFS, sem plugin de upload).

Uso:
    python make_frames.py PASTA_DOS_FRAMES [qualidade]

Exemplo:
    python make_frames.py frames 75

Depois copie o frames.h para a pasta do sketch (ao lado do monitor_vw.ino).
Requer:  pip install pillow
"""
import glob
import io
import os
import sys

from PIL import Image, ImageDraw

src = sys.argv[1] if len(sys.argv) > 1 else "frames"
quality = int(sys.argv[2]) if len(sys.argv) > 2 else 75

files = []
for ext in ("*.jpg", "*.jpeg", "*.png"):
    files += glob.glob(os.path.join(src, ext))
files.sort()
if not files:
    sys.exit(f"Nenhum frame encontrado em '{src}'")

# Mascara circular: o que fica fora do display redondo vira preto (JPEG menor)
mask = Image.new("L", (240, 240), 0)
ImageDraw.Draw(mask).ellipse((0, 0, 239, 239), fill=255)

blobs = []
for f in files:
    im = Image.open(f).convert("RGB")
    if im.size != (240, 240):
        im = im.resize((240, 240), Image.LANCZOS)
    bg = Image.new("RGB", (240, 240), (0, 0, 0))
    bg.paste(im, (0, 0), mask)
    buf = io.BytesIO()
    # baseline (nao progressivo) e 4:2:0: o decoder do ESP32 le rapido
    bg.save(buf, "JPEG", quality=quality, progressive=False, subsampling=2)
    blobs.append(buf.getvalue())

total = sum(len(b) for b in blobs)

with open("frames.h", "w") as h:
    h.write("// Gerado por make_frames.py - nao editar\n")
    h.write("#pragma once\n#include <Arduino.h>\n\n")
    h.write(f"#define N_FRAMES {len(blobs)}\n\n")
    for i, b in enumerate(blobs):
        h.write(f"const uint8_t f{i}[] PROGMEM = {{" + ",".join(map(str, b)) + "};\n")
    h.write("\nconst uint8_t* const FRAMES[N_FRAMES] = {"
            + ",".join(f"f{i}" for i in range(len(blobs))) + "};\n")
    h.write("const uint32_t FRAME_LEN[N_FRAMES] = {"
            + ",".join(str(len(b)) for b in blobs) + "};\n")

print(f"{len(blobs)} frames, {total / 1024:.0f} KB de JPEG -> frames.h")
if total > 2_800_000:
    print("ATENCAO: passa de ~2,8 MB. Rode de novo com qualidade menor (ex.: 60).")
elif total > 1_200_000:
    print("Use Tools > Partition Scheme > 'Huge APP (3MB No OTA/1MB SPIFFS)'.")