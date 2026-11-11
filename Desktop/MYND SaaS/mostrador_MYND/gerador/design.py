"""Gera as imagens do mostrador MYND (240x296) e os sprites."""
import numpy as np
from PIL import Image, ImageDraw, ImageFont, ImageFilter

U = '/root/.claude/uploads/7b96b73d-2d2c-5012-82f8-7111ae7ae94a/'
V = U + '959d7c69-WhatsApp_Video_2026-09-24_at_15.08.39.mp4'
FONTS = 'fonts/'
W, H = 240, 296
CYAN = (0, 212, 245)
BLUE = (46, 140, 235)
WHITE = (255, 255, 255)
SS = 4  # supersampling


def alpha_from_light(path, chan=None):
    """imagem com fundo claro -> máscara alpha (0..255)"""
    a = np.asarray(Image.open(path).convert('RGB')).astype(np.float32)
    bg = 247.0
    v = a.min(axis=2) if chan is None else a[..., chan]
    al = np.clip((bg - v) / (bg - 40) * 255 * 1.15, 0, 255)
    return al.astype(np.uint8)


def crop_alpha(al):
    ys, xs = np.where(al > 20)
    return al[ys.min():ys.max() + 1, xs.min():xs.max() + 1]


def tint(mask, color, opacity=1.0):
    m = Image.fromarray(mask) if isinstance(mask, np.ndarray) else mask
    im = Image.new('RGBA', m.size, color + (0,))
    im.putalpha(m.point(lambda v: int(v * opacity)))
    return im


def wordmark(width):
    """'MYND' (do logo claro) em branco + barra ciano/branca"""
    al = alpha_from_light(U + '59933e7d-image.jpg')
    al[238:260, :] = 0  # remove a barra original (redesenhada nítida)
    letters = crop_alpha(al)
    lw, lh = letters.shape[1], letters.shape[0]
    s = width / lw
    L = Image.fromarray(letters).resize((width, round(lh * s)), Image.LANCZOS)
    bar_h = max(3, round(8 * s * 0.8))
    gap = max(3, round(6 * s))
    bw = round(width * 1.12)
    out = Image.new('RGBA', (bw, L.size[1] + gap + bar_h), (0, 0, 0, 0))
    out.alpha_composite(tint(L, WHITE), ((bw - width) // 2 + round(width * 0.03), 0))
    d = ImageDraw.Draw(out)
    y0 = L.size[1] + gap
    split = round(bw * 0.78)
    d.rectangle([0, y0, split, y0 + bar_h - 1], fill=CYAN + (255,))
    d.rectangle([split, y0, bw - 1, y0 + bar_h - 1], fill=WHITE + (255,))
    return out


def cube(size, opacity):
    al = crop_alpha(alpha_from_light(U + 'eeb3af79-image.jpg', chan=0))
    m = Image.fromarray(al).resize((size, round(al.shape[0] * size / al.shape[1])), Image.LANCZOS)
    return tint(m, BLUE, opacity)


def mesh_frame(t=4.0):
    import subprocess, io
    png = subprocess.run(['ffmpeg', '-v', 'error', '-ss', str(t), '-i', V, '-frames:v', '1',
                          '-f', 'image2pipe', '-vcodec', 'png', '-'], capture_output=True).stdout
    return Image.open(io.BytesIO(png)).convert('RGB')


def background(mesh_strength=0.55, cube_op=0.26):
    bg = Image.new('RGBA', (W, H), (0, 0, 0, 255))
    # malha do vídeo (parte de baixo do frame, sem o logo), só na metade inferior com fade
    fr = mesh_frame()
    fw, fh = fr.size
    crop = fr.crop((0, int(fh * 0.62), fw, fh)).resize((W, int(W * (fh * 0.38) / fw)), Image.LANCZOS)
    m = np.asarray(crop).astype(np.float32) * mesh_strength
    mh = m.shape[0]
    fade = np.clip(np.linspace(0, 1.4, mh), 0, 1)[:, None, None]
    m = (m * fade).astype(np.uint8)
    layer = Image.fromarray(m).convert('RGBA')
    bg.alpha_composite(layer, (0, H - mh))
    # cubo MYND discreto atrás dos números
    c = cube(200, cube_op)
    bg.alpha_composite(c, ((W - c.size[0]) // 2, 62 + (170 - c.size[1]) // 2))
    return bg


def rounded(im, r=26):
    mask = Image.new('L', (im.size[0] * SS, im.size[1] * SS), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, mask.size[0] - 1, mask.size[1] - 1], r * SS, fill=255)
    mask = mask.resize(im.size, Image.LANCZOS)
    out = Image.new('RGBA', im.size, (0, 0, 0, 255))
    out.paste(im, (0, 0), mask)
    return out


def glyph(text, w, h, font, size, color, anchor='mm', dx=0, dy=0):
    im = Image.new('RGBA', (w * SS, h * SS), (0, 0, 0, 0))
    f = ImageFont.truetype(FONTS + font, size * SS)
    ImageDraw.Draw(im).text((w * SS / 2 + dx * SS, h * SS / 2 + dy * SS), text, font=f, fill=color + (255,), anchor=anchor)
    return im.resize((w, h), Image.LANCZOS)


def fit_size(chars, w, h, font, maxsize):
    """maior tamanho de fonte em que todos os chars cabem em w x h"""
    for s in range(maxsize, 4, -1):
        f = ImageFont.truetype(FONTS + font, s)
        ok = True
        for ch in chars:
            l, t, r, b = f.getbbox(ch, anchor='mm')
            if r - l > w - 2 or b - t > h - 2:
                ok = False
                break
        if ok:
            return s
    return 5
