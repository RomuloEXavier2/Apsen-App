"""Mostrador MYND HUD (arte do usuário adaptada para 240x296) + dados reais."""
import struct, math
import numpy as np
from PIL import Image, ImageDraw, ImageFont, ImageFilter
from design import wordmark, cube, alpha_from_light, crop_alpha, U
from jlface import *

W, H, S = 240, 296, 4
CY = (0, 200, 255)
CY_D = (0, 110, 160)
CY_DD = (0, 55, 85)
WHITE = (255, 255, 255)
GRAY = (150, 165, 175)
F = 'fonts/'
T = open('xfer1.bin', 'rb').read()   # modelo com campos de texto

# ---------------- layout (px na tela 240x296) ----------------
DIG_W, DIG_H = 44, 56
TIME_Y = 40
HX = (10, 55); COLON_X = 101; MX = (111, 156); SEC_XY = (202, 76)
DATE_Y = 13
WEEK = dict(x=58, y=DATE_Y, w=44, h=20)
DAY_X = (104, 120); DAY_W, DAY_H = 16, 22
MONTH_XY = (140, 14)
PANEL_W, PANEL_H = 56, 58
PANELS = {  # nome: (x, y)
    'steps': (5, 106), 'kcal': (5, 168), 'batt': (179, 106), 'hr': (179, 168)}
MUSIC_Y = 234


def P(v):  # escala supersample
    return [int(round(c * S)) for c in v] if isinstance(v, (list, tuple)) else int(round(v * S))


def glow(layer, radius=3, strength=1.6):
    a = np.asarray(layer).astype(np.float32)
    g = np.asarray(layer.filter(ImageFilter.GaussianBlur(radius * S))).astype(np.float32)
    out = np.clip(a + g * strength, 0, 255).astype(np.uint8)
    return Image.fromarray(out)


def octagon(x, y, w, h, c):
    return [P((x + c, y)), P((x + w - c, y)), P((x + w, y + c)), P((x + w, y + h - c)),
            P((x + w - c, y + h)), P((x + c, y + h)), P((x, y + h - c)), P((x, y + c))]


def icon_shoe(d, cx, cy, s, col):
    pts = [(-9, 3), (-9, -2), (-5, -6), (-2, -3), (2, -3), (9, 1), (9, 4), (-9, 4)]
    d.polygon([P((cx + x * s / 9, cy + y * s / 9)) for x, y in pts], fill=col)
    for k in range(3):
        d.line([P((cx - 3 + k * 3, cy - 3 + k * 0.2)), P((cx - 1 + k * 3, cy - 1))], fill=(0, 0, 0), width=P(0.8))


def icon_flame(d, cx, cy, s, col):
    outer = [(0, -10), (3, -6), (6, -2), (7, 2), (6, 6), (3, 9), (0, 10), (-3, 9), (-6, 6), (-7, 2),
             (-6, -2), (-4, -4), (-3, -1), (-1, -5)]
    inner = [(0, 1), (2, 4), (2.5, 6.5), (1, 8.5), (0, 9), (-1, 8.5), (-2.5, 6.5), (-2, 4)]
    k = s / 10
    d.polygon([P((cx + x * k, cy + y * k)) for x, y in outer], fill=col)
    d.polygon([P((cx + x * k, cy + y * k)) for x, y in inner], fill=(0, 0, 0))


def icon_heart(d, cx, cy, s, col):
    r = s * 0.28
    d.ellipse([P(cx - 2 * r), P(cy - r * 1.3), P(cx), P(cy + r * 0.7)], fill=col)
    d.ellipse([P(cx), P(cy - r * 1.3), P(cx + 2 * r), P(cy + r * 0.7)], fill=col)
    d.polygon([P((cx - 2 * r + 0.3, cy - r * 0.1)), P((cx + 2 * r - 0.3, cy - r * 0.1)), P((cx, cy + s * 0.62))], fill=col)
    ecg = [(-7, 0), (-3, 0), (-1.5, -3.5), (0.5, 3), (2, -1), (3, 0), (7, 0)]
    d.line([P((cx + x * s / 16, cy - s * 0.12 + y * s / 16)) for x, y in ecg], fill=(0, 0, 0), width=P(1.1))


