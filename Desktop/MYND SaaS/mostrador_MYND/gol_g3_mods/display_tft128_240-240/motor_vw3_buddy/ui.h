/*
 * ui.h - Mostrador MYND: relogio + temperatura + progresso Bambu + desk buddy + notificacoes
 *
 * Visual da referencia buddy_vw: fundo azul-petroleo com grade sutil, anel fino com dois
 * arcos ciano, marcas de minuto, hora fina ciano com brilho, termometro a esquerda e
 * logo da Bambu a direita. Tudo com ANTI-ALIASING (bordas suaves):
 *   - fontes e icones vem de assets.h (alfa de 8 bits, gerado por assets_maker.py)
 *   - circulos, arcos e linhas calculam a cobertura de cada pixel
 *   - o corpo do buddy e os olhos tem rampa de alfa na borda
 *
 * Tudo e desenhado num GFXcanvas16 (240x240) e enviado inteiro ao display a cada quadro.
 * Nao conhece BLE nem o display: tambem roda no simulador de PC (preview/).
 *
 * API usada pelo .ino:
 *   uiInit()                         uma vez no setup
 *   uiRender(millis(), tm)           desenha um quadro no canvas
 *   uiSetSynced(bool)                hora recebida do celular
 *   uiSetTemp(int c) / uiClearTemp() temperatura externa
 *   uiSetPrint(pct, estado)          'R' imprimindo, 'P' pausada, 'F' concluida, 'X' falhou, 'I' parada
 *   uiNotify(app, titulo, texto)     app: 'W' WhatsApp, 'I' Instagram, outro = generico
 *   uiExprByName(nome, ms)           "feliz", "surpresa", "raiva", "triste", "curioso", "normal"
 *   uiDemo()                         demonstracao de tudo
 */
#pragma once
#include <Arduino.h>
#include <Adafruit_GFX.h>
#include <time.h>
#include <math.h>
#include <string.h>
#include <stdio.h>
#include "assets.h"

#define W  240
#define H  240
#define CX 120
#define CY 120
#define C16 1912                 // centro da tela em 1/16 px (119,5 * 16)

#define C565(r, g, b) ((uint16_t)((((r) & 0xF8) << 8) | (((g) & 0xFC) << 3) | ((b) >> 3)))

// ---------------- paleta (tirada da referencia) ----------------
const uint16_t ARC_CORE  = C565(120, 225, 255);
const uint16_t ARC_GLOW  = C565(0, 140, 210);
const uint16_t RING_DIM  = C565(34, 58, 74);
const uint16_t TICK_MIN  = C565(48, 62, 74);
const uint16_t TICK_MAJ  = C565(105, 125, 140);
const uint16_t TIME_CORE = C565(95, 228, 255);
const uint16_t TIME_GLOW = C565(0, 150, 225);
const uint16_t LBL_COL   = C565(196, 216, 232);
const uint16_t WHITE     = C565(235, 245, 255);
const uint16_t ALERT     = C565(255, 60, 90);
const uint16_t OK_GREEN  = C565(60, 220, 120);
const uint16_t AMBER     = C565(255, 190, 60);
const uint16_t EYE_CORE  = C565(110, 240, 255);
const uint16_t EYE_GLOW  = C565(0, 150, 215);
const uint16_t CARD_BG   = C565(8, 16, 30);

GFXcanvas16 canvas(W, H);

// ===========================================================================
// Estado
// ===========================================================================
enum Expr { EX_NORMAL, EX_HAPPY, EX_SURPRISE, EX_ANGRY, EX_SAD, EX_CURIOUS };

struct Notif { char app; char title[34]; char text[160]; };

struct UiData {
  bool synced = false;
  bool hasTemp = false;
  int  temp = 0;
  int  pct = -1;
  char pst = 'I';
} ui;

struct Buddy {
  int   expr = EX_NORMAL;
  uint32_t exprUntil = 0;
  float lx = 0, ly = 0, tlx = 0, tly = 0;
  uint32_t lookUntil = 0;
  uint32_t blinkAt = 2500, blinkT0 = 0;
  uint32_t bounceT0 = 0, bounceUntil = 0;
  uint32_t nextIdle = 6000;
  int lastMin = -1;
} bd;

// Travessuras (modo relogio): a cada 5..12 s o buddy escolhe uma brincadeira
enum Antic { AN_NONE, AN_FLY, AN_HIDE, AN_STEAL, AN_ICONS, AN_SHAKE, AN_LOOP, AN_RAGE };
struct AnticState {
  int type = AN_NONE, last = AN_NONE;
  uint32_t t0 = 0, dur = 0, nextAt = 7000;
  int k = 4;               // digito roubado
  int pk[2] = {0, 1};      // esconderijos (so olhos)
  float ph = 0;            // fase do caminho de voo
  // AN_RAGE: 7 elementos (0 termometro, 1..5 digitos da hora, 6 Bambu) caindo com gravidade
  float ox[7], oy[7], vx[7], vy[7], sx[7], sy[7];
  bool hit[7], rest[7], snap;
  uint32_t lastE;
} an;
struct TimeMods { int8_t dx[5], dy[5]; uint8_t hide; } tmods;   // digitos tremendo / roubados
int8_t shL = 0, shR = 0, shLy = 0, shRy = 0;                   // icones cutucados / derrubados
float curX = 120, curY = 110, curS = 1.1f;                     // ultima pose (a notificacao sai dali)
float goX = 120, goY = 110, goS = 1.1f;

enum Mode { M_CLOCK, M_GO, M_PULL, M_HOP, M_SHOW, M_BACKIN, M_PUSH, M_HOME };
const uint16_t DUR[] = {0, 450, 800, 350, 3000, 350, 800, 650};
Mode mode = M_CLOCK;
uint32_t modeT0 = 0;
int nsx = -1, nsy = -1;
Notif nq[4]; int nqHead = 0, nqLen = 0;
Notif cur;

bool demoOn = false; uint32_t demoT0 = 0; int demoStep = 0;

// ===========================================================================
// Utilidades basicas
// ===========================================================================
static inline uint16_t rgb(int r, int g, int b) {
  if (r < 0) r = 0; if (r > 255) r = 255;
  if (g < 0) g = 0; if (g > 255) g = 255;
  if (b < 0) b = 0; if (b > 255) b = 255;
  return C565(r, g, b);
}

// mistura RGB565: a = 0 (so bg) .. 256 (so fg)
static inline uint16_t blend565(uint16_t bg, uint16_t fg, int a) {
  if (a <= 0) return bg;
  if (a >= 256) return fg;
  int br = bg >> 11, bgc = (bg >> 5) & 63, bb = bg & 31;
  int fr = fg >> 11, fgc = (fg >> 5) & 63, fb = fg & 31;
  int r = br + (((fr - br) * a) >> 8);
  int g = bgc + (((fgc - bgc) * a) >> 8);
  int b = bb + (((fb - bb) * a) >> 8);
  return (uint16_t)((r << 11) | (g << 5) | b);
}

static inline uint16_t scale565(uint16_t c, int f) {
  uint16_t r = ((c >> 11) * f) >> 8;
  uint16_t g = (((c >> 5) & 63) * f) >> 8;
  uint16_t b = ((c & 31) * f) >> 8;
  return (r << 11) | (g << 5) | b;
}

static inline void blendPx(int x, int y, uint16_t c, int a) {
  if ((unsigned)x >= W || (unsigned)y >= H || a <= 0) return;
  uint16_t* p = canvas.getBuffer() + y * W + x;
  *p = blend565(*p, c, a);
}

void fadeCanvas(int f) {
  uint16_t* b = canvas.getBuffer();
  for (int i = 0; i < W * H; i++) if (b[i]) b[i] = scale565(b[i], f);
}

static float clamp01(float x) { return x < 0 ? 0 : (x > 1 ? 1 : x); }
static float easeOut(float x) { x = clamp01(x); return 1 - (1 - x) * (1 - x) * (1 - x); }
static float easeIn(float x)  { x = clamp01(x); return x * x * x; }
static float easeIO(float x)  { x = clamp01(x); return x * x * (3 - 2 * x); }
static float lerpf(float a, float b, float t) { return a + (b - a) * t; }
int fadeFactor(float x) { return (int)(easeIO(x) * 256); }

static inline int clampi(int v, int lo, int hi) { return v < lo ? lo : (v > hi ? hi : v); }

static uint32_t isqrt32(uint32_t n) {
  uint32_t r = 0, b = 1u << 30;
  while (b > n) b >>= 2;
  while (b) {
    if (n >= r + b) { n -= r + b; r = (r >> 1) + b; } else r >>= 1;
    b >>= 2;
  }
  return r;
}

// Tabelas
static int16_t COS_T[360], SIN_T[360];          // x1024
static int16_t ATAN_T[257];                     // atan(i/256) em decimos de grau
static uint8_t HALFW[H];                        // meia largura do circulo da tela por linha
static uint16_t BG_LUT[226], BG_GRID[226];      // fundo radial (indice = d^2 >> 6)
struct Tick { int16_t x0, y0, x1, y1; uint8_t major; };
static Tick ticks[60];

static inline int normA(int a) { a %= 360; return a < 0 ? a + 360 : a; }

// angulo do pixel em decimos de grau: 0 = direita, cresce no sentido horario (y para baixo)
static int atan2d10(int dy, int dx) {
  int ax = dx < 0 ? -dx : dx, ay = dy < 0 ? -dy : dy;
  if (!ax && !ay) return 0;
  int base = (ax >= ay) ? ATAN_T[(ay << 8) / ax] : 900 - ATAN_T[(ax << 8) / ay];
  if (dx >= 0) return dy >= 0 ? base : (3600 - base) % 3600;
  return dy >= 0 ? 1800 - base : 1800 + base;
}

// ===========================================================================
// Primitivas com anti-aliasing (coordenadas em 1/16 px; centro do pixel = x*16+8)
// ===========================================================================

