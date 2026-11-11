#!/usr/bin/env python3
"""
Gera assets.h: fontes e icones com ANTI-ALIASING (alfa de 8 bits) para o mostrador.

Uso (dentro da pasta do sketch):
    pip install pillow
    python assets_maker.py

Le de assets_src/:
    SairaSemiCondensed-Light/Regular/Medium.ttf   (Google Fonts, licenca OFL)
    bambulab_logo.png
Escreve assets.h (nao editar a mao).
"""
import os
from PIL import Image, ImageDraw, ImageFont, ImageFilter

SRC = "assets_src"
REG = os.path.join(SRC, "SairaSemiCondensed-Regular.ttf")
LIGHT = os.path.join(SRC, "SairaSemiCondensed-Light.ttf")
MED = os.path.join(SRC, "SairaSemiCondensed-Medium.ttf")
LOGO = os.path.join(SRC, "bambulab_logo.png")

ASCII = [chr(c) for c in range(32, 127)] + ["°"]      # o grau vira o codigo 127 no firmware

out = []
def w(s=""): out.append(s)

def arr_u8(name, data):
    w("static const uint8_t %s[] PROGMEM = {" % name)
    for i in range(0, len(data), 32):
        w(",".join(str(v) for v in data[i:i + 32]) + ",")
    w("};")

def arr_u16(name, data):
    w("static const uint16_t %s[] PROGMEM = {" % name)
    for i in range(0, len(data), 16):
        w(",".join("0x%04X" % v for v in data[i:i + 16]) + ",")
    w("};")

def render_char(font, ch, glow=0, features=None):
    """Retorna (alpha bytes, w, h, xo, yo, adv). xo/yo relativos a origem na linha de base."""
    pad = 8 + glow * 3
    size = font.size * 2 + pad * 2
    im = Image.new("L", (size, size), 0)
    d = ImageDraw.Draw(im)
    bx, by = pad, font.size + pad
    d.text((bx, by), ch, font=font, fill=255, anchor="ls", features=features)
    adv = round(font.getlength(ch, features=features))
    if glow:
        im = im.filter(ImageFilter.MaxFilter(3)).filter(ImageFilter.GaussianBlur(glow))
        im = im.point(lambda v: min(255, int(v * 1.6)))
    box = im.getbbox()
    if not box:
        return b"", 0, 0, 0, 0, adv
    im = im.crop(box)
    return bytes(im.tobytes()), im.width, im.height, box[0] - bx, box[1] - by, adv

def font_block(name, path, px, chars, glow=0, features=None):
    font = ImageFont.truetype(path, px)
    bmp = bytearray()
    glyphs = []
    first = ord(chars[0]) if chars[0] != "°" else 127
    for ch in chars:
        a, gw, gh, xo, yo, adv = render_char(font, ch, glow, features)
        glyphs.append((len(bmp), gw, gh, xo, yo, adv))
        bmp += a
    codes = [127 if c == "°" else ord(c) for c in chars]
    assert codes == list(range(codes[0], codes[0] + len(codes))), "caracteres precisam ser contiguos"
    asc, desc = font.getmetrics()
    arr_u8(name + "_bmp", bmp)
    w("static const AAGlyph %s_g[] PROGMEM = {" % name)
    for g in glyphs:
        w("  {%d,%d,%d,%d,%d,%d}," % g)
    w("};")
    w("static const AAFont %s = {%s_bmp, %s_g, %d, %d, %d};" % (name, name, name, codes[0], codes[-1], asc))
    w()
    print("%-12s %3dpx  %2d glifos  %6d bytes" % (name, px, len(chars), len(bmp)))

def icon_rgba(name, im):
    im = im.convert("RGBA")
    rgb, al = [], []
    for r, g, b, a in im.getdata():
        rgb.append(((r & 0xF8) << 8) | ((g & 0xFC) << 3) | (b >> 3))
        al.append(a)
    arr_u16(name + "_rgb", rgb)
    arr_u8(name + "_a", al)
    w("static const AAIcon %s = {%d, %d, %s_rgb, %s_a};" % (name, im.width, im.height, name, name))
    w()

