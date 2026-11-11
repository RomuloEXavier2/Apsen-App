#!/usr/bin/env python3
"""Converte um PNG (com transparencia) em icons.h (RGB565 pre-multiplicado sobre preto).
Uso: python icon_maker.py bambulab_logo.png 22"""
import sys
from PIL import Image

src = sys.argv[1] if len(sys.argv) > 1 else "bambulab_logo.png"
h = int(sys.argv[2]) if len(sys.argv) > 2 else 22
im = Image.open(src).convert("RGBA")
im = im.crop(im.getbbox())                      # tira a borda transparente
w = max(1, round(im.width * h / im.height))
im = im.resize((w, h), Image.LANCZOS)
px = []
for r, g, b, a in im.getdata():
    r, g, b = r * a // 255, g * a // 255, b * a // 255
    c = ((r & 0xF8) << 8) | ((g & 0xFC) << 3) | (b >> 3)
    if a < 24: c = 0
    elif c == 0: c = 0x0821                     # preto opaco nao pode virar "transparente"
    px.append(c)
with open("icons.h", "w") as f:
    f.write("// Gerado por icon_maker.py a partir de %s - nao editar\n#pragma once\n#include <Arduino.h>\n" % src)
    f.write("#define BAMBU_W %d\n#define BAMBU_H %d\n" % (w, h))
    f.write("const uint16_t bambu_icon[BAMBU_W * BAMBU_H] PROGMEM = {\n")
    for i in range(0, len(px), 16):
        f.write(",".join("0x%04X" % v for v in px[i:i + 16]) + ",\n")
    f.write("};\n")
print("icons.h: %dx%d" % (w, h))