// Anel (circulo de espessura w16). a0/span em decimos de grau (span < 0 = circulo inteiro),
// fade = decimos de grau de esmaecimento nas pontas do arco.
static void aaRing(int cx16, int cy16, int r16, int w16, int a0, int span, int fade, uint16_t col, int alpha) {
  int ro = r16 + w16 / 2 + 12, ri = r16 - w16 / 2 - 12;
  int ylo = (cy16 - ro) >> 4, yhi = (cy16 + ro) >> 4;
  if (ylo < 0) ylo = 0; if (yhi > H - 1) yhi = H - 1;
  uint16_t* buf = canvas.getBuffer();
  for (int y = ylo; y <= yhi; y++) {
    int dy = y * 16 + 8 - cy16;
    int ady = dy < 0 ? -dy : dy;
    if (ady > ro) continue;
    int xo = (int)isqrt32((uint32_t)(ro * ro - dy * dy));
    int xi = (ri > 0 && ady < ri) ? (int)isqrt32((uint32_t)(ri * ri - dy * dy)) : -1;
    // faixas de pixels: [pa..la] a esquerda e [ra..pb] a direita (o miolo do anel e pulado)
    int pa = (cx16 - xo - 8) >> 4, pb = (cx16 + xo + 8) >> 4;
    int la = pb, ra = pb + 1;
    if (xi > 16) {
      la = (cx16 - xi + 24) >> 4;
      ra = (cx16 + xi - 24) >> 4;
      if (ra <= la) ra = la + 1;
    }
    for (int seg = 0; seg < 2; seg++) {
      int xs = seg ? ra : pa, xe = seg ? pb : la;
      if (xs < 0) xs = 0; if (xe > W - 1) xe = W - 1;
      for (int x = xs; x <= xe; x++) {
        int dx = x * 16 + 8 - cx16;
        int d = (int)isqrt32((uint32_t)(dx * dx + dy * dy));
        int cov = w16 / 2 + 8 - (d > r16 ? d - r16 : r16 - d);   // 1/16 de pixel coberto
        if (cov <= 0) continue;
        int a = cov >= 16 ? alpha : (alpha * cov) >> 4;
        if (span >= 0) {
          int rel = (atan2d10(dy, dx) - a0 + 3600) % 3600;
          if (rel > span) continue;
          int edge = rel < span - rel ? rel : span - rel;
          if (fade > 0 && edge < fade) a = a * edge / fade;
        }
        uint16_t* p = buf + y * W + x;
        *p = blend565(*p, col, a);
      }
    }
  }
}

// Disco cheio com borda suave
static void aaDisc(int cx16, int cy16, int r16, uint16_t col, int alpha) {
  int ro = r16 + 12;
  int ylo = (cy16 - ro) >> 4, yhi = (cy16 + ro) >> 4;
  if (ylo < 0) ylo = 0; if (yhi > H - 1) yhi = H - 1;
  uint16_t* buf = canvas.getBuffer();
  for (int y = ylo; y <= yhi; y++) {
    int dy = y * 16 + 8 - cy16;
    if (dy > ro || dy < -ro) continue;
    int xo = (int)isqrt32((uint32_t)(ro * ro - dy * dy));
    int inner = r16 - 24;
    int xin = (dy < inner && dy > -inner) ? (int)isqrt32((uint32_t)(inner * inner - dy * dy)) : 0;
    int pa = (cx16 - xo) >> 4, pb = (cx16 + xo) >> 4;
    if (pa < 0) pa = 0; if (pb > W - 1) pb = W - 1;
    for (int x = pa; x <= pb; x++) {
      int dx = x * 16 + 8 - cx16;
      int a = alpha;
      if (dx >= xin || dx <= -xin || xin == 0) {
        int d = (int)isqrt32((uint32_t)(dx * dx + dy * dy));
        int cov = r16 + 8 - d;
        if (cov <= 0) continue;
        if (cov < 16) a = (alpha * cov) >> 4;
      }
      uint16_t* p = buf + y * W + x;
      *p = (a >= 256) ? col : blend565(*p, col, a);
    }
  }
}

// Linha com espessura w16 e pontas arredondadas
static void aaLine(int x0, int y0, int x1, int y1, int w16, uint16_t col, int alpha) {
  int m = w16 / 2 + 16;
  int bx0 = ((x0 < x1 ? x0 : x1) - m) >> 4, bx1 = ((x0 > x1 ? x0 : x1) + m) >> 4;
  int by0 = ((y0 < y1 ? y0 : y1) - m) >> 4, by1 = ((y0 > y1 ? y0 : y1) + m) >> 4;
  bx0 = clampi(bx0, 0, W - 1); bx1 = clampi(bx1, 0, W - 1);
  by0 = clampi(by0, 0, H - 1); by1 = clampi(by1, 0, H - 1);
  int ex = x1 - x0, ey = y1 - y0;
  int32_t len2 = ex * ex + ey * ey;
  for (int y = by0; y <= by1; y++)
    for (int x = bx0; x <= bx1; x++) {
      int px = x * 16 + 8 - x0, py = y * 16 + 8 - y0;
      int32_t t = len2 ? ((int32_t)px * ex + (int32_t)py * ey) * 256 / len2 : 0;
      t = clampi(t, 0, 256);
      int qx = px - (ex * t >> 8), qy = py - (ey * t >> 8);
      int d = (int)isqrt32((uint32_t)(qx * qx + qy * qy));
      int cov = w16 / 2 + 8 - d;
      if (cov <= 0) continue;
      blendPx(x, y, col, cov >= 16 ? alpha : (alpha * cov) >> 4);
    }
}

// ---------------- texto (fontes alfa de assets.h) ----------------
static inline const AAGlyph* glyphOf(const AAFont& f, char ch) {
  uint8_t c = (uint8_t)ch;
  if (c < f.first || c > f.last) c = '?';
  if (c < f.first || c > f.last) return nullptr;
  return &f.g[c - f.first];
}

static void aaGlyph(const AAFont& f, const AAGlyph* g, int x, int y, uint16_t col, int alpha) {
  if (!g || !g->w) return;
  const uint8_t* b = f.bmp + g->off;
  uint16_t* buf = canvas.getBuffer();
  for (int j = 0; j < g->h; j++) {
    int Y = y + g->yo + j;
    if ((unsigned)Y >= H) continue;
    for (int i = 0; i < g->w; i++) {
      int a = pgm_read_byte(&b[j * g->w + i]);
      if (!a) continue;
      int X = x + g->xo + i;
      if ((unsigned)X >= W) continue;
      a = (a == 255) ? alpha : (a * alpha) >> 8;
      uint16_t* p = buf + Y * W + X;
      *p = blend565(*p, col, a);
    }
  }
}

static int aaW(const AAFont& f, const char* s, int track = 0, int n = -1) {
  int w = 0, i = 0;
  for (; s[i] && (n < 0 || i < n); i++) { const AAGlyph* g = glyphOf(f, s[i]); w += (g ? g->adv : 0) + track; }
  return i ? w - track : 0;
}

static void aaText(const AAFont& f, const char* s, int x, int y, uint16_t col, int alpha = 256, int track = 0) {
  for (int i = 0; s[i]; i++) {
    const AAGlyph* g = glyphOf(f, s[i]);
    aaGlyph(f, g, x, y, col, alpha);
    x += (g ? g->adv : 0) + track;
  }
}

static void aaTextC(const AAFont& f, const char* s, int cx, int y, uint16_t col, int alpha = 256, int track = 0) {
  aaText(f, s, cx - aaW(f, s, track) / 2, y, col, alpha, track);
}

// ---------------- icones ----------------
static void aaIcon(const AAIcon& ic, int x, int y, uint16_t tint, int alpha) {
  uint16_t* buf = canvas.getBuffer();
  for (int j = 0; j < ic.h; j++) {
    int Y = y + j;
    if ((unsigned)Y >= H) continue;
    for (int i = 0; i < ic.w; i++) {
      int X = x + i;
      if ((unsigned)X >= W) continue;
      int a = pgm_read_byte(&ic.a[j * ic.w + i]);
      if (!a) continue;
      a = (a * alpha) >> 8;
      uint16_t c = ic.rgb ? pgm_read_word(&ic.rgb[j * ic.w + i]) : tint;
      uint16_t* p = buf + Y * W + X;
      *p = blend565(*p, c, a);
    }
  }
}

// ===========================================================================
// Expressoes / comportamento do buddy
// ===========================================================================
void setExpr(int e, uint32_t ms, uint32_t now) { bd.expr = e; bd.exprUntil = now + ms; }
void lookAt(float x, float y, uint32_t ms, uint32_t now) { bd.tlx = x; bd.tly = y; bd.lookUntil = now + ms; }
void bounce(uint32_t ms, uint32_t now) { bd.bounceT0 = now; bd.bounceUntil = now + ms; }

int baseExpr() { return ui.synced ? EX_NORMAL : EX_SAD; }

void idleThink(uint32_t now, const tm& t) {
  if (bd.exprUntil && now > bd.exprUntil) { bd.expr = baseExpr(); bd.exprUntil = 0; }
  if (!bd.exprUntil) bd.expr = baseExpr();
  if (bd.lookUntil && now > bd.lookUntil) { bd.tlx = 0; bd.tly = 0; bd.lookUntil = 0; }

  if (now >= bd.blinkAt) { bd.blinkT0 = now; bd.blinkAt = now + 2200 + random(0, 4000); }

  if (bd.lastMin != t.tm_min) {
    if (bd.lastMin >= 0 && mode == M_CLOCK && an.type == AN_NONE) { setExpr(EX_SURPRISE, 800, now); lookAt(0, 1, 1200, now); bounce(600, now); }
    bd.lastMin = t.tm_min;
  }

  if (mode != M_CLOCK || an.type != AN_NONE || now < bd.nextIdle) return;
  bd.nextIdle = now + 5000 + random(0, 7000);
  if (bd.exprUntil) return;
  switch (random(0, 7)) {
    case 0: lookAt(-1, 0.3f, 1800, now); setExpr(EX_CURIOUS, 1800, now); break;
    case 1: lookAt(1, 0.3f, 1800, now); setExpr(ui.pst == 'R' ? EX_HAPPY : EX_CURIOUS, 1800, now); break;
    case 2: lookAt(0, 1, 1500, now); break;
    case 3: setExpr(EX_HAPPY, 1300, now); bounce(1000, now); break;
    case 4: lookAt(-0.6f, -0.9f, 1200, now); break;
    case 5: lookAt(0.8f, -0.5f, 1000, now); setExpr(EX_SURPRISE, 600, now); break;
    default: break;
  }
}