def text(d, xy, s, size, col, font='orbitron-700.ttf', anchor='mm'):
    d.text(P(xy), s, font=ImageFont.truetype(F + font, P(size)), fill=col, anchor=anchor)


def icon_nozzle(d, cx, cy, s, col):
    k = s / 10
    d.rectangle([P(cx - 7 * k), P(cy - 9 * k), P(cx + 7 * k), P(cy - 3 * k)], fill=col)
    d.polygon([P((cx - 5 * k, cy - 2 * k)), P((cx + 5 * k, cy - 2 * k)), P((cx + 1.5 * k, cy + 5 * k)), P((cx - 1.5 * k, cy + 5 * k))], fill=col)
    d.ellipse([P(cx - 1.5 * k), P(cy + 7 * k), P(cx + 1.5 * k), P(cy + 10 * k)], fill=col)


BAR_X, BAR_W, BAR_H = 62, 116, 8
BAR_Y = MUSIC_Y + 22


def progress_bar(i):
    im = Image.new('RGBA', (BAR_W, BAR_H), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    fill = round(BAR_W * i / 15)
    for x in range(0, BAR_W, 6):
        d.rectangle([x, 0, x + 3, BAR_H - 1], fill=(CY + (255,)) if x + 4 <= fill else (CY_DD + (255,)))
    return im


def background(bambu=False):
    im = Image.new('RGB', (W * S, H * S), (0, 0, 0))
    lines = Image.new('RGB', im.size, (0, 0, 0))
    d = ImageDraw.Draw(lines)
    lw = P(1.4)
    # moldura externa com cantos "HUD"
    d.rounded_rectangle([P(3), P(3), P(W - 4), P(H - 4)], P(30), outline=CY_D, width=P(1.2))
    for (x0, y0, sx, sy) in ((6, 6, 1, 1), (W - 7, 6, -1, 1), (6, H - 7, 1, -1), (W - 7, H - 7, -1, -1)):
        d.line([P((x0 + sx * 34, y0)), P((x0 + sx * 12, y0)), P((x0, y0 + sy * 12)), P((x0, y0 + sy * 44))], fill=CY, width=P(2.6), joint='curve')
        d.line([P((x0 + sx * 5, y0 + sy * 52)), P((x0 + sx * 5, y0 + sy * 70))], fill=CY_D, width=P(1.2))
    # moldura da data (topo)
    d.line([P((52, 8)), P((188, 8))], fill=CY_D, width=lw)
    d.line([P((40, 38)), P((56, 38)), P((62, 35)), P((178, 35)), P((184, 38)), P((200, 38))], fill=CY, width=lw)
    d.rectangle([P(118), P(4), P(122), P(8)], fill=CY)
    # trilhas de circuito laterais
    def trace(pts, dot=True, col=CY_D):
        d.line([P(p) for p in pts], fill=col, width=P(1.1), joint='curve')
        if dot:
            x, y = pts[-1]
            d.ellipse([P(x - 1.8), P(y - 1.8), P(x + 1.8), P(y + 1.8)], fill=CY)
    for sgn, xb in ((1, 0), (-1, W)):
        X = lambda v: xb + sgn * v
        trace([(X(10), 60), (X(22), 72), (X(22), 100)])
        trace([(X(14), 44), (X(32), 62), (X(40), 62), (X(58), 80)])
        trace([(X(62), 104), (X(62), 150), (X(58), 156)], dot=False)
        trace([(X(62), 228), (X(70), 220), (X(70), 190)])
        trace([(X(10), 232), (X(18), 240), (X(18), 262)])
        trace([(X(26), 280), (X(44), 262), (X(54), 262)])
    # painéis octogonais
    for name, (x, y) in PANELS.items():
        d.polygon(octagon(x, y, PANEL_W, PANEL_H, 9), outline=CY, width=P(1.6))
        d.polygon(octagon(x + 3, y + 3, PANEL_W - 6, PANEL_H - 6, 7), outline=CY_DD, width=P(1))
    # barra de música
    my = MUSIC_Y
    d.polygon([P((40, my + 18)), P((58, my)), P((182, my)), P((200, my + 18)), P((182, my + 36)), P((58, my + 36))], outline=CY_D, width=P(1.2))
    d.line([P((30, my + 18)), P((52, my + 18))], fill=CY_D, width=P(1))
    d.line([P((188, my + 18)), P((210, my + 18))], fill=CY_D, width=P(1))
    if not bambu:
        d.ellipse([P(106), P(my + 4), P(134), P(my + 32)], outline=CY, width=P(1.8))
    else:  # trilho da barra de progresso
        d.rectangle([P(BAR_X - 2), P(BAR_Y - 2), P(BAR_X + BAR_W + 1), P(BAR_Y + BAR_H + 1)], outline=CY_DD, width=P(1))
    # "//" base
    d.polygon([P((92, 280)), P((100, 272)), P((140, 272)), P((148, 280)), P((140, 288)), P((100, 288))], outline=CY_D, width=P(1))
    im = glow(lines, 2.2, 1.3)
    d = ImageDraw.Draw(im)
    # ícones de música
    c = my + 18
    if bambu:
        text(d, (62, my + 11), 'BAMBU', 8, CY, 'oxanium-800.ttf', anchor='lm')
        text(d, (100, my + 11), '% / MIN', 6.5, GRAY, 'oxanium-800.ttf', anchor='lm')
    else:
        d.polygon([P((74, c)), P((84, c - 7)), P((84, c + 7))], fill=CY); d.rectangle([P(71), P(c - 7), P(73), P(c + 7)], fill=CY)
        d.rectangle([P(114.5), P(c - 7), P(117.5), P(c + 7)], fill=CY); d.rectangle([P(122.5), P(c - 7), P(125.5), P(c + 7)], fill=CY)
        d.polygon([P((166, c)), P((156, c - 7)), P((156, c + 7))], fill=CY); d.rectangle([P(167), P(c - 7), P(169), P(c + 7)], fill=CY)
    d.polygon([P((114, 284)), P((119, 276)), P((123, 276)), P((118, 284))], fill=CY)
    d.polygon([P((121, 284)), P((126, 276)), P((130, 276)), P((125, 284))], fill=CY)
    # dois-pontos da hora
    for yy in (TIME_Y + 18, TIME_Y + 38):
        d.rectangle([P(COLON_X), P(yy), P(COLON_X + 7), P(yy + 7)], fill=WHITE)
    # ícones e rótulos dos painéis
    (sx, sy), (kx, ky), (bx, by), (hx, hy) = PANELS['steps'], PANELS['kcal'], PANELS['batt'], PANELS['hr']
    icon_shoe(d, sx + 28, sy + 14, 9, CY)
    (icon_nozzle if bambu else icon_flame)(d, kx + 28, ky + 13, 9, CY)
    if bambu:  # bateria fixa
        d.rectangle([P(bx + 18), P(by + 9), P(bx + 36), P(by + 18)], outline=CY, width=P(1.4))
        d.rectangle([P(bx + 37), P(by + 12), P(bx + 38.5), P(by + 15)], fill=CY)
        d.rectangle([P(bx + 20), P(by + 11), P(bx + 30), P(by + 16)], fill=CY)
    icon_heart(d, hx + 28, hy + 14, 10, CY)
    for (x, y), lab in (((sx, sy), 'PASSOS'), ((kx, ky), 'BICO' if bambu else 'KCAL'), ((hx, hy), 'BPM')):
        text(d, (x + 28, y + 48), lab, 7, GRAY, 'oxanium-800.ttf')
    text(d, (bx + 28, by + 48), 'BATERIA', 7, GRAY, 'oxanium-800.ttf')
    im = im.convert('RGBA')
    # logo central: cubo + MYND + barra + slogan
    cb = cube(112, 1.0)
    a = np.asarray(cb).astype(np.float32); a[..., :3] = np.array(CY, np.float32)
    cb = Image.fromarray(a.astype(np.uint8), 'RGBA').resize((cb.size[0] * S, cb.size[1] * S), Image.LANCZOS)
    cx0 = P(120) - cb.size[0] // 2; cy0 = P(104)
    glow_l = Image.new('RGBA', im.size, (0, 0, 0, 0)); glow_l.alpha_composite(cb, (cx0, cy0))
    gl = glow_l.filter(ImageFilter.GaussianBlur(P(2.5)))
    im.alpha_composite(gl); im.alpha_composite(glow_l)
    wm = wordmark(92)
    wm = wm.resize((wm.size[0] * S, wm.size[1] * S), Image.LANCZOS)
    # halo azul atrás do MYND (esfera do logo)
    halo = Image.new('RGBA', im.size, (0, 0, 0, 0)); hd = ImageDraw.Draw(halo)
    hd.ellipse([P(108), P(150), P(132), P(174)], fill=(40, 140, 255, 255))
    im.alpha_composite(halo.filter(ImageFilter.GaussianBlur(P(5))))
    bk = Image.new('RGBA', im.size, (0, 0, 0, 0))
    ImageDraw.Draw(bk).rectangle([P(62), P(150), P(178), P(190)], fill=(0, 0, 0, 150))
    im.alpha_composite(bk.filter(ImageFilter.GaussianBlur(P(3))))
    im.alpha_composite(wm, (P(120) - wm.size[0] // 2, P(154)))
    d = ImageDraw.Draw(im)
    text(d, (120, 186), 'Make Your New Dreams', 7.2, WHITE, 'space-mono-400.ttf')
    out = im.convert('RGB').resize((W, H), Image.LANCZOS)
    return out


def sprite(txt, w, h, col, font='oxanium-800.ttf', size=None, chars=None, stretch=1.0):
    from design import fit_size, glyph
    import design
    design.FONTS = F
    iw = int(w / stretch)
    s = size or fit_size(chars or [txt], iw, h, font, 120)
    g = glyph(txt, iw, h, font, s, col)
    return g.resize((w, h), Image.LANCZOS) if stretch != 1.0 else g


def rgb888(c):
    return bytes(c)


def text_rec(tid, x, y, w, color=WHITE, align=1, tail=b'1000200000'):
    r = bytearray(32)
    r[0] = 5; r[1] = tid; struct.pack_into('<HH', r, 2, x, y); r[6] = w
    r[7:10] = bytes(color); r[10] = align; r[11] = 1; r[12:12 + len(tail)] = tail
    return bytes(r)


WEEK_PT = ['DOM', 'SEG', 'TER', 'QUA', 'QUI', 'SEX', 'SÁB']


def build_hud(bambu=False):
    assert text_rec(0x0d, 40, 21, 56, (0xff, 0xfa, 0xca)) == T[0x564:0x584]
    assert text_rec(0x12, 183, 20, 44, (0xff, 0xfa, 0xca)) == T[0x584:0x5a4]
    f = parse(T)
    info = f
    dig_h = [encode_sprite(sprite(str(i), DIG_W, DIG_H, WHITE, chars='0123456789', stretch=1.18)) for i in range(10)]
    dig_m = [encode_sprite(sprite(str(i), DIG_W, DIG_H, CY, chars='0123456789', stretch=1.18)) for i in range(10)]
    week = [encode_sprite(sprite(t, WEEK['w'], WEEK['h'], WHITE, chars=WEEK_PT)) for t in WEEK_PT]
    day = [encode_sprite(sprite(str(i), DAY_W, DAY_H, CY, chars='0123456789')) for i in range(10)]
    src = {e['type']: e for e in info['elements']}
    from make_face import recolor
    batt = [encode_sprite(recolor(info['res'][o], CY)) for o in src[23]['offs']]
    bx, by = PANELS['batt']
    els = [
        dict(type=1, x=HX[0], y=TIME_Y, z=0, imgs=dig_h[:3]),
        dict(type=2, x=HX[1], y=TIME_Y, z=0, imgs=dig_h),
        dict(type=3, x=MX[0], y=TIME_Y, z=0, imgs=dig_m[:6]),
        dict(type=4, x=MX[1], y=TIME_Y, z=0, imgs=dig_m),
        dict(type=31, x=WEEK['x'], y=WEEK['y'], z=0, imgs=week),
        dict(type=10, x=DAY_X[0], y=DATE_Y - 1, z=0, imgs=day),
        dict(type=11, x=DAY_X[1], y=DATE_Y - 1, z=0, imgs=day),
        dict(type=23, x=bx + 18, y=by + 9, z=0, imgs=batt),
    ]
    if bambu:  # 'ícone do clima' vira a barra de progresso (código 0..15)
        els = [e for e in els if e['type'] != 23]  # ícone de bateria vira desenho fixo (falta espaço na tabela)
        els.append(dict(type=6, x=BAR_X, y=BAR_Y, z=0, imgs=[encode_sprite(progress_bar(i)) for i in range(16)]))
    sx, sy = PANELS['steps']; kx, ky = PANELS['kcal']; hx, hy = PANELS['hr']
    texts = [
        text_rec(22, SEC_XY[0], SEC_XY[1], 30, WHITE, 1),          # segundos
        text_rec(27, MONTH_XY[0], MONTH_XY[1], 44, WHITE, 1),      # mês (SEP)
        text_rec(14, sx + 2, sy + 22, PANEL_W - 4, WHITE, 2),      # passos
        text_rec(13 if bambu else 17, kx + 2, ky + 22, PANEL_W - 4, WHITE, 2),  # bico °C (temp. do clima) ou kcal
        text_rec(18, bx + 2, by + 22, PANEL_W - 4, WHITE, 2),      # bateria %
        text_rec(5, hx + 2, hy + 22, PANEL_W - 4, WHITE, 2),       # batimento
    ]
    if bambu:  # mín/máx do clima = progresso / minutos restantes
        texts.append(text_rec(33, 124, MUSIC_Y + 2, 56, WHITE, 3))
    f['elements'] = els
    space = PREV_OFF - (TABLE + ENTRY * len(els))
    recs = b''.join(texts)
    assert len(recs) <= space, (len(recs), space)
    f['tail'] = recs + bytes(space - len(recs))
    head = bytearray(f['head']); head[0x8C:0xB4] = bytes(0x28); head[0x8C] = 1
    for i in range(len(els)): head[0x8D + i] = 6
    for i in range(len(texts)): head[0x8D + len(els) + i] = 5
    f['head'] = head
    bg = background(bambu)
    f['bg'] = encode_rgb565(bg)
    return f, bg, texts


def simulate(b, vals):
    """desenha como o relógio: fundo + imagens + textos (fonte aproximada)"""
    from make_face import render
    img = render(b, vals['h'], vals['m'], vals['wd'], vals['d'], vals.get('wi', 0), vals['bi']).convert('RGBA')
    g = parse(b)
    d = ImageDraw.Draw(img)
    fnt = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf', 15)
    tail = g['tail']
    for k in range(0, len(tail), 32):
        r = tail[k:k + 32]
        if r[0] != 5: break
        tid = r[1]; x, y = struct.unpack('<HH', r[2:6]); w = r[6]; col = tuple(r[7:10]); al = r[10]
        s = vals['t'].get(tid, '?')
        tw = d.textlength(s, font=fnt)
        xx = x if al in (0, 1, 4) else (x + (w - tw) / 2 if al == 2 else x + w - tw)
        d.text((xx, y), s, font=fnt, fill=col)
    return img.convert('RGB')


if __name__ == '__main__':
    import sys
    bambu = 'bambu' in sys.argv
    name = 'MYND_HUD_BAMBU' if bambu else 'MYND_HUD'
    f, bg, texts = build_hud(bambu)
    f['preview'] = bytes(PREV_W * PREV_H * 2)
    b = build(f)
    t = {22: '32', 27: 'JUN', 14: '7420', 17: '328', 18: '78%', 5: '72'}
    if bambu:
        t.update({13: '220°C', 33: '67/82'})
    vals = dict(h=10, m=47, wd=6, d=28, bi=4, wi=round(67 / 100 * 15), t=t)
    shot = simulate(b, vals)
    from make_face import preview_from
    f['preview'] = encode_rgb565(preview_from(shot))
    b = build(f)
    open(f'out/{name}.bin', 'wb').write(b)
    shot.save(f'out/{name}_sim.png')
    print('ok', name, len(b))
