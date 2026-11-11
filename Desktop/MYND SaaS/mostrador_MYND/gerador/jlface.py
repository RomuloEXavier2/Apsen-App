"""Leitor/gerador do formato de mostrador 'JL' do Haylou Watch S6 (engenharia reversa).

Layout (offsets absolutos no arquivo):
  0x000  8 bytes zero + ID ascii (8 bytes, ex.: b'001002JL')
  0x08C  0x01 + 1 byte por elemento (0x06 = elemento de imagem, 0x05 = texto)
  0x0B4  0x01, flag ; 0x0BC u32 = 0x4EA
  0x0C0  tabela de elementos, 0x84 bytes cada:
           u8 0x06, u8 tipo, u16 x, u16 y, u16 0, u32 offsets[31]
  (depois da tabela: registros de texto opcionais, até 0x5E8)
  0x5E8  prévia 156x214 RGB565 big-endian (sem cabeçalho)
  0x10AB8 cabeçalho 4B + fundo 240x296 RGB565 BE
  0x335BC recursos: cabeçalho 4B + pixels ; offset_tabela = pos_arquivo - 0x20B4
Cabeçalho de imagem (u32 LE): cf:5 | extra:5 | w:11 | h:11
  cf=4 -> RGB565 BE (2 B/px) ; cf=5 -> A8 + RGB565 BE (3 B/px, cor pré-multiplicada)
"""
import struct
import numpy as np

BASE = 0x20B4
TABLE = 0xC0
ENTRY = 0x84
PREV_OFF, PREV_W, PREV_H = 0x5E8, 156, 214
BG_HDR, BG_W, BG_H = 0x10AB8, 240, 296
RES_START = BG_HDR + 4 + BG_W * BG_H * 2  # 0x335BC


def img_header(cf, w, h, extra=0):
    return struct.pack('<I', cf | (extra << 5) | (w << 10) | (h << 21))


def parse_header(b):
    v = struct.unpack('<I', b)[0]
    return v & 0x1F, (v >> 5) & 0x1F, (v >> 10) & 0x7FF, (v >> 21) & 0x7FF


def parse(d):
    f = {'head': bytearray(d[:TABLE]), 'elements': [], 'res': {}}
    o = TABLE
    while d[o] == 6:
        typ = d[o + 1]
        x, y, z = struct.unpack('<HHH', d[o + 2:o + 8])
        offs = [struct.unpack('<I', d[o + 8 + 4 * k:o + 12 + 4 * k])[0] for k in range(31)]
        offs = [v for v in offs if v]
        f['elements'].append({'type': typ, 'x': x, 'y': y, 'z': z, 'offs': offs})
        o += ENTRY
    f['table_end'] = o
    f['tail'] = bytes(d[o:PREV_OFF])  # registros de texto etc.
    f['preview'] = bytes(d[PREV_OFF:BG_HDR])
    f['bg_hdr'] = bytes(d[BG_HDR:BG_HDR + 4])
    f['bg'] = bytes(d[BG_HDR + 4:RES_START])
    for e in f['elements']:
        for off in e['offs']:
            if off in f['res']:
                continue
            p = off + BASE
            cf, ex, w, h = parse_header(d[p:p + 4])
            bpp = 3 if cf == 5 else 2
            f['res'][off] = bytes(d[p:p + 4 + w * h * bpp])
    return f


def build(f):
    """f: dict como parse(); em elements, 'imgs' (lista de bytes com cabeçalho) substitui 'offs'."""
    out = bytearray(RES_START)
    out[:TABLE] = f['head']
    # recursos
    res_blob = bytearray()
    placed = {}
    tables = []
    for e in f['elements']:
        offs = []
        for img in e['imgs']:
            key = img
            if key not in placed:
                placed[key] = RES_START + len(res_blob) - BASE
                res_blob += img
            offs.append(placed[key])
        tables.append(offs)
    # tabela
    o = TABLE
    for e, offs in zip(f['elements'], tables):
        ent = bytearray(ENTRY)
        struct.pack_into('<BBHHH', ent, 0, 6, e['type'], e['x'], e['y'], e.get('z', 0))
        for k, v in enumerate(offs):
            struct.pack_into('<I', ent, 8 + 4 * k, v)
        out[o:o + ENTRY] = ent
        o += ENTRY
    tail = f['tail']
    out[o:o + len(tail)] = tail
    assert o + len(tail) == PREV_OFF, 'tabela/tail não cabem'
    out[PREV_OFF:BG_HDR] = f['preview']
    out[BG_HDR:BG_HDR + 4] = f['bg_hdr']
    out[BG_HDR + 4:RES_START] = f['bg']
    return bytes(out + res_blob)


def with_imgs(f):
    for e in f['elements']:
        e['imgs'] = [f['res'][o] for o in e['offs']]
    return f


# ---------- conversão de imagens ----------
def rgb565(rgb):
    rgb = rgb.astype(np.uint16)
    return ((rgb[..., 0] >> 3) << 11) | ((rgb[..., 1] >> 2) << 5) | (rgb[..., 2] >> 3)


def encode_rgb565(im):
    """PIL RGB -> bytes RGB565 big-endian"""
    a = np.asarray(im.convert('RGB'))
    return rgb565(a).astype('>u2').tobytes()


def encode_sprite(im):
    """PIL RGBA -> bytes do recurso cf=5 (cabeçalho + A,Hi,Lo pré-multiplicado)"""
    a = np.asarray(im.convert('RGBA')).astype(np.float32)
    al = a[..., 3:4] / 255.0
    pm = np.clip(a[..., :3] * al + 0.5, 0, 255).astype(np.uint8)
    c = rgb565(pm)
    h, w = c.shape
    px = np.zeros((h, w, 3), np.uint8)
    px[..., 0] = a[..., 3].astype(np.uint8)
    px[..., 1] = c >> 8
    px[..., 2] = c & 0xFF
    return img_header(5, w, h) + px.tobytes()


def encode_opaque(im):
    w, h = im.size
    return img_header(4, w, h) + encode_rgb565(im)


def decode_res(b):
    cf, ex, w, h = parse_header(b[:4])
    if cf == 5:
        a = np.frombuffer(b[4:4 + w * h * 3], np.uint8).reshape(h, w, 3)
        c = (a[..., 1].astype(np.uint32) << 8) | a[..., 2]
        al = a[..., 0]
    else:
        c = np.frombuffer(b[4:4 + w * h * 2], '>u2').reshape(h, w).astype(np.uint32)
        al = np.full((h, w), 255, np.uint8)
    r = ((c >> 11) & 31) * 255 // 31
    g = ((c >> 5) & 63) * 255 // 63
    bl = (c & 31) * 255 // 31
    return np.dstack([r, g, bl, al]).astype(np.uint8), cf


def decode_rgb565(b, w, h):
    c = np.frombuffer(b[:w * h * 2], '>u2').reshape(h, w).astype(np.uint32)
    return np.dstack([((c >> 11) & 31) * 255 // 31, ((c >> 5) & 63) * 255 // 63,
                      (c & 31) * 255 // 31]).astype(np.uint8)