// ===========================================================================
// Corpo do buddy (metaballs, matematica inteira - o C3 nao tem FPU)
// ===========================================================================
struct Ball { int16_t x, y, r; int32_t q; };
#define BMAX 10
#define FT   256              // limiar do campo (Q8)
#define FK   13               // meia largura da rampa de anti-aliasing (unidades de campo)
static Ball balls[BMAX];
static int nBalls = 0, handFrom = 99;
#define GWMAX 70
#define GHMAX 84
static int16_t grid[GWMAX * GHMAX];              // ~12 KB

static void addBall(float x, float y, float r) {
  if (nBalls >= BMAX) return;
  Ball& b = balls[nBalls++];
  b.x = (int16_t)lroundf(x); b.y = (int16_t)lroundf(y);
  b.r = (int16_t)(r < 1 ? 1 : lroundf(r));
  b.q = (int32_t)b.r * b.r * 256;
}

static inline int32_t fieldAt(int x, int y, int from, int to) {
  int32_t f = 0;
  for (int i = from; i < to; i++) {
    int dx = x - balls[i].x, dy = y - balls[i].y;
    int32_t d2 = dx * dx + dy * dy;
    if (d2 < 1) d2 = 1;
    f += balls[i].q / d2;
  }
  return f;
}

static int headX16, headY16, bodyTop, bodyH;     // cabeca em 1/16 px (olhos com posicao sub-pixel)

static void buildBalls(float bx, float by, float s, float t, int hands, float lean = 0) {
  nBalls = 0;
  float br = sinf(t * 2.2f);
  float w1 = sinf(t * 1.7f), w2 = sinf(t * 1.3f + 1.1f), w3 = sinf(t * 2.6f + 2.0f);
  float sq = 1.0f + 0.025f * br;
  float hx = bx + 1.5f * w2 * s, hy = by - 30 * s * sq + br * 1.2f * s;
  addBall(hx,                  hy,                               24 * s);   // cabeca
  addBall(bx,                  by - 7 * s,                       20 * s);   // tronco
  addBall(bx - 2 * s + w1 * s, by + 15 * s,                      20 * s);   // barriga
  addBall(bx + 10 * s,         by + 32 * s + w1 * 1.5f * s,      14 * s);   // base direita
  addBall(bx - 14 * s,         by + 35 * s + w2 * 1.5f * s,      12 * s);   // base esquerda
  addBall(bx - 26 * s,         by + 49 * s + w3 * 2.5f * s,       7 * s);   // gota
  addBall(bx + 23 * s,         by + 51 * s + w1 * 2.5f * s,       6 * s);   // gota
  addBall(bx - 5 * s,          by + 52 * s + w2 * 2.0f * s,       6 * s);   // gota
  handFrom = nBalls;
  if (hands == 1) {                                // apoiadas em cima da hora
    addBall(bx - 41 * s, by - 13 * s + w2 * 1.0f * s, 9 * s);
    addBall(bx + 40 * s, by - 15 * s + w1 * 1.0f * s, 9 * s);
  } else if (hands == 2) {                         // segurando algo na frente da barriga
    addBall(bx - 15 * s, by + 14 * s + w2 * 0.8f * s, 8 * s);
    addBall(bx + 15 * s, by + 12 * s + w1 * 0.8f * s, 8 * s);
  }
  if (lean != 0) {                                 // inclina na direcao do voo (cabeca na frente, gotas atras)
    for (int i = 0; i < nBalls; i++) balls[i].x += (int16_t)lroundf((balls[i].y - by) * lean);
    hx += (hy - by) * lean;
  }
  headX16 = (int)lroundf(hx * 16);
  headY16 = (int)lroundf(hy * 16);
  bodyTop = (int)(hy - 30 * s);
  bodyH = (int)(120 * s);
}

static inline uint16_t bodyColor(int x, int y, int32_t f, float s) {
  int e = (int)((f - FT) * 256 / (FT / 2));
  e = clampi(e, 0, 256);
  int v = clampi((y - bodyTop) * 256 / (bodyH > 0 ? bodyH : 1), 0, 256);
  int r = 26 + ((7 - 26) * v >> 8), g = 46 + ((15 - 46) * v >> 8), b = 80 + ((32 - 80) * v >> 8);
  int rr = 72 + ((r - 72) * e >> 8), gg = 118 + ((g - 118) * e >> 8), bb = 170 + ((b - 170) * e >> 8);
  // brilho especular na cabeca (elipse com borda suave)
  int hx = (headX16 >> 4) - (int)(11 * s), hy = (headY16 >> 4) - (int)(13 * s);
  int hrx = (int)(8 * s) + 1, hry = (int)(5 * s) + 1;
  int dx = x - hx, dy = y - hy;
  int32_t q = (int32_t)dx * dx * hry * hry + (int32_t)dy * dy * hrx * hrx;
  int32_t R = (int32_t)hrx * hrx * hry * hry;
  if (q < R) {
    int a = (int)((int64_t)(R - q) * 170 / R);
    rr += (180 - rr) * a >> 8; gg += (225 - gg) * a >> 8; bb += (255 - bb) * a >> 8;
  }
  return rgb(rr, gg, bb);
}

// cobertura 0..256 na borda (rampa suave em volta do limiar)
static inline int edgeCov(int32_t f) {
  if (f >= FT + FK) return 256;
  if (f <= FT - FK) return 0;
  return (int)((f - (FT - FK)) * 256 / (2 * FK));
}

static inline void shadePixel(uint16_t* p, int x, int y, int32_t f, float s) {
  if (f > FT * 3 / 4 && f < FT + FK) {                       // brilho em volta da borda
    int g = f < FT ? f : FT;
    int a = (int)((g - FT * 3 / 4) * 40 / (FT / 4));
    *p = blend565(*p, C565(0, 70, 160), a);
  }
  int cov = edgeCov(f);
  if (!cov) return;
  *p = blend565(*p, bodyColor(x, y, f, s), (218 * cov) >> 8);
}

static void drawBody(float s) {
  if (!nBalls) return;
  int x0 = 9999, y0 = 9999, x1 = -9999, y1 = -9999;
  for (int i = 0; i < nBalls; i++) {
    int m = balls[i].r + balls[i].r / 2 + 2;
    if (balls[i].x - m < x0) x0 = balls[i].x - m;
    if (balls[i].x + m > x1) x1 = balls[i].x + m;
    if (balls[i].y - m < y0) y0 = balls[i].y - m;
    if (balls[i].y + m > y1) y1 = balls[i].y + m;
  }
  if (x0 < 0) x0 = 0; if (y0 < 0) y0 = 0;
  if (x1 > W - 1) x1 = W - 1; if (y1 > H - 1) y1 = H - 1;
  if (x0 > x1 || y0 > y1) return;
  int gw = (x1 - x0) / 2 + 2, gh = (y1 - y0) / 2 + 2;
  if (gw > GWMAX) { gw = GWMAX; x1 = x0 + (gw - 2) * 2; }
  if (gh > GHMAX) { gh = GHMAX; y1 = y0 + (gh - 2) * 2; }
  for (int j = 0; j < gh; j++)
    for (int i = 0; i < gw; i++) {
      int32_t f = fieldAt(x0 + i * 2, y0 + j * 2, 0, nBalls);
      grid[j * gw + i] = (int16_t)(f > 32767 ? 32767 : f);
    }

  uint16_t* buf = canvas.getBuffer();
  for (int y = y0; y <= y1; y++) {
    int gy = (y - y0) >> 1, fy = (y - y0) & 1;
    const int16_t* r0 = grid + gy * gw;
    const int16_t* r1 = r0 + gw;
    uint16_t* p = buf + y * W + x0;
    for (int x = x0; x <= x1; x++, p++) {
      int gx = (x - x0) >> 1, fx = (x - x0) & 1;
      int32_t f;
      if (!fx && !fy) f = r0[gx];
      else if (!fy)   f = (r0[gx] + r0[gx + 1]) >> 1;
      else if (!fx)   f = (r0[gx] + r1[gx]) >> 1;
      else            f = (r0[gx] + r0[gx + 1] + r1[gx] + r1[gx + 1]) >> 2;
      if (f < FT * 3 / 4) continue;
      shadePixel(p, x, y, f, s);
    }
  }
}

// So as maos, por cima dos digitos (parece que ele segura a hora)
static void drawHands(float s) {
  uint16_t* buf = canvas.getBuffer();
  for (int h = handFrom; h < nBalls; h++) {
    int m = balls[h].r * 2;
    int x0 = balls[h].x - m, x1 = balls[h].x + m, y0 = balls[h].y - m, y1 = balls[h].y + m;
    if (x0 < 0) x0 = 0; if (y0 < 0) y0 = 0; if (x1 > W - 1) x1 = W - 1; if (y1 > H - 1) y1 = H - 1;
    for (int y = y0; y <= y1; y++)
      for (int x = x0; x <= x1; x++) {
        int32_t fh = fieldAt(x, y, h, h + 1);
        if (fh < FT / 3) continue;
        int32_t f = fieldAt(x, y, 0, nBalls);
        int cov = edgeCov(f);
        if (!cov) continue;
        // so onde a mao domina o campo; transicao suave para o corpo
        int32_t fo = f - fh;
        int own = fh >= fo * 2 ? 256 : (fh <= fo ? 0 : (int)((fh - fo) * 256 / (fo ? fo : 1)));
        if (!own) continue;
        uint16_t* p = buf + y * W + x;
        *p = blend565(*p, bodyColor(x, y, f, s), (215 * cov >> 8) * own >> 8);
      }
  }
}

