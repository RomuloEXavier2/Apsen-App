import copy, json
from design import *
from jlface import *

T_BIG = open('xfer2.bin', 'rb').read()   # modelo: planeta (números grandes)
T_INFO = open('xfer1.bin', 'rb').read()  # modelo: lua nascendo (clima + bateria)

DIG_FONT = 'michroma-400.ttf'
SMALL_FONT = 'michroma-400.ttf'
WEEK_PT = ['DOM', 'SEG', 'TER', 'QUA', 'QUI', 'SEX', 'SÁB']  # ordem do relógio: SUN..SAT

# posições (x, y)
HOUR_Y, MIN_Y = 56, 140
DIG_X = (60, 124)
DATE_Y = 236
WEEK_X = 81
DAY_X = (135, 147)
WEATHER_XY = (16, 17)
BATT_XY = (204, 22)


def big_digit(ch, color):
    w, h = 56, 78
    s = fit_size('0123456789', w, int(h / 1.22), DIG_FONT, 120)
    g = glyph(ch, w, int(h / 1.22), DIG_FONT, s, color)
    tall = g.resize((w, int(g.size[1] * 1.22)), Image.LANCZOS)
    out = Image.new('RGBA', (w, h), (0, 0, 0, 0))
    out.alpha_composite(tall, (0, (h - tall.size[1]) // 2))
    return out


def small_glyph(text, w, h, color, maxsize=40, chars=None):
    s = fit_size(chars or [text], w, h, SMALL_FONT, maxsize)
    return glyph(text, w, h, SMALL_FONT, s, color)


def recolor(res_bytes, color, keep_lum=True):
    """recolore um sprite do modelo mantendo alpha (e luminância como intensidade)"""
    rgba, cf = decode_res(res_bytes)
    a = rgba.astype(np.float32)
    lum = (a[..., 0] * 0.3 + a[..., 1] * 0.59 + a[..., 2] * 0.11) / 255.0
    if cf == 5:
        # cor pré-multiplicada: intensidade/alpha
        al = np.maximum(a[..., 3] / 255.0, 1e-3)
        inten = np.clip(lum / al, 0, 1)
        alpha = a[..., 3]
    else:
        inten = np.ones_like(lum)
        alpha = np.clip(lum * 255 * 1.4, 0, 255)  # opaco: preto vira transparente
    col = np.array(color, np.float32)[None, None, :] * (inten[..., None] if keep_lum else 1)
    out = np.dstack([np.clip(col, 0, 255), alpha]).astype(np.uint8)
    return Image.fromarray(out, 'RGBA')


def compose_static(full):
    bg = background()
    wm = wordmark(104)
    bg.alpha_composite(wm, ((W - wm.size[0]) // 2, 14))
    # tagline discreta
    tag = Image.new('RGBA', (W * SS, 14 * SS), (0, 0, 0, 0))
    f = ImageFont.truetype(FONTS + 'space-mono-400.ttf', 9 * SS)
    ImageDraw.Draw(tag).text((W * SS / 2, 7 * SS), 'Make Your New Dreams', font=f, fill=(170, 178, 186, 255), anchor='mm')
    bg.alpha_composite(tag.resize((W, 14), Image.LANCZOS), (0, 266))
    return rounded(bg.convert('RGB'), 28)


def build_face(full=True, face_id=None, cyan_min=True):
    f = parse(T_BIG)
    info = parse(T_INFO)
    hours = [encode_sprite(big_digit(str(i), WHITE)) for i in range(10)]
    mins = [encode_sprite(big_digit(str(i), CYAN)) for i in range(10)] if cyan_min else hours
    week = [encode_sprite(small_glyph(t, 48, 20, WHITE, chars=WEEK_PT)) for t in WEEK_PT]
    day = [encode_sprite(small_glyph(str(i), 12, 20, CYAN, chars='0123456789')) for i in range(10)]
    els = []
    for e in f['elements']:
        t = e['type']
        e = dict(e)
        if t == 1:   e.update(x=DIG_X[0], y=HOUR_Y, imgs=hours[:3])
        elif t == 2: e.update(x=DIG_X[1], y=HOUR_Y, imgs=hours)
        elif t == 3: e.update(x=DIG_X[0], y=MIN_Y, imgs=mins[:6])
        elif t == 4: e.update(x=DIG_X[1], y=MIN_Y, imgs=mins)
        elif t == 31: e.update(x=WEEK_X, y=DATE_Y, imgs=week)
        elif t == 10: e.update(x=DAY_X[0], y=DATE_Y, imgs=day)
        elif t == 11: e.update(x=DAY_X[1], y=DATE_Y, imgs=day)
        else: raise ValueError(t)
        els.append(e)
    if full:
        src = {e['type']: e for e in info['elements']}
        wx = src[6]; bx = src[23]
        weather = [encode_sprite(recolor(info['res'][o], WHITE)) for o in wx['offs']]
        batt = [encode_sprite(recolor(info['res'][o], CYAN)) for o in bx['offs']]
        els.append(dict(type=6, x=WEATHER_XY[0], y=WEATHER_XY[1], z=0, imgs=weather))
        els.append(dict(type=23, x=BATT_XY[0], y=BATT_XY[1], z=0, imgs=batt))
    f['elements'] = els
    # cabeçalho: lista de tipos de registro (0x01 + 0x06 por elemento)
    head = bytearray(f['head'])
    head[0x8C:0xB0] = bytes(0x24)
    head[0x8C] = 1
    for i in range(len(els)):
        head[0x8D + i] = 6
    if face_id:
        head[8:16] = face_id
    f['head'] = head
    f['tail'] = bytes(PREV_OFF - (TABLE + ENTRY * len(els)))
    static = compose_static(full)
    f['bg'] = encode_rgb565(static)
    return f, static


def render(binbytes, hh=10, mm=8, wd=1, dd=21, weather=1, batt=5):
    """desenha o mostrador a partir do .bin (como o relógio faria)"""
    g = parse(binbytes)
    img = Image.fromarray(decode_rgb565(g['bg'], BG_W, BG_H)).convert('RGBA')
    val = {1: hh // 10, 2: hh % 10, 3: mm // 10, 4: mm % 10, 31: wd, 10: dd // 10, 11: dd % 10,
           8: 0, 9: 9, 6: weather, 23: batt, 24: 0}
    for e in g['elements']:
        k = val.get(e['type'], 0)
        rgba, cf = decode_res(g['res'][e['offs'][min(k, len(e['offs']) - 1)]])
        a = rgba.astype(np.float32)
        if cf == 5:  # pré-multiplicado -> reto
            al = np.maximum(a[..., 3:4] / 255, 1e-3)
            a[..., :3] = np.clip(a[..., :3] / al, 0, 255)
        img.alpha_composite(Image.fromarray(a.astype(np.uint8), 'RGBA'), (e['x'], e['y']))
    return img.convert('RGB')


def preview_from(img):
    p = img.resize((PREV_W, PREV_H), Image.LANCZOS)
    return rounded(p, 18)


if __name__ == '__main__':
    import sys
    import os
    for p in ('out/MYND_simples.bin', 'out/MYND_simples_render.png'):
        if os.path.exists(p): os.remove(p)
    for full, cyan, name in ((True, True, 'MYND_completo'), (False, False, 'MYND_leve')):
        f, static = build_face(full, cyan_min=cyan)
        f['preview'] = bytes(PREV_W * PREV_H * 2)  # provisório
        b = build(f)
        shot = render(b)
        f['preview'] = encode_rgb565(preview_from(shot))
        b = build(f)
        open(f'out/{name}.bin', 'wb').write(b)
        render(b).save(f'out/{name}_render.png')
        print(name, len(b), [ (e['type'], e['x'], e['y'], len(e['imgs'])) for e in f['elements']])