def icon_alpha(name, im):
    im = im.convert("L")
    arr_u8(name + "_a", list(im.getdata()))
    w("static const AAIcon %s = {%d, %d, nullptr, %s_a};" % (name, im.width, im.height, name))
    w()

def supersample(wpx, hpx, draw_fn, k=8):
    big = Image.new("L", (wpx * k, hpx * k), 0)
    draw_fn(ImageDraw.Draw(big), k)
    return big.resize((wpx, hpx), Image.LANCZOS)

# ----------------------------------------------------------------------------
w("// Gerado por assets_maker.py - nao editar")
w("#pragma once")
w("#include <Arduino.h>")
w("struct AAGlyph { uint32_t off; uint8_t w, h; int8_t xo, yo; uint8_t adv; };")
w("struct AAFont  { const uint8_t* bmp; const AAGlyph* g; uint8_t first, last, asc; };")
w("struct AAIcon  { uint8_t w, h; const uint16_t* rgb; const uint8_t* a; };   // rgb nullptr = so alfa (cor no desenho)")
w()

DIG = [chr(c) for c in range(ord("0"), ord(":") + 1)]
font_block("F_TIME", LIGHT, 60, DIG, features=["tnum"])              # hora
font_block("F_TIMEGLOW", LIGHT, 60, DIG, glow=4, features=["tnum"])  # brilho da hora (borrado)
font_block("F_LBL", REG, 17, ASCII)                                 # "25 C", texto da notificacao
font_block("F_SML", REG, 13, ASCII)                                 # "68%", nome do app
font_block("F_TTL", MED, 21, ASCII)                                 # remetente

# Logo da Bambu (imagem do usuario) com transparencia
logo = Image.open(LOGO).convert("RGBA")
logo = logo.crop(logo.getbbox())
H = 17
logo = logo.resize((max(1, round(logo.width * H / logo.height)), H), Image.LANCZOS)
icon_rgba("IC_BAMBU", logo)

# Termometro (contorno fino), desenhado em 8x e reduzido
def thermo(d, k):
    lw = int(1.15 * k)
    d.rounded_rectangle((2.6 * k, 0.5 * k, 6.4 * k, 9.6 * k), radius=1.9 * k, outline=255, width=lw)
    d.ellipse((1.0 * k, 7.6 * k, 8.0 * k, 14.6 * k), outline=255, width=lw)
    d.rectangle((3.0 * k + lw, 8.4 * k, 6.0 * k - lw, 9.9 * k), fill=0)        # abre a juncao
    d.ellipse((2.9 * k, 9.5 * k, 6.1 * k, 12.7 * k), fill=255)                 # bulbo
    d.rectangle((4.1 * k, 3.5 * k, 4.9 * k, 10.5 * k), fill=255)               # coluna
    for yy in (2.4, 4.4, 6.4):
        d.line((7.2 * k, yy * k, 8.6 * k, yy * k), fill=255, width=int(0.8 * k))
icon_alpha("IC_THERMO", supersample(9, 15, thermo))

# Balao de conversa generico com 3 pontos vazados
def bubble(d, k):
    d.rounded_rectangle((0.5 * k, 0.5 * k, 19.5 * k, 13.5 * k), radius=5 * k, fill=255)
    d.polygon([(4 * k, 12 * k), (9 * k, 12 * k), (3 * k, 17.5 * k)], fill=255)
    for cx in (5.5, 10, 14.5):
        d.ellipse(((cx - 1.6) * k, 5.4 * k, (cx + 1.6) * k, 8.6 * k), fill=0)
icon_alpha("IC_BUBBLE", supersample(20, 18, bubble))

open("assets.h", "w").write("\n".join(out) + "\n")
print("assets.h gerado (%d KB)" % (os.path.getsize("assets.h") // 1024))