// ===========================================================================
// Olhos (anti-aliasing por distancia ate a borda da elipse)
// ===========================================================================
// cobertura 0..16 de uma elipse (centro 0, raios rx16/ry16) no ponto X,Y (1/16 px)
static inline int ellipseCov(int X, int Y, int rx16, int ry16) {
  int32_t u = (int32_t)X * 1024 / rx16, v = (int32_t)Y * 1024 / ry16;
  int32_t q = (u * u + v * v) >> 10;                 // 1024 na borda
  if (q > 1024 * 3) return 0;
  int32_t gx = u * 64 / rx16, gy = v * 64 / ry16;
  int32_t g = (int32_t)isqrt32((uint32_t)(gx * gx + gy * gy));
  if (g == 0) return 16;
  int32_t d16 = (1024 - q) * 32 / g;                  // distancia ate a borda em 1/16 px
  return clampi(d16 + 8, 0, 16);
}

static void eyeShape(int ex16, int ey16, int rx16, int ry16, int type, int side, uint16_t col, int alpha) {
  if (rx16 < 12) rx16 = 12; if (ry16 < 12) ry16 = 12;
  int sh = ry16 * 13 / 10;
  int bx0 = (ex16 - rx16 - 16) >> 4, bx1 = (ex16 + rx16 + 16) >> 4;
  int by0 = (ey16 - ry16 - 16) >> 4, by1 = (ey16 + ry16 + 16) >> 4;
  for (int y = by0; y <= by1; y++)
    for (int x = bx0; x <= bx1; x++) {
      int X = x * 16 + 8 - ex16, Y = y * 16 + 8 - ey16;
      int cov = ellipseCov(X, Y, rx16, ry16);
      if (!cov) continue;
      int inward = X * (-side);
      if (type == EX_HAPPY) {
        cov = cov * (16 - ellipseCov(X, Y - sh, rx16, ry16)) >> 4;          // "^"
      } else if (type == EX_ANGRY || type == EX_SAD) {
        int sgn = (type == EX_ANGRY) ? 1 : -1;
        int s16 = (Y * 10 + ry16 * 2 - inward * 11 * sgn) * 69 >> 10;        // distancia ate o corte
        cov = cov * clampi(s16 + 8, 0, 16) >> 4;
      }
      if (cov) blendPx(x, y, col, alpha * cov >> 4);
    }
}

static void drawEyes(float s, uint32_t now, int expr, int ea = 256) {
  float lx = bd.lx, ly = bd.ly;
  int baseRx = (int)(4.6f * 16 * s), baseRy = (int)(6.9f * 16 * s);
  int sep = (int)(8.5f * 16 * s);
  int ey = headY16 - (int)(1 * 16 * s) + (int)(ly * 3 * 16 * s);
  int exo = (int)(lx * 5 * 16 * s);

  float open = 1.0f;
  uint32_t bt = now - bd.blinkT0;
  if (bt < 180 && expr != EX_HAPPY) open = fabsf((float)bt - 90) / 90.0f;
  if (open < 0.12f) open = 0.12f;

  for (int side = -1; side <= 1; side += 2) {
    int rx = baseRx, ry = baseRy, oy = 0;
    switch (expr) {
      case EX_SURPRISE: rx = baseRx * 13 / 10; ry = baseRy * 11 / 10; break;
      case EX_SAD:      oy = (int)(2 * 16 * s); break;
      case EX_HAPPY:    oy = (int)(-1 * 16 * s); rx = baseRx * 6 / 5; break;
      case EX_CURIOUS:  if (side > 0) { rx = baseRx * 5 / 4; ry = baseRy * 5 / 4; } else ry = baseRy * 11 / 20; break;
      default: break;
    }
    ry = (int)(ry * open);
    int ex = headX16 + side * sep + exo, eyy = ey + oy;
    eyeShape(ex, eyy, rx + 52, ry + 52, expr, side, EYE_GLOW, 60 * ea >> 8);      // halo largo
    eyeShape(ex, eyy, rx + 22, ry + 22, expr, side, EYE_GLOW, 120 * ea >> 8);
    eyeShape(ex, eyy, rx, ry, expr, side, EYE_CORE, ea);                          // miolo
    if (expr != EX_HAPPY && expr != EX_ANGRY && ry > 48)                           // reflexo
      eyeShape(ex - rx / 3, eyy - ry / 3, rx / 3 + 6, ry / 4 + 6, EX_NORMAL, side, WHITE, 190 * ea >> 8);
  }
  if (expr == EX_SURPRISE) {
    int r = (int)(3.2f * 16 * s);
    aaRing(headX16 + exo, headY16 + (int)(13 * 16 * s), r, 20, 0, -1, 0, EYE_CORE, ea);
    aaRing(headX16 + exo, headY16 + (int)(13 * 16 * s), r, 52, 0, -1, 0, EYE_GLOW, 80 * ea >> 8);
  }
}

static void drawBuddyAt(float x, float y, float s, int expr, uint32_t now) {
  buildBalls(x, y, s, now / 1000.0f, 0);
  drawBody(s);
  drawEyes(s, now, expr);
}

// ===========================================================================
// Mostrador (layout medido na referencia: escala 0,506 da imagem de 512 px)
// ===========================================================================
static void drawBackground() {
  uint16_t* buf = canvas.getBuffer();
  for (int y = 0; y < H; y++) {
    uint16_t* row = buf + y * W;
    int hw = HALFW[y];
    int xa = CX - hw, xb = CX + hw;
    if (xa > 0) memset(row, 0, xa * 2);
    if (xb < W) memset(row + xb, 0, (W - xb) * 2);
    int dy = y - CY;
    bool gy = ((dy + 160) % 20) == 0;
    for (int x = xa; x < xb; x++) {
      int dx = x - CX;
      int idx = (dx * dx + dy * dy) >> 6;
      if (idx > 225) idx = 225;
      bool gx = ((dx + 160) % 20) == 0;
      row[x] = (gx || gy) ? BG_GRID[idx] : BG_LUT[idx];
    }
  }
}

static bool printActive() { return ui.pct >= 0 && (ui.pst == 'R' || ui.pst == 'P' || ui.pst == 'F' || ui.pst == 'X'); }

// Arcos da referencia: A comeca no topo e vai ate ~ +20 graus; B vai de ~ +40 a +90 (embaixo).
// Durante uma impressao o arco A mostra o progresso (com 68% fica igual a referencia).
static void drawRing() {
  const int R = 107 * 16;
  aaRing(C16, C16, R, 18, 0, -1, 0, RING_DIM, 256);                        // trilho fino
  int spanA = 1080;                                                        // 108 graus (decorativo)
  uint16_t ca = ARC_CORE;
  if (printActive()) {
    spanA = clampi(ui.pct, 2, 100) * 1600 / 100;
    if (ui.pst == 'F') ca = OK_GREEN;
    else if (ui.pst == 'P') ca = AMBER;
    else if (ui.pst == 'X') ca = ALERT;
  }
  const int A0 = 2720;                                                     // -88 graus
  aaRing(C16, C16, R, 80, A0, spanA, 60, ARC_GLOW, 70);                    // brilho
  aaRing(C16, C16, R, 36, A0, spanA, 40, ca, 256);                         // arco
  aaRing(C16, C16, R, 80, 400, 495, 60, ARC_GLOW, 70);
  aaRing(C16, C16, R, 36, 400, 495, 40, ARC_CORE, 256);
}

static void drawTicks() {
  for (int k = 0; k < 60; k++) {
    const Tick& t = ticks[k];
    aaLine(t.x0, t.y0, t.x1, t.y1, t.major ? 22 : 16, t.major ? TICK_MAJ : TICK_MIN, 256);
  }
}

// Hora: Saira Semi Condensed com numeros tabulares + brilho borrado por baixo
static const int TIME_BASE = 142;
static void timeStr(int hh, int mm, char* s) { snprintf(s, 6, "%02d:%02d", hh, mm); }

static void timeLayout(const char* s, int* xs) {
  int x = CX - aaW(F_TIME, s) / 2;
  for (int i = 0; i < 5; i++) { xs[i] = x; x += glyphOf(F_TIME, s[i])->adv; }
}

// centro do digito k (0..4) na tela
static void digitCenter(const tm& t, int k, int& cx, int& cy) {
  char s[6]; int xs[5];
  timeStr(t.tm_hour, t.tm_min, s); timeLayout(s, xs);
  const AAGlyph* g = glyphOf(F_TIME, s[k]);
  cx = xs[k] + g->xo + g->w / 2; cy = TIME_BASE + g->yo + g->h / 2;
}

static void drawTime(int hh, int mm) {
  char s[6]; int xs[5];
  timeStr(hh, mm, s); timeLayout(s, xs);
  for (int i = 0; i < 5; i++) {                    // brilho
    if (tmods.hide & (1 << i)) continue;
    aaGlyph(F_TIMEGLOW, glyphOf(F_TIMEGLOW, s[i]), xs[i] + tmods.dx[i], TIME_BASE + tmods.dy[i], TIME_GLOW, 120);
  }
  for (int i = 0; i < 5; i++) {                    // miolo (roubado = so um fantasma no lugar)
    bool gone = tmods.hide & (1 << i);
    aaGlyph(F_TIME, glyphOf(F_TIME, s[i]), xs[i] + (gone ? 0 : tmods.dx[i]), TIME_BASE + (gone ? 0 : tmods.dy[i]),
            TIME_CORE, gone ? 28 : 256);
  }
}

// digito solto (carregado pelo buddy), centralizado em cx,cy
static void drawFloatingDigit(char c, int cx, int cy) {
  const AAGlyph* g = glyphOf(F_TIME, c);
  int x = cx - (g->xo + g->w / 2), y = cy - (g->yo + g->h / 2);
  aaGlyph(F_TIMEGLOW, glyphOf(F_TIMEGLOW, c), x, y, TIME_GLOW, 150);
  aaGlyph(F_TIME, g, x, y, TIME_CORE, 256);
}

static void drawSideInfo() {
  char b[10];
  // ---- temperatura (esquerda) ----
  aaIcon(IC_THERMO, 39 - IC_THERMO.w / 2 + shL, 101 + shLy - (shL ? 1 : 0), LBL_COL, 230);
  if (ui.hasTemp) {
    snprintf(b, sizeof(b), "%d\x7F" "C", ui.temp);
    aaTextC(F_LBL, b, 41 + shL, 129 + shLy, LBL_COL);
  } else {
    aaTextC(F_LBL, "--", 41 + shL, 129 + shLy, LBL_COL, 150);
  }
  // ---- impressora (direita) ----
  bool act = printActive();
  aaIcon(IC_BAMBU, 200 - IC_BAMBU.w / 2 + shR, 106 + shRy - (shR ? 1 : 0), 0, act ? 256 : 110);
  uint16_t pc = LBL_COL;
  if (!act || ui.pct < 0) { strcpy(b, "--"); }
  else if (ui.pst == 'X') { strcpy(b, "ERRO"); pc = ALERT; }
  else if (ui.pct >= 100 || ui.pst == 'F') { strcpy(b, "100%"); pc = OK_GREEN; }
  else { snprintf(b, sizeof(b), "%d%%", ui.pct); if (ui.pst == 'P') pc = AMBER; }
  aaTextC(F_SML, b, 200 + shR, 137 + shRy, pc, act ? 256 : 150);
}

static void drawClockBase(bool withTime, const tm& t) {
  drawBackground();
  drawRing();
  drawTicks();
  drawSideInfo();
  if (!ui.synced) aaTextC(F_SML, "SEM SINC", CX, 204, ALERT, 230, 1);
  if (withTime) drawTime(t.tm_hour, t.tm_min);
}

// ===========================================================================
// Cartao de notificacao
// ===========================================================================
static uint16_t appColor(char a) { return a == 'W' ? C565(37, 211, 102) : a == 'I' ? C565(225, 48, 140) : C565(0, 150, 255); }
static const char* appName(char a) { return a == 'W' ? "WHATSAPP" : a == 'I' ? "INSTAGRAM" : "NOTIFICACAO"; }

static int wrapText(const char* s, const AAFont& f, int maxW, char lines[][48], int maxLines) {
  int n = 0; const char* p = s;
  while (*p && n < maxLines) {
    while (*p == ' ') p++;
    if (!*p) break;
    int len = 0, lastSpace = -1;
    while (p[len] && p[len] != '\n') {
      if (len >= 46 || aaW(f, p, 0, len + 1) > maxW) break;
      if (p[len] == ' ') lastSpace = len;
      len++;
    }
    bool more = p[len] && p[len] != '\n';
    if (more && lastSpace > 0) len = lastSpace;
    if (len == 0) len = 1;
    memcpy(lines[n], p, len); lines[n][len] = 0;
    p += len;
    if (*p == '\n') p++;
    n++;
  }
  if (*p && n == maxLines) {
    char* L = lines[n - 1]; int l = strlen(L);
    while (l > 0 && aaW(f, L) + aaW(f, "...") > maxW) L[--l] = 0;
    while (l > 0 && L[l - 1] == ' ') L[--l] = 0;
    strcat(L, "...");
  }
  return n;
}

static void fitText(char* s, const AAFont& f, int maxW) {
  int l = strlen(s);
  if (aaW(f, s) <= maxW) return;
  while (l > 0 && aaW(f, s) + aaW(f, "...") > maxW) s[--l] = 0;
  strcat(s, "...");
}

static void drawCard(int ox, int oy, const Notif& n, float timeLeft) {
  int cx = CX + ox, cy = CY + oy;
  int cx16 = C16 + ox * 16, cy16 = C16 + oy * 16;
  uint16_t ac = appColor(n.app);
  aaDisc(cx16, cy16, 121 * 16, CARD_BG, 256);
  aaRing(cx16, cy16, 117 * 16, 20, 0, -1, 0, scale565(ac, 110), 256);
  if (timeLeft >= 0) {
    int span = (int)(timeLeft * 3600);
    if (span > 0) {
      aaRing(cx16, cy16, 113 * 16, 70, 2700, span, 0, ac, 70);
      aaRing(cx16, cy16, 113 * 16, 32, 2700, span, 0, ac, 256);
    }
  }
  // icone: circulo na cor do app + balao generico
  aaDisc((cx - 0) * 16 + 8, (cy - 66) * 16 + 8, 21 * 16, scale565(ac, 60), 256);
  aaDisc((cx - 0) * 16 + 8, (cy - 66) * 16 + 8, 18 * 16, ac, 256);
  aaIcon(IC_BUBBLE, cx - IC_BUBBLE.w / 2, cy - 66 - IC_BUBBLE.h / 2 + 1, WHITE, 256);
  aaTextC(F_SML, appName(n.app), cx, cy - 32, ac, 256, 2);

  char title[40]; strncpy(title, n.title, 35); title[35] = 0;
  fitText(title, F_TTL, 190);
  aaTextC(F_TTL, title, cx, cy - 6, WHITE);
  aaLine((cx - 40) * 16, (cy + 5) * 16, (cx + 40) * 16, (cy + 5) * 16, 16, scale565(ac, 140), 256);

  char lines[3][48];
  const int maxW[3] = {192, 182, 150};
  int nl = wrapText(n.text, F_LBL, 182, lines, 3);
  for (int i = 0; i < nl; i++) {
    fitText(lines[i], F_LBL, maxW[i]);
    aaTextC(F_LBL, lines[i], cx, cy + 30 + i * 21, C565(200, 220, 238));
  }
}

// ===========================================================================
// Fila e maquina de estados da notificacao
// ===========================================================================
static void startNotif(uint32_t now) {
  cur = nq[nqHead]; nqHead = (nqHead + 1) % 4; nqLen--;
  int c = random(0, 4);
  nsx = (c == 1 || c == 2) ? 1 : -1;
  nsy = (c >= 2) ? 1 : -1;
  mode = M_GO; modeT0 = now;
}

void uiNotify(char app, const char* title, const char* text) {
  if (nqLen == 4) { nqHead = (nqHead + 1) % 4; nqLen--; }
  Notif& n = nq[(nqHead + nqLen) % 4];
  n.app = app;
  strncpy(n.title, title, sizeof(n.title) - 1); n.title[sizeof(n.title) - 1] = 0;
  strncpy(n.text, text, sizeof(n.text) - 1);    n.text[sizeof(n.text) - 1] = 0;
  nqLen++;
}

static const float RIM = 84;
static const float D = 250;

// ===========================================================================
// Eventos vindos do celular
// ===========================================================================
void uiSetSynced(bool s) {
  uint32_t now = millis();
  if (s && !ui.synced) { setExpr(EX_HAPPY, 2000, now); bounce(800, now); }
  ui.synced = s;
}

void uiSetTemp(int c) {
  uint32_t now = millis();
  if (!ui.hasTemp || c != ui.temp) {
    lookAt(-1, 0.3f, 2000, now);
    setExpr(c >= 32 ? EX_SURPRISE : EX_CURIOUS, 2000, now);
  }
  ui.hasTemp = true; ui.temp = c;
}
void uiClearTemp() { ui.hasTemp = false; }

void uiSetPrint(int pct, char st) {
  uint32_t now = millis();
  if (pct > 100) pct = 100;
  char old = ui.pst; int oldPct = ui.pct;
  ui.pct = pct; ui.pst = st;
  if (st != old) {
    if (st == 'F') { setExpr(EX_HAPPY, 6000, now); bounce(2500, now); lookAt(1, 0.3f, 2500, now); }
    else if (st == 'X') { setExpr(EX_ANGRY, 3000, now); lookAt(1, 0.3f, 3000, now); }
    else if (st == 'P') { setExpr(EX_CURIOUS, 3000, now); lookAt(1, 0.3f, 3000, now); }
    else if (st == 'R') { setExpr(EX_HAPPY, 1500, now); lookAt(1, 0.3f, 1500, now); }
  } else if (pct != oldPct && st == 'R' && mode == M_CLOCK && !bd.exprUntil) {
    lookAt(1, 0.3f, 1200, now);
  }
}

bool uiExprByName(const char* n, uint32_t ms) {
  static const char* const names[][2] = {
    {"normal", "normal"}, {"feliz", "happy"}, {"surpresa", "surprise"},
    {"raiva", "angry"}, {"triste", "sad"}, {"curioso", "curious"}};
  for (int i = 0; i < 6; i++)
    if (!strcasecmp(n, names[i][0]) || !strcasecmp(n, names[i][1])) { setExpr(i, ms, millis()); return true; }
  return false;
}

void uiDemo() { demoOn = true; demoT0 = millis(); demoStep = 0; }

static void demoTick(uint32_t now) {
  if (!demoOn) return;
  static const uint32_t at[] = {0, 2500, 5000, 7500, 10000, 12500, 15000, 16000, 26000, 30000, 33000};
  while (demoStep < 11 && now - demoT0 >= at[demoStep]) {
    switch (demoStep) {
      case 0: setExpr(EX_HAPPY, 2400, now); bounce(1000, now); break;
      case 1: setExpr(EX_SURPRISE, 2400, now); break;
      case 2: setExpr(EX_ANGRY, 2400, now); break;
      case 3: setExpr(EX_SAD, 2400, now); break;
      case 4: setExpr(EX_CURIOUS, 2400, now); lookAt(1, 0.2f, 2400, now); break;
      case 5: uiSetTemp(ui.hasTemp ? ui.temp + 1 : 25); break;
      case 6: uiSetPrint(ui.pct < 0 ? 68 : (ui.pct + 7) % 100, 'R'); break;
      case 7: uiNotify('W', "Fulano", "Chego em 10 minutos, me espera na frente da FIAP!"); break;
      case 8: uiNotify('I', "mynd.store", "curtiu sua foto e comecou a seguir voce"); break;
      case 9: uiSetPrint(100, 'F'); break;
      case 10: demoOn = false; break;
    }
    demoStep++;
  }
}

// ===========================================================================
// Travessuras do buddy (so no modo relogio)
//   AN_FLY    voa pela tela fazendo um "8" e volta
//   AN_HIDE   mergulha para fora da tela, aparecem so os olhos em 2 esconderijos, volta de baixo
//   AN_STEAL  rouba um digito da hora, foge com ele e depois devolve no lugar
//   AN_ICONS  cutuca o termometro e a logo da Bambu
//   AN_SHAKE  chacoalha os numeros da hora
//   AN_LOOP   da uma volta no anel e volta
//   AN_RAGE   raivoso: da cabecada em cada elemento, tudo cai e se bagunca, depois volta
// Cada uma e uma "coreografia" em funcao do tempo: anticPose(e) devolve a pose naquele instante.
// ===========================================================================
struct Pose {
  float x, y, s;
  int hands;          // 0 nenhuma, 1 em cima da hora, 2 segurando na frente
  bool front;         // desenhado na frente da hora
  bool body, eyes;
  int expr;           // -1 = expressao do momento
  bool look; float lx, ly;
  int eyeA;           // alfa dos olhos
  bool carry; char carryC;
};

static const float HOME_X = 120, HOME_Y = 110, HOME_S = 1.1f;

static Pose homePose(float hop) {
  Pose p;
  memset(&p, 0, sizeof(p));
  p.x = HOME_X; p.y = HOME_Y + hop; p.s = HOME_S;
  p.hands = 1; p.body = true; p.eyes = true; p.expr = -1; p.eyeA = 256;
  return p;
}

static float seg(uint32_t e, uint32_t a, uint32_t b) { return e <= a ? 0.0f : e >= b ? 1.0f : (float)(e - a) / (float)(b - a); }
static float easeOutBack(float x) { x = clamp01(x); const float c1 = 1.70158f, c3 = c1 + 1; float u = x - 1; return 1 + c3 * u * u * u + c1 * u * u; }
static void place(Pose& p, float x0, float y0, float s0, float x1, float y1, float s1, float k) {
  p.x = lerpf(x0, x1, k); p.y = lerpf(y0, y1, k); p.s = lerpf(s0, s1, k);
}
static void lookTo(Pose& p, float lx, float ly) { p.look = true; p.lx = lx; p.ly = ly; }

// esconderijos para "so os olhos"
static const int16_t PEEK[4][2] = {{34, 176}, {206, 178}, {120, 90}, {120, 44}};

static void anticStart(uint32_t now) {
  // "Saco embaralhado": cada rodada e uma ordem aleatoria das 7 travessuras, sem repetir.
  // Assim, em quaisquer 7 seguidas nenhuma aparece mais de 2 vezes, e em 13 seguidas
  // (bem abaixo do limite de 14) todas ja apareceram. A primeira de uma rodada nunca
  // repete a ultima da rodada anterior.
  const int N_ANTIC = AN_RAGE - AN_FLY + 1;            // 7
  static uint8_t order[N_ANTIC];
  static int pos = N_ANTIC;
  if (pos >= N_ANTIC) {
    for (int i = 0; i < N_ANTIC; i++) order[i] = AN_FLY + i;
    for (int i = N_ANTIC - 1; i > 0; i--) { int j = random(0, i + 1); uint8_t tmp = order[i]; order[i] = order[j]; order[j] = tmp; }
    if (order[0] == an.last) { int j = random(1, N_ANTIC); uint8_t tmp = order[0]; order[0] = order[j]; order[j] = tmp; }
    pos = 0;
  }
  int ty = order[pos++];
  an.type = ty; an.last = ty; an.t0 = now;
  an.ph = random(0, 628) / 100.0f;
  switch (ty) {
    case AN_FLY:   an.dur = 6500; break;
    case AN_HIDE:  an.dur = 7800;
                   an.pk[0] = random(0, 4);
                   do an.pk[1] = random(0, 4); while (an.pk[1] == an.pk[0]);
                   break;
    case AN_STEAL: { static const int8_t ks[] = {0, 1, 3, 4}; an.k = ks[random(0, 4)]; an.dur = 7400; } break;
    case AN_ICONS: an.dur = 4800; break;
    case AN_SHAKE: an.dur = 1800; break;
    case AN_RAGE:  an.dur = 8800;
                   for (int i = 0; i < 7; i++) { an.ox[i] = an.oy[i] = an.vx[i] = an.vy[i] = 0; an.hit[i] = an.rest[i] = false; }
                   an.snap = false; an.lastE = 0;
                   break;
    default:       an.dur = 4800; break;    // AN_LOOP
  }
}

// Pose da travessura no instante e (ms desde o inicio). fx = aplica efeitos (digitos, icones).
static void anticPose(uint32_t e, const tm& t, Pose& p, bool fx) {
  const float u = e / 1000.0f;
  switch (an.type) {

    case AN_FLY: {
      float a = easeIO(seg(e, 0, 800)) * (1 - easeIO(seg(e, an.dur - 900, an.dur)));
      float px = 120 + 62 * sinf(1.25f * u + an.ph), py = 112 + 50 * sinf(2.1f * u + an.ph * 0.7f);
      place(p, HOME_X, HOME_Y, HOME_S, px, py, 0.7f, a);
      p.front = a > 0.2f; p.hands = a < 0.05f ? 1 : 0;
      p.expr = (e < an.dur / 2) ? EX_HAPPY : EX_CURIOUS;
    } break;

    case AN_HIDE: {
      p.hands = 0;
      if (e < 700) {                                    // mergulho para baixo
        float k = easeIn(seg(e, 0, 700));
        place(p, HOME_X, HOME_Y, HOME_S, 120, 300, 0.95f, k);
        p.front = true; p.expr = EX_SURPRISE;
      } else if (e < 1900 || (e >= 3900 && e < 4300) || (e >= 6100 && e < 6400)) {
        p.body = false; p.eyes = false;                 // sumiu!
      } else if (e < 6100) {                            // so os olhos, no escuro
        bool first = e < 3900;
        uint32_t a0 = first ? 1900 : 4300, a1 = first ? 3900 : 6100;
        const int16_t* sp = PEEK[an.pk[first ? 0 : 1]];
        p.body = false; p.eyes = true; p.x = sp[0]; p.y = sp[1]; p.s = 1.15f;
        float fade = fminf(seg(e, a0, a0 + 300), 1 - seg(e, a1 - 300, a1));
        p.eyeA = (int)(fade * 256);
        float tx = (120 - sp[0]) / 90.0f, ty = (120 - sp[1]) / 90.0f;
        uint32_t mid = (a0 + a1) / 2;
        if (e > mid - 300 && e < mid + 200) lookTo(p, -tx, ty * 0.3f);      // desconfia e olha pro outro lado
        else lookTo(p, fmaxf(-1, fminf(1, tx)), fmaxf(-1, fminf(1, ty)));
        p.expr = first ? (e < mid ? EX_CURIOUS : EX_NORMAL) : EX_HAPPY;
      } else {                                          // volta de baixo com um pulo
        float k = seg(e, 6400, an.dur);
        p.x = 120; p.y = lerpf(290, HOME_Y, easeOutBack(k)); p.s = HOME_S;
        p.front = false; p.hands = k > 0.85f ? 1 : 0; p.expr = EX_HAPPY;
      }
    } break;

    case AN_STEAL: {
      int dcx, dcy; digitCenter(t, an.k, dcx, dcy);
      char ts[6]; timeStr(t.tm_hour, t.tm_min, ts);
      const float S = 0.72f, co = 22 * S;
      float gx = dcx, gy = dcy - co;                    // onde o buddy fica para pegar o digito
      float hx = dcx, hy = gy - 24;                     // pairando acima
      bool stolen = e >= 1300 && e < 5600;
      p.front = true; p.hands = 2; p.expr = EX_HAPPY;
      if (e < 900) {
        place(p, HOME_X, HOME_Y, HOME_S, hx, hy, S, easeIO(seg(e, 0, 900)));
        p.front = e > 150; p.hands = e < 150 ? 1 : 0; p.expr = EX_CURIOUS; lookTo(p, 0, 1);
      } else if (e < 1300) {
        place(p, hx, hy, S, gx, gy, S, easeIO(seg(e, 900, 1300)));
        p.expr = EX_CURIOUS; lookTo(p, 0, 1);
      } else if (e < 1700) {
        place(p, gx, gy, S, hx, hy, S, easeIO(seg(e, 1300, 1700)));
      } else if (e < 4800) {                            // foge com o digito
        float a = easeIO(seg(e, 1700, 2300)) * (1 - easeIO(seg(e, 4200, 4800)));
        float v = (e - 1700) / 1000.0f;
        float px = 120 + 60 * sinf(1.7f * v + an.ph), py = 104 + 32 * sinf(3.1f * v + 1.0f);
        place(p, hx, hy, S, px, py, S, a);
        if (e > 3300 && e < 3700) p.expr = EX_SURPRISE;           // "quase me pegam!"
      } else if (e < 5200) {
        p.x = hx; p.y = hy + sinf(u * 9) * 1.5f; p.s = S; lookTo(p, 0, 1);
      } else if (e < 5600) {                            // encaixa de volta
        place(p, hx, hy, S, gx, gy, S, easeIO(seg(e, 5200, 5600)));
        lookTo(p, 0, 1);
      } else if (e < 6000) {
        place(p, gx, gy, S, hx, hy, S, easeIO(seg(e, 5600, 6000)));
      } else {                                          // volta para casa
        float k = easeIO(seg(e, 6000, an.dur));
        place(p, hx, hy, S, HOME_X, HOME_Y, HOME_S, k);
        p.front = k < 0.85f; p.hands = k > 0.85f ? 1 : 0;
      }
      if (stolen) { p.carry = true; p.carryC = ts[an.k]; if (fx) tmods.hide |= 1 << an.k; }
    } break;

    case AN_ICONS: {
      const float LX = 64, LY = 78, RX = 176, RY = 78, S = 0.6f;
      p.front = true; p.hands = 0;
      if (e < 900) {
        place(p, HOME_X, HOME_Y, HOME_S, LX, LY, S, easeIO(seg(e, 0, 900)));
        p.front = e > 150; p.expr = EX_CURIOUS; lookTo(p, -1, 0.7f);
      } else if (e < 1800) {
        p.x = LX - 3 * fabsf(sinf((e - 900) * 0.012f)); p.y = LY; p.s = S; lookTo(p, -1, 0.7f);
        p.expr = e < 1200 ? EX_SURPRISE : EX_HAPPY;
        if (fx && e < 1500) shL = (int8_t)lroundf(sinf(e * 0.09f) * 2);
      } else if (e < 2900) {
        float k = easeIO(seg(e, 1800, 2900));
        place(p, LX, LY, S, RX, RY, S, k);
        p.y -= sinf(k * 3.14159f) * 26;                 // pula por cima da hora
        p.expr = EX_HAPPY;
      } else if (e < 3800) {
        p.x = RX + 3 * fabsf(sinf((e - 2900) * 0.012f)); p.y = RY; p.s = S; lookTo(p, 1, 0.7f);
        p.expr = e < 3300 ? EX_CURIOUS : EX_HAPPY;
        if (fx && e < 3400) shR = (int8_t)lroundf(sinf(e * 0.09f) * 2);
      } else {
        float k = easeIO(seg(e, 3800, an.dur));
        place(p, RX, RY, S, HOME_X, HOME_Y, HOME_S, k);
        p.front = k < 0.85f; p.hands = k > 0.85f ? 1 : 0; p.expr = EX_HAPPY;
      }
    } break;

    case AN_SHAKE: {                                    // chacoalha a hora (fica em casa)
      float env = sinf(seg(e, 0, an.dur) * 3.14159f);
      p.y = HOME_Y - fabsf(sinf(e * 0.025f)) * 4 * env;
      p.expr = EX_HAPPY;
      if (fx) for (int i = 0; i < 5; i++) {
        tmods.dx[i] = (int8_t)lroundf(sinf(e * 0.045f + i * 1.7f) * 2.5f * env);
        tmods.dy[i] = (int8_t)lroundf(cosf(e * 0.061f + i * 2.3f) * 2.5f * env);
      }
    } break;

    case AN_RAGE: {                                     // raivoso: cabecada em tudo, as coisas caem
      // posicao original de cada elemento (centro) e meia altura
      float bx[7], by[7], hh[7];
      bx[0] = 40; by[0] = 115; hh[0] = 15;
      bx[6] = 200; by[6] = 120; hh[6] = 16;
      for (int i = 0; i < 5; i++) { int cx, cy; digitCenter(t, i, cx, cy); bx[i + 1] = cx; by[i + 1] = cy; hh[i + 1] = (i == 2) ? 12 : 21; }
      const uint32_t T0 = 600, STEP = 640, HITAT = 380;          // alvo i: chega em T0+i*STEP, bate em +HITAT
      const uint32_t RESTORE = 6200, RESTORE_END = 7500;
      const float S = 0.62f;

      // ---- fisica dos elementos (so na chamada que aplica efeitos) ----
      if (fx) {
        float dt = (e - an.lastE) / 1000.0f;
        if (dt > 0.05f) dt = 0.05f;
        an.lastE = e;
        for (int i = 0; i < 7; i++) {
          if (!an.hit[i] && e >= T0 + i * STEP + HITAT) {        // levou a cabecada
            an.hit[i] = true;
            // cada um voa para um lado (espalha a bagunca pelo fundo)
            if (i == 0)      an.vx[i] = -(30 + random(0, 40));
            else if (i == 6) an.vx[i] = -(10 + random(0, 50));
            else             an.vx[i] = (i - 3) * 38 + random(-15, 16);
            an.vy[i] = -(120 + random(0, 90));
          }
          if (an.hit[i] && !an.rest[i] && e < RESTORE) {
            an.vy[i] += 650 * dt;                                // gravidade
            an.ox[i] += an.vx[i] * dt; an.oy[i] += an.vy[i] * dt;
            float ax = bx[i] + an.ox[i];
            if (ax < 34)  { an.ox[i] = 34 - bx[i];  an.vx[i] = fabsf(an.vx[i]) * 0.6f; }
            if (ax > 206) { an.ox[i] = 206 - bx[i]; an.vx[i] = -fabsf(an.vx[i]) * 0.6f; }
            ax = bx[i] + an.ox[i];
            float dx = ax - 120, lim = 104 * 104 - dx * dx;
            float floorY = 120 + (lim > 0 ? sqrtf(lim) : 0) - hh[i] - 4;   // "chao" = borda redonda
            if (by[i] + an.oy[i] > floorY) {
              an.oy[i] = floorY - by[i];
              if (fabsf(an.vy[i]) < 45) { an.vy[i] = 0; an.vx[i] *= 0.5f; if (fabsf(an.vx[i]) < 8) an.rest[i] = true; }
              else { an.vy[i] = -an.vy[i] * 0.42f; an.vx[i] *= 0.7f; }
              // escorrega pela curva do fundo
              an.vx[i] += (dx > 0 ? -1 : 1) * 30 * dt;
            }
          }
        }
        float k = 0;
        if (e >= RESTORE) {                                       // tudo volta pro lugar
          if (!an.snap) { an.snap = true; for (int i = 0; i < 7; i++) { an.sx[i] = an.ox[i]; an.sy[i] = an.oy[i]; } }
          k = easeOutBack(seg(e, RESTORE, RESTORE_END));
          for (int i = 0; i < 7; i++) { an.ox[i] = an.sx[i] * (1 - k); an.oy[i] = an.sy[i] * (1 - k); }
        }
        shL = (int8_t)fmaxf(-120, fminf(120, an.ox[0])); shLy = (int8_t)fmaxf(-120, fminf(120, an.oy[0]));
        shR = (int8_t)fmaxf(-120, fminf(120, an.ox[6])); shRy = (int8_t)fmaxf(-120, fminf(120, an.oy[6]));
        for (int i = 0; i < 5; i++) {
          tmods.dx[i] = (int8_t)fmaxf(-120, fminf(120, an.ox[i + 1]));
          tmods.dy[i] = (int8_t)fmaxf(-120, fminf(120, an.oy[i + 1]));
        }
      }

      // ---- trajetoria do buddy ----
      p.front = true; p.hands = 0; p.expr = EX_ANGRY;
      auto pre = [&](int i, float& x, float& y) {             // ponto de preparo (antes da cabecada)
        float dir = (i == 0) ? -1.0f : 1.0f;
        x = bx[i] - dir * 30; y = by[i] - hh[i] - 30;
      };
      if (e < T0) {
        float x, y; pre(0, x, y);
        place(p, HOME_X, HOME_Y, HOME_S, x, y, S, easeIO(seg(e, 0, T0)));
        p.front = e > 150; p.hands = e < 150 ? 1 : 0;
        p.x += sinf(e * 0.08f) * 1.5f;                            // tremendo de raiva
      } else if (e < T0 + 7 * STEP) {
        int i = (e - T0) / STEP;
        uint32_t le = (e - T0) % STEP;
        float dir = (i == 0) ? -1.0f : 1.0f;
        float px0, py0; pre(i, px0, py0);
        float cx = bx[i] - dir * 9, cy = by[i] - hh[i] - 10;       // contato: a cabeca acerta o elemento
        if (le < 300) {                                            // vai ate o proximo alvo
          float sx0 = px0, sy0 = py0;                              // partida = fim do recuo anterior
          if (i > 0) { float qx, qy; pre(i - 1, qx, qy); float d0 = (i - 1 == 0) ? -1.0f : 1.0f; sx0 = qx + d0 * 10; sy0 = qy - 6; }
          place(p, sx0, sy0, S, px0, py0, S, easeIO(seg(le, 0, 300)));
          p.x += sinf(e * 0.09f) * 1.2f;
        } else if (le < HITAT) {
          place(p, px0, py0, S, cx, cy, S, easeIn(seg(le, 300, HITAT)));   // investida
        } else {
          place(p, cx, cy, S, px0 + dir * 10, py0 - 6, S, easeOut(seg(le, HITAT, STEP)));   // recua
        }
      } else if (e < RESTORE) {                                  // bufando no meio da tela
        p.x = 120 + sinf(e * 0.07f) * 3; p.y = 62 - fabsf(sinf(e * 0.02f)) * 5; p.s = 0.7f;
        lookTo(p, 0, 1);
      } else if (e < RESTORE_END) {                              // ve tudo voltar...
        p.x = 120; p.y = 62; p.s = 0.7f; p.expr = EX_SURPRISE; lookTo(p, 0, 1);
      } else {                                                   // ...e volta pra casa como se nada fosse
        float k = easeIO(seg(e, RESTORE_END, an.dur));
        place(p, 120, 62, 0.7f, HOME_X, HOME_Y, HOME_S, k);
        p.front = k < 0.85f; p.hands = k > 0.85f ? 1 : 0; p.expr = EX_HAPPY;
      }
    } break;
    case AN_LOOP: {                                     // volta no anel
      float a = easeIO(seg(e, 0, 700)) * (1 - easeIO(seg(e, an.dur - 800, an.dur)));
      float th = -1.5708f + 6.2832f * easeIO(seg(e, 300, an.dur - 300));
      place(p, HOME_X, HOME_Y, HOME_S, 120 + 74 * cosf(th), 120 + 74 * sinf(th), 0.55f, a);
      p.front = a > 0.2f; p.hands = a < 0.05f ? 1 : 0; p.expr = EX_HAPPY;
    } break;
  }
}

// Desenha o modo relogio com o buddy na pose da travessura atual
static void drawClockScene(uint32_t now, const tm& t, float hop) {
  float tt = now / 1000.0f;
  memset(&tmods, 0, sizeof(tmods)); shL = shR = shLy = shRy = 0;
  Pose p = homePose(hop), q = p;
  if (an.type == AN_NONE && now >= an.nextAt && !bd.exprUntil) anticStart(now);
  if (an.type != AN_NONE) {
    uint32_t ea = now - an.t0;
    if (ea >= an.dur) {
      an.type = AN_NONE;
      an.nextAt = now + 5000 + random(0, 10001);       // proxima travessura: 5 a 15 s
      bd.nextIdle = now + 2500;
    } else {
      q = homePose(0);
      anticPose(ea > 40 ? ea - 40 : 0, t, q, false);   // pose 40 ms antes -> velocidade
      anticPose(ea, t, p, true);
    }
  }
  float vx = (p.x - q.x) / 0.04f, vy = (p.y - q.y) / 0.04f;
  float lean = 0;
  if (p.body && p.front) lean = fmaxf(-0.3f, fminf(0.3f, -vx / 700.0f));
  if (p.look) { bd.tlx = p.lx; bd.tly = p.ly; }
  else if (an.type != AN_NONE && p.front && (fabsf(vx) > 20 || fabsf(vy) > 20)) {
    bd.tlx = fmaxf(-1, fminf(1, vx / 150)); bd.tly = fmaxf(-1, fminf(1, vy / 150));
  } else if (an.type != AN_NONE && !bd.lookUntil) { bd.tlx = 0; bd.tly = 0; }
  int ex = p.expr >= 0 ? p.expr : bd.expr;

  drawClockBase(false, t);
  if (p.body) buildBalls(p.x, p.y, p.s, tt, p.hands, lean);
  if (p.body && !p.front) drawBody(p.s);
  drawTime(t.tm_hour, t.tm_min);
  if (p.body && !p.front && p.hands == 1) drawHands(p.s);
  if (p.body && p.front) drawBody(p.s);
  if (p.carry) drawFloatingDigit(p.carryC, (int)lroundf(p.x), (int)lroundf(p.y + 22 * p.s));
  if (p.body && p.front && p.hands == 2) drawHands(p.s);
  if (p.eyes && p.eyeA > 0) {
    if (!p.body) { headX16 = (int)lroundf(p.x * 16); headY16 = (int)lroundf((p.y + p.s) * 16); }
    drawEyes(p.s, now, ex, p.eyeA);
  }
  if (p.body) { curX = p.x; curY = p.y; curS = p.s; }
  else        { curX = 120; curY = 280; curS = 0.9f; }      // escondido: a notificacao vem de baixo
}

// ===========================================================================
// Inicializacao e quadro
// ===========================================================================
void uiInit() {
  for (int i = 0; i < 360; i++) {
    COS_T[i] = (int16_t)lroundf(cosf(i * 0.017453292f) * 1024);
    SIN_T[i] = (int16_t)lroundf(sinf(i * 0.017453292f) * 1024);
  }
  for (int i = 0; i <= 256; i++) ATAN_T[i] = (int16_t)lroundf(atanf(i / 256.0f) * 572.9578f);
  for (int y = 0; y < H; y++) {
    float dy = y + 0.5f - CY;
    float r2 = 120.0f * 120.0f - dy * dy;
    HALFW[y] = r2 > 0 ? (uint8_t)ceilf(sqrtf(r2)) : 0;
    if (HALFW[y] > 120) HALFW[y] = 120;
  }
  // fundo: azul-petroleo um pouco mais claro no centro, escurecendo para a borda
  for (int i = 0; i < 226; i++) {
    float d = sqrtf((float)(i << 6)) / 120.0f;            // 0 no centro .. 1 na borda
    float k = d * d;
    int r = (int)(9 - 6 * k), g = (int)(18 - 12 * k), b = (int)(26 - 17 * k);
    BG_LUT[i] = rgb(r, g, b);
    BG_GRID[i] = rgb(r + 3, g + 5, b + 6);
  }
  for (int k = 0; k < 60; k++) {
    float a = (-90 + k * 6) * 0.017453292f;
    bool major = (k % 5) == 0;
    float r0 = major ? 90.5f : 94.5f, r1 = 98.5f;
    ticks[k].x0 = (int16_t)lroundf(C16 + cosf(a) * r0 * 16);
    ticks[k].y0 = (int16_t)lroundf(C16 + sinf(a) * r0 * 16);
    ticks[k].x1 = (int16_t)lroundf(C16 + cosf(a) * r1 * 16);
    ticks[k].y1 = (int16_t)lroundf(C16 + sinf(a) * r1 * 16);
    ticks[k].major = major;
  }
}

void uiRender(uint32_t now, const tm& t) {
  demoTick(now);
  idleThink(now, t);
  if (mode == M_CLOCK && nqLen > 0) {             // notificacao interrompe a travessura
    an.type = AN_NONE; an.nextAt = now + 5000 + random(0, 10001);
    memset(&tmods, 0, sizeof(tmods)); shL = shR = shLy = shRy = 0;
    goX = curX; goY = curY; goS = curS;
    startNotif(now);
  }

  float tt = now / 1000.0f;
  float wx = bd.lookUntil ? 0 : 0.18f * sinf(tt * 0.45f);
  bd.lx += (bd.tlx + wx - bd.lx) * 0.2f;
  bd.ly += (bd.tly - bd.ly) * 0.2f;

  float hop = 0;
  if (now < bd.bounceUntil) hop = -fabsf(sinf((now - bd.bounceT0) * 3.14159f / 320.0f)) * 7;

  uint32_t e = now - modeT0;
  float p = (mode == M_CLOCK) ? 0 : (float)e / DUR[mode];
  if (mode != M_CLOCK && e >= DUR[mode]) {
    modeT0 = now; e = 0; p = 0;
    switch (mode) {
      case M_GO: mode = M_PULL; break;
      case M_PULL: mode = M_HOP; break;
      case M_HOP: mode = M_SHOW; break;
      case M_SHOW: mode = M_BACKIN; break;
      case M_BACKIN: mode = M_PUSH; break;
      case M_PUSH: mode = M_HOME; break;
      default: mode = M_CLOCK; setExpr(EX_HAPPY, 1500, now); bounce(700, now);
               bd.nextIdle = now + 4000; break;
    }
  }

  const float HX = 120, HY = 110;                 // casa do buddy (medido na referencia)
  const float HS = 1.1f;                          // escala em casa
  const float SS = 0.55f;                         // escala durante a animacao
  switch (mode) {
    case M_CLOCK:
      drawClockScene(now, t, hop);
      break;

    case M_GO: {
      drawClockBase(true, t);
      float k = easeIn(p);
      float x = lerpf(goX, CX + nsx * 150, k), y = lerpf(goY, CY + nsy * 150, k);
      bd.tlx = nsx; bd.tly = nsy;
      drawBuddyAt(x, y, lerpf(goS, SS, easeOut(p)), EX_SURPRISE, now);
    } break;

    case M_PULL: {
      drawClockBase(true, t);
      float k = easeOut(p);
      float ox = nsx * D * (1 - k), oy = nsy * D * (1 - k);
      drawCard((int)ox, (int)oy, cur, -1);
      bd.tlx = -nsx; bd.tly = -nsy;
      drawBuddyAt(CX + ox - nsx * RIM, CY + oy - nsy * RIM, SS, EX_HAPPY, now);
    } break;

    case M_HOP: {
      drawCard(0, 0, cur, 1.0f);
      float k = easeIn(p);
      float x = lerpf(CX - nsx * RIM, CX - nsx * 160, k), y = lerpf(CY - nsy * RIM, CY - nsy * 160, k);
      drawBuddyAt(x, y, SS, EX_HAPPY, now);
    } break;

    case M_SHOW:
      drawCard(0, 0, cur, 1.0f - p);
      break;

    case M_BACKIN: {
      drawCard(0, 0, cur, 0);
      float k = easeOut(p);
      float x = lerpf(CX - nsx * 160, CX - nsx * RIM, k), y = lerpf(CY - nsy * 160, CY - nsy * RIM, k);
      bd.tlx = nsx; bd.tly = nsy;
      drawBuddyAt(x, y, SS, EX_CURIOUS, now);
    } break;

    case M_PUSH: {
      drawClockBase(true, t);
      float k = easeIn(p);
      float ox = nsx * D * k, oy = nsy * D * k;
      drawCard((int)ox, (int)oy, cur, -1);
      drawBuddyAt(CX + ox - nsx * RIM, CY + oy - nsy * RIM, SS, EX_ANGRY, now);
    } break;

    case M_HOME: {
      drawClockBase(true, t);
      float k = easeOut(p);
      float x = lerpf(CX + nsx * 150, HX, k), y = lerpf(CY + nsy * 150, HY, k);
      bd.tlx = -nsx * (1 - k); bd.tly = -nsy * (1 - k);
      drawBuddyAt(x, y, lerpf(SS, HS, k), EX_HAPPY, now);
    } break;
  }
}
