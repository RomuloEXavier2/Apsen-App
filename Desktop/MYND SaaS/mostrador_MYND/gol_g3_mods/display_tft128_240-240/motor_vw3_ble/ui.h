/*
 * ui.h - Mostrador MYND: relogio + temperatura + progresso Bambu + desk buddy + notificacoes
 *
 * Tudo e desenhado num GFXcanvas16 (240x240) e enviado inteiro ao display a cada quadro.
 * Este arquivo nao conhece BLE nem o display: so desenha. Assim ele tambem roda no
 * simulador de PC (pasta preview/) para ver as animacoes sem gravar o ESP32.
 *
 * API usada pelo .ino:
 *   uiInit()                         uma vez no setup
 *   uiRender(millis(), tm)           desenha um quadro no canvas
 *   uiSetSynced(bool)                hora recebida do celular (tira o "SEM SINC")
 *   uiSetTemp(int c) / uiClearTemp() temperatura externa
 *   uiSetPrint(pct, estado)          estado: 'R' imprimindo, 'P' pausada, 'F' concluida, 'X' falhou, 'I' parada
 *   uiNotify(app, titulo, texto)     app: 'W' WhatsApp, 'I' Instagram, outro = generico
 *   uiExpr(nome, ms)                 forca uma expressao ("feliz", "surpresa", "raiva", "triste", "curioso", "normal")
 *   uiDemo()                         roda uma demonstracao de tudo
 */
#pragma once
#include <Arduino.h>
#include <Adafruit_GFX.h>
#include <Fonts/FreeSans9pt7b.h>
#include <Fonts/FreeSansBold9pt7b.h>
#include <Fonts/FreeSansBold12pt7b.h>
#include <Fonts/FreeSans24pt7b.h>
#include <time.h>
#include <math.h>
#include <string.h>
#include <stdio.h>
#include "icons.h"

#define W  240
#define H  240
#define CX 120
#define CY 120

#define C565(r, g, b) ((uint16_t)((((r) & 0xF8) << 8) | (((g) & 0xFC) << 3) | ((b) >> 3)))

// ---------------- paleta ----------------
const uint16_t NEON     = C565(0, 150, 255);
const uint16_t NEON_HI  = C565(120, 205, 255);
const uint16_t NEON_MID = C565(0, 80, 190);
const uint16_t GLOW1    = C565(0, 35, 95);
const uint16_t TRACK    = C565(0, 22, 55);
const uint16_t TICK_DIM = C565(0, 48, 105);
const uint16_t TICK_MAJ = C565(0, 95, 200);
const uint16_t WHITE    = C565(235, 245, 255);
const uint16_t ALERT    = C565(255, 60, 90);
const uint16_t OK_GREEN = C565(60, 220, 120);
const uint16_t AMBER    = C565(255, 190, 60);
const uint16_t CYAN_TXT = C565(150, 240, 255);   // miolo dos digitos da hora
const uint16_t CYAN_MID = C565(0, 175, 225);
const uint16_t CYAN_HALO= C565(0, 55, 85);
const uint16_t EYE_CORE = C565(110, 240, 255);
const uint16_t EYE_GLOW = C565(0, 140, 200);
const uint16_t CARD_BG  = C565(8, 16, 32);

const char* const DIAS[]  = {"DOM", "SEG", "TER", "QUA", "QUI", "SEX", "SAB"};
const char* const MESES[] = {"JAN", "FEV", "MAR", "ABR", "MAI", "JUN", "JUL", "AGO", "SET", "OUT", "NOV", "DEZ"};

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
  int  pct = -1;         // -1 = sem dado
  char pst = 'I';        // estado da impressora
} ui;

struct Buddy {
  float x = CX, y = 118, s = 1.0f;       // centro do corpo e escala
  int   expr = EX_NORMAL;
  uint32_t exprUntil = 0;
  float lx = 0, ly = 0, tlx = 0, tly = 0; // olhar atual / alvo (-1..1)
  uint32_t lookUntil = 0;
  uint32_t blinkAt = 2500, blinkT0 = 0;
  uint32_t bounceT0 = 0, bounceUntil = 0;
  uint32_t nextIdle = 6000;
  int lastMin = -1;
} bd;

// Notificacoes
enum Mode { M_CLOCK, M_GO, M_PULL, M_HOP, M_SHOW, M_BACKIN, M_PUSH, M_HOME };
const uint16_t DUR[] = {0, 450, 800, 350, 3000, 350, 800, 650};   // ms de cada fase
Mode mode = M_CLOCK;
uint32_t modeT0 = 0;
int nsx = -1, nsy = -1;            // canto escolhido (-1/+1, -1/+1)
Notif nq[4]; int nqHead = 0, nqLen = 0;
Notif cur;

// Demo
bool demoOn = false; uint32_t demoT0 = 0; int demoStep = 0;

// ===========================================================================
// Utilidades
// ===========================================================================
static inline uint16_t rgb(int r, int g, int b) {
  if (r < 0) r = 0; if (r > 255) r = 255;
  if (g < 0) g = 0; if (g > 255) g = 255;
  if (b < 0) b = 0; if (b > 255) b = 255;
  return C565(r, g, b);
}

// mistura dois RGB565: a = 0 (so bg) .. 256 (so fg)
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

static inline uint16_t scale565(uint16_t c, int f) {          // f: 0..256
  uint16_t r = ((c >> 11) * f) >> 8;
  uint16_t g = (((c >> 5) & 63) * f) >> 8;
  uint16_t b = ((c & 31) * f) >> 8;
  return (r << 11) | (g << 5) | b;
}

static inline void plotA(int x, int y, uint16_t c, int a) {
  if ((unsigned)x >= W || (unsigned)y >= H) return;
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

static int16_t COS_T[360], SIN_T[360];
static inline int normA(int a) { a %= 360; return a < 0 ? a + 360 : a; }
static inline int px(int r, int a) { return CX + ((r * COS_T[normA(a)] + 512) >> 10); }
static inline int py(int r, int a) { return CY + ((r * SIN_T[normA(a)] + 512) >> 10); }

static inline int advOf(const GFXfont* f, char c) {
  if (c < (char)f->first || c > (char)f->last) c = '?';
  return f->glyph[(uint8_t)c - f->first].xAdvance;
}
static int textW(const char* s, const GFXfont* f, int sp = 0, int n = -1) {
  int w = 0, i = 0;
  for (; s[i] && (n < 0 || i < n); i++) w += advOf(f, s[i]) + sp;
  return i ? w - sp : 0;
}

// Texto com espacamento entre letras, centralizado. y = linha de base
void spacedC(const char* s, int cx, int y, const GFXfont* f, uint16_t col, int sp) {
  int x = cx - textW(s, f, sp) / 2;
  canvas.setFont(f); canvas.setTextSize(1); canvas.setTextColor(col);
  for (int i = 0; s[i]; i++) {
    canvas.setCursor(x, y); canvas.print(s[i]);
    x += advOf(f, s[i]) + sp;
  }
}

void textAt(const char* s, int x, int y, const GFXfont* f, uint16_t col) {
  canvas.setFont(f); canvas.setTextSize(1); canvas.setTextColor(col);
  canvas.setCursor(x, y); canvas.print(s);
}

// Texto neon (halo + meio + miolo)
void neonL(const char* s, int x, int y, const GFXfont* f, uint16_t core, uint16_t mid, uint16_t halo) {
  static const int8_t o1[4][2] = {{-2, 0}, {2, 0}, {0, -2}, {0, 2}};
  static const int8_t o2[4][2] = {{-1, -1}, {1, -1}, {-1, 1}, {1, 1}};
  canvas.setFont(f); canvas.setTextSize(1);
  for (int i = 0; i < 4; i++) { canvas.setTextColor(halo); canvas.setCursor(x + o1[i][0], y + o1[i][1]); canvas.print(s); }
  for (int i = 0; i < 4; i++) { canvas.setTextColor(mid);  canvas.setCursor(x + o2[i][0], y + o2[i][1]); canvas.print(s); }
  canvas.setTextColor(core); canvas.setCursor(x, y); canvas.print(s);
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

  // piscar
  if (now >= bd.blinkAt) { bd.blinkT0 = now; bd.blinkAt = now + 2200 + random(0, 4000); }

  // virada do minuto: susto + olha para a hora
  if (bd.lastMin != t.tm_min) {
    if (bd.lastMin >= 0 && mode == M_CLOCK) { setExpr(EX_SURPRISE, 800, now); lookAt(0, 1, 1200, now); bounce(600, now); }
    bd.lastMin = t.tm_min;
  }

  if (mode != M_CLOCK || now < bd.nextIdle) return;
  bd.nextIdle = now + 5000 + random(0, 7000);
  if (bd.exprUntil) return;                       // ja esta reagindo a algo
  switch (random(0, 7)) {
    case 0: lookAt(-1, 0.3f, 1800, now); setExpr(EX_CURIOUS, 1800, now); break;          // olha a temperatura
    case 1: lookAt(1, 0.3f, 1800, now);                                                  // olha a impressora
            setExpr(ui.pst == 'R' ? EX_HAPPY : EX_CURIOUS, 1800, now); break;
    case 2: lookAt(0, 1, 1500, now); break;                                              // olha a hora
    case 3: setExpr(EX_HAPPY, 1300, now); bounce(1000, now); break;
    case 4: lookAt(-0.6f, -0.9f, 1200, now); break;
    case 5: lookAt(0.8f, -0.5f, 1000, now); setExpr(EX_SURPRISE, 600, now); break;
    default: break;                                                                      // so respira
  }
}

// ===========================================================================
// Corpo do buddy (metaballs) - matematica inteira (o C3 nao tem FPU)
// ===========================================================================
struct Ball { int16_t x, y, r; int32_t q; };   // q = r*r*256  (campo em Q8, limiar 256)
#define BMAX 10
#define FT   256
static Ball balls[BMAX];
static int nBalls = 0, handFrom = 99;
#define GWMAX 84
#define GHMAX 100
static int32_t grid[GWMAX * GHMAX];

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

// geometria atual (para olhos/brilho)
static int headX, headY, bodyTop, bodyH;

static void buildBalls(float bx, float by, float s, float t, bool hands) {
  nBalls = 0;
  float br = sinf(t * 2.2f);                       // respiracao
  float w1 = sinf(t * 1.7f), w2 = sinf(t * 1.3f + 1.1f), w3 = sinf(t * 2.6f + 2.0f);
  float sq = 1.0f + 0.025f * br;                   // estica/achata
  addBall(bx + 1.5f * w2 * s,  by - 30 * s * sq + br * 1.2f * s, 24 * s);   // cabeca
  addBall(bx,                  by - 7 * s,                       20 * s);   // tronco
  addBall(bx - 2 * s + w1 * s, by + 15 * s,                      20 * s);   // barriga
  addBall(bx + 10 * s,         by + 32 * s + w1 * 1.5f * s,      14 * s);   // base direita
  addBall(bx - 14 * s,         by + 35 * s + w2 * 1.5f * s,      12 * s);   // base esquerda
  addBall(bx - 26 * s,         by + 49 * s + w3 * 2.5f * s,       7 * s);   // gota
  addBall(bx + 23 * s,         by + 51 * s + w1 * 2.5f * s,       6 * s);   // gota
  addBall(bx - 5 * s,          by + 52 * s + w2 * 2.0f * s,       6 * s);   // gota
  handFrom = nBalls;
  if (hands) {                                     // maos apoiadas em cima da hora
    addBall(bx - 41 * s, by - 13 * s + w2 * 1.0f * s, 9 * s);
    addBall(bx + 40 * s, by - 15 * s + w1 * 1.0f * s, 9 * s);
  }
  headX = (int)lroundf(bx + 1.5f * w2 * s);
  headY = (int)lroundf(by - 30 * s * sq + br * 1.2f * s);
  bodyTop = headY - (int)(30 * s);
  bodyH = (int)(120 * s);
}

// cor do corpo num pixel (f >= FT)
static inline uint16_t bodyColor(int x, int y, int32_t f, float s) {
  int e = (int)((f - FT) * 256 / (FT / 2));           // 0 na borda .. 256 por dentro
  if (e > 256) e = 256;
  int v = (y - bodyTop) * 256 / (bodyH > 0 ? bodyH : 1);
  if (v < 0) v = 0; if (v > 256) v = 256;
  int r = 24 + ((6 - 24) * v >> 8), g = 50 + ((16 - 50) * v >> 8), b = 104 + ((44 - 104) * v >> 8);
  // borda clara (slime translucido)
  int rr = 70 + ((r - 70) * e >> 8), gg = 140 + ((g - 140) * e >> 8), bb = 225 + ((b - 225) * e >> 8);
  // brilho especular na cabeca
  int hx = headX - (int)(11 * s), hy = headY - (int)(13 * s);
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

static inline void shadePixel(uint16_t* p, int x, int y, int32_t f, float s) {
  if (f < FT) {
    if (f > FT * 3 / 4) {                                // brilho em volta
      int a = (int)((f - FT * 3 / 4) * 80 / (FT / 4));
      *p = blend565(*p, C565(0, 70, 160), a);
    }
    return;
  }
  *p = blend565(*p, bodyColor(x, y, f, s), 205);         // levemente translucido
}

// Passo 1: corpo inteiro (campo calculado numa grade 2x2 e interpolado)
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
    for (int i = 0; i < gw; i++)
      grid[j * gw + i] = fieldAt(x0 + i * 2, y0 + j * 2, 0, nBalls);

  uint16_t* buf = canvas.getBuffer();
  for (int y = y0; y <= y1; y++) {
    int gy = (y - y0) >> 1, fy = (y - y0) & 1;
    const int32_t* r0 = grid + gy * gw;
    const int32_t* r1 = r0 + gw;
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

// Passo 2: so as maos, desenhadas por cima dos digitos (parece que ele segura a hora)
static void drawHands(float s) {
  uint16_t* buf = canvas.getBuffer();
  for (int h = handFrom; h < nBalls; h++) {
    int m = balls[h].r * 2;
    int x0 = balls[h].x - m, x1 = balls[h].x + m, y0 = balls[h].y - m, y1 = balls[h].y + m;
    if (x0 < 0) x0 = 0; if (y0 < 0) y0 = 0; if (x1 > W - 1) x1 = W - 1; if (y1 > H - 1) y1 = H - 1;
    for (int y = y0; y <= y1; y++)
      for (int x = x0; x <= x1; x++) {
        int32_t fh = fieldAt(x, y, h, h + 1);
        if (fh < FT / 2) continue;
        int32_t f = fieldAt(x, y, 0, nBalls);
        if (f < FT) continue;
        int32_t fo = f - fh;                       // contribuicao do resto do corpo
        if (fo > fh) continue;                     // pixel pertence mais ao corpo que a mao
        uint16_t* p = buf + y * W + x;
        *p = blend565(*p, bodyColor(x, y, f, s), 230);
      }
  }
}

// ===========================================================================
// Olhos
// ===========================================================================
// Desenha a forma do olho (elipse recortada conforme a expressao)
static void eyeShape(int ex, int ey, int rx, int ry, int type, int side, uint16_t col, int alpha) {
  if (rx < 1) rx = 1; if (ry < 1) ry = 1;
  int32_t rx2 = rx * rx, ry2 = ry * ry, R = rx2 * ry2;
  int sh = ry * 13 / 10;                            // deslocamento do recorte do olho feliz
  for (int dy = -ry; dy <= ry; dy++) {
    for (int dx = -rx; dx <= rx; dx++) {
      if ((int32_t)dx * dx * ry2 + (int32_t)dy * dy * rx2 > R) continue;
      int inward = dx * (-side);                    // positivo = lado de dentro (nariz)
      if (type == EX_HAPPY) {
        int ddy = dy - sh;
        if ((int32_t)dx * dx * ry2 + (int32_t)ddy * ddy * rx2 <= R) continue;   // "^"
      } else if (type == EX_ANGRY) {
        if (dy * 10 < -ry * 2 + inward * 11) continue;                         // sobrancelha "\ /"
      } else if (type == EX_SAD) {
        if (dy * 10 < -ry * 2 - inward * 11) continue;                         // "/ \"
      }
      plotA(ex + dx, ey + dy, col, alpha);
    }
  }
}

static void drawEyes(float s, uint32_t now, int expr) {
  float lx = bd.lx, ly = bd.ly;
  int baseRx = (int)lroundf(6 * s), baseRy = (int)lroundf(9 * s);
  int sep = (int)lroundf(11 * s);
  int ey = headY - (int)lroundf(1 * s) + (int)lroundf(ly * 3 * s);
  int exo = (int)lroundf(lx * 5 * s);

  // piscada (nao pisca se estiver feliz/raiva - o desenho ja e "fechado")
  float open = 1.0f;
  uint32_t bt = now - bd.blinkT0;
  if (bt < 180 && expr != EX_HAPPY) open = fabsf((float)bt - 90) / 90.0f;
  if (open < 0.12f) open = 0.12f;

  for (int side = -1; side <= 1; side += 2) {
    int rx = baseRx, ry = baseRy, oy = 0;
    switch (expr) {
      case EX_SURPRISE: rx = baseRx * 13 / 10 + 1; ry = baseRy * 11 / 10 + 1; break;
      case EX_SAD:      oy = (int)(2 * s); rx = baseRx; break;
      case EX_HAPPY:    oy = (int)(-1 * s); rx = baseRx + 1; break;
      case EX_CURIOUS:  if (side > 0) { rx = baseRx * 5 / 4 + 1; ry = baseRy * 5 / 4 + 1; }
                        else ry = baseRy * 11 / 20 + 1;
                        break;
      default: break;
    }
    ry = (int)lroundf(ry * open); if (ry < 1) ry = 1;
    int ex = headX + side * sep + exo, eyy = ey + oy;
    eyeShape(ex, eyy, rx + 3, ry + 3, expr, side, EYE_GLOW, 70);    // halo
    eyeShape(ex, eyy, rx + 1, ry + 1, expr, side, EYE_GLOW, 150);
    eyeShape(ex, eyy, rx, ry, expr, side, EYE_CORE, 256);            // miolo
    if (expr != EX_HAPPY && expr != EX_ANGRY && ry > 3)              // reflexo
      eyeShape(ex - rx / 3, eyy - ry / 3, rx / 3 + 1, ry / 4 + 1, EX_NORMAL, side, WHITE, 200);
  }
  // boca so na surpresa
  if (expr == EX_SURPRISE) {
    int mx = headX + exo, my = headY + (int)(13 * s);
    int r = (int)(3 * s) + 1;
    canvas.drawCircle(mx, my, r + 1, EYE_GLOW);
    canvas.drawCircle(mx, my, r, EYE_CORE);
  }
}

// Buddy completo (usado fora do relogio: animacao de notificacao)
static void drawBuddyAt(float x, float y, float s, int expr, uint32_t now) {
  float t = now / 1000.0f;
  buildBalls(x, y, s, t, false);
  drawBody(s);
  drawEyes(s, now, expr);
}

// ===========================================================================
// Mostrador
// ===========================================================================
static void drawTicks(int sec) {
  const int TAIL = 14;
  for (int k = 0; k < 60; k++) {
    int a = -90 + k * 6;
    bool major = (k % 5) == 0;
    int rin = major ? 100 : 105;
    canvas.drawLine(px(112, a), py(112, a), px(rin, a), py(rin, a), major ? TICK_MAJ : TICK_DIM);
  }
  for (int i = TAIL; i >= 0; i--) {
    int k = ((sec - i) % 60 + 60) % 60;
    int a = -90 + k * 6;
    int f = 256 - (256 * i) / (TAIL + 1);
    f = (f * f) >> 8;
    bool major = (k % 5) == 0;
    int rin = major ? 98 : 102;
    uint16_t col = (i == 0) ? WHITE : scale565(NEON_HI, f);
    canvas.drawLine(px(112, a), py(112, a), px(rin, a), py(rin, a), col);
  }
  int a = -90 + sec * 6;
  canvas.fillCircle(px(108, a), py(108, a), 4, GLOW1);
  canvas.fillCircle(px(108, a), py(108, a), 2, WHITE);
}

static bool printActive() { return ui.pct >= 0 && (ui.pst == 'R' || ui.pst == 'P' || ui.pst == 'F'); }

static void drawRing() {
  canvas.drawCircle(CX, CY, 119, GLOW1);
  if (!printActive()) { canvas.drawCircle(CX, CY, 117, NEON_MID); return; }
  // anel externo = progresso da impressao
  int deg = ui.pct * 360 / 100;
  uint16_t c = ui.pst == 'F' ? OK_GREEN : ui.pst == 'P' ? AMBER : NEON_HI;
  for (int a = 0; a < 360; a++) {                 // raios grossos colados = arco continuo
    int aa = -90 + a;
    uint16_t cc = (a < deg) ? c : TRACK;
    canvas.drawLine(px(114, aa), py(114, aa), px(118, aa), py(118, aa), cc);
    int x1 = (px(116, aa) + px(116, aa + 1)) / 2, y1 = (py(116, aa) + py(116, aa + 1)) / 2;
    canvas.fillCircle(x1, y1, 1, cc);
  }
  if (deg > 0 && deg < 360) {                      // cabeca brilhante do arco
    int aa = -90 + deg;
    canvas.fillCircle(px(116, aa), py(116, aa), 3, GLOW1);
    canvas.fillCircle(px(116, aa), py(116, aa), 2, WHITE);
  }
}

// Hora com celulas de largura fixa (24 px por digito) - cabe com os icones dos lados
static const int TIME_BASE = 141, CELL = 24, CCELL = 10;
static void drawTime(int hh, int mm, bool colonOn) {
  const GFXfont* F = &FreeSans24pt7b;
  char s[6]; snprintf(s, sizeof(s), "%02d:%02d", hh, mm);
  int total = 4 * CELL + CCELL;
  int x = CX - total / 2;
  for (int i = 0; i < 5; i++) {
    int cw = (i == 2) ? CCELL : CELL;
    const GFXglyph* g = &F->glyph[(uint8_t)s[i] - F->first];
    int gx = x + (cw - g->width) / 2 - g->xOffset;
    char one[2] = {s[i], 0};
    if (i == 2) neonL(one, gx, TIME_BASE - 2, F, colonOn ? CYAN_TXT : CYAN_MID, colonOn ? CYAN_MID : CYAN_HALO, CYAN_HALO);
    else        neonL(one, gx, TIME_BASE, F, CYAN_TXT, CYAN_MID, CYAN_HALO);
    x += cw;
  }
}

static void drawThermo(int cx, int top, uint16_t fill) {
  canvas.drawRoundRect(cx - 3, top, 7, 15, 3, NEON_HI);
  canvas.fillCircle(cx, top + 18, 5, NEON_HI);
  canvas.fillCircle(cx, top + 18, 3, fill);
  canvas.drawFastVLine(cx, top + 4, 12, fill);
  canvas.drawFastHLine(cx + 5, top + 3, 3, NEON_MID);
  canvas.drawFastHLine(cx + 5, top + 7, 3, NEON_MID);
  canvas.drawFastHLine(cx + 5, top + 11, 3, NEON_MID);
}

static void drawSideInfo() {
  const GFXfont* F = &FreeSans9pt7b;
  char b[8];
  // ---- temperatura (esquerda) ----
  const int LX = 40, BASE = 136;
  uint16_t tc = !ui.hasTemp ? NEON_MID : ui.temp < 18 ? NEON_HI : ui.temp < 28 ? C565(120, 230, 255)
              : ui.temp < 34 ? AMBER : ALERT;
  drawThermo(LX, 92, tc);
  if (ui.hasTemp) {
    snprintf(b, sizeof(b), "%d", ui.temp);
    int w = textW(b, F) + 6 + textW("C", F);
    int x = LX - w / 2;
    textAt(b, x, BASE, F, WHITE);
    x += textW(b, F) + 3;
    canvas.drawCircle(x, BASE - 11, 2, WHITE);
    textAt("C", x + 3, BASE, F, WHITE);
  } else {
    spacedC("--", LX, BASE, F, NEON_MID, 1);
  }

  // ---- impressora (direita) ----
  const int RX = 200;
  bool act = printActive() || ui.pst == 'X';
  int ix = RX - BAMBU_W / 2, iy = 112 - BAMBU_H;
  uint16_t* buf = canvas.getBuffer();
  for (int y = 0; y < BAMBU_H; y++)
    for (int x = 0; x < BAMBU_W; x++) {
      uint16_t c = pgm_read_word(&bambu_icon[y * BAMBU_W + x]);
      if (!c) continue;
      int X = ix + x, Y = iy + y;
      if ((unsigned)X < W && (unsigned)Y < H) buf[Y * W + X] = act ? c : scale565(c, 80);
    }
  uint16_t pc = WHITE;
  if (!act || ui.pct < 0) { strcpy(b, "--"); pc = NEON_MID; }
  else if (ui.pst == 'X') { strcpy(b, "ERRO"); pc = ALERT; }
  else if (ui.pct >= 100 || ui.pst == 'F') { strcpy(b, "100"); pc = OK_GREEN; }
  else { snprintf(b, sizeof(b), "%d%%", ui.pct); pc = ui.pst == 'P' ? AMBER : WHITE; }
  spacedC(b, RX, BASE, F, pc, 0);
}

static void drawDate(const tm& t) {
  char b[20];
  if (!ui.synced) { spacedC("SEM SINC", CX, 210, &FreeSans9pt7b, ALERT, 2); return; }
  snprintf(b, sizeof(b), "%s %02d %s", DIAS[t.tm_wday], t.tm_mday, MESES[t.tm_mon]);
  spacedC(b, CX, 210, &FreeSans9pt7b, NEON_HI, 2);
}

// Fundo do relogio (sem o buddy)
static void drawClockBase(const tm& t, bool withTime) {
  canvas.fillScreen(0);
  canvas.drawCircle(CX, CY, 93, TRACK);
  drawRing();
  drawTicks(t.tm_sec);
  drawSideInfo();
  drawDate(t);
  if (withTime) drawTime(t.tm_hour, t.tm_min, (t.tm_sec & 1) == 0);
}

// ===========================================================================
// Cartao de notificacao (disco que ocupa a tela inteira)
// ===========================================================================
static uint16_t appColor(char a) { return a == 'W' ? C565(37, 211, 102) : a == 'I' ? C565(225, 48, 140) : NEON; }
static const char* appName(char a) { return a == 'W' ? "WHATSAPP" : a == 'I' ? "INSTAGRAM" : "NOTIFICACAO"; }

// Icone generico: balao de conversa com 3 pontinhos sobre um circulo na cor do app
static void drawAppIcon(int cx, int cy, int r, char app) {
  uint16_t c = appColor(app);
  canvas.fillCircle(cx, cy, r + 3, scale565(c, 70));
  canvas.fillCircle(cx, cy, r, c);
  int bw = r * 6 / 5, bh = r * 4 / 5;
  canvas.fillRoundRect(cx - bw / 2, cy - bh / 2 - 2, bw, bh, bh / 3, WHITE);
  canvas.fillTriangle(cx - bw / 4, cy + bh / 2 - 4, cx - bw / 4 + 7, cy + bh / 2 - 4, cx - bw / 3, cy + bh / 2 + 4, WHITE);
  for (int i = -1; i <= 1; i++) canvas.fillCircle(cx + i * (bw / 4), cy - 2, 2, c);
}

// quebra texto em linhas que caibam em maxW (palavras inteiras; "..." se sobrar)
static int wrapText(const char* s, const GFXfont* f, int maxW, char lines[][48], int maxLines) {
  int n = 0; const char* p = s;
  while (*p && n < maxLines) {
    while (*p == ' ') p++;
    if (!*p) break;
    int len = 0, lastSpace = -1;
    while (p[len] && p[len] != '\n') {
      if (len >= 46 || textW(p, f, 0, len + 1) > maxW) break;
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
  if (*p && n == maxLines) {                      // sobrou texto: reticencias na ultima linha
    char* L = lines[n - 1]; int l = strlen(L);
    while (l > 0 && textW(L, f) + textW("...", f) > maxW) L[--l] = 0;
    while (l > 0 && L[l - 1] == ' ') L[--l] = 0;
    strcat(L, "...");
  }
  return n;
}

static void drawCard(int ox, int oy, const Notif& n, float timeLeft) {
  int cx = CX + ox, cy = CY + oy;
  uint16_t ac = appColor(n.app);
  canvas.fillCircle(cx, cy, 120, CARD_BG);
  canvas.drawCircle(cx, cy, 119, scale565(ac, 90));
  canvas.drawCircle(cx, cy, 118, scale565(ac, 160));
  if (timeLeft >= 0) {                             // contagem dos 3 s no anel
    int deg = (int)(timeLeft * 360);
    for (int a = 0; a < deg; a++)
      for (int r = 115; r <= 117; r++)
        canvas.drawPixel(cx - CX + px(r, -90 + a), cy - CY + py(r, -90 + a), ac);
  }
  drawAppIcon(cx, cy - 66, 17, n.app);
  spacedC(appName(n.app), cx, cy - 31, &FreeSans9pt7b, ac, 2);

  // remetente (negrito, cortado se for longo)
  const GFXfont* FT12 = &FreeSansBold12pt7b;
  char title[36]; strncpy(title, n.title, 35); title[35] = 0;
  int l = strlen(title);
  if (textW(title, FT12) > 196) {
    while (l > 0 && textW(title, FT12) + textW("...", FT12) > 196) title[--l] = 0;
    strcat(title, "...");
  }
  spacedC(title, cx, cy - 3, FT12, WHITE, 0);
  canvas.drawFastHLine(cx - 40, cy + 8, 80, scale565(ac, 120));

  char lines[3][48];
  const int maxW[3] = {196, 184, 150};
  int nl = wrapText(n.text, &FreeSans9pt7b, 184, lines, 3);
  for (int i = 0; i < nl; i++) {
    if (textW(lines[i], &FreeSans9pt7b) > maxW[i]) {   // linha de baixo e mais estreita (tela redonda)
      int k = strlen(lines[i]);
      while (k > 0 && textW(lines[i], &FreeSans9pt7b) + textW("...", &FreeSans9pt7b) > maxW[i]) lines[i][--k] = 0;
      strcat(lines[i], "...");
    }
    spacedC(lines[i], cx, cy + 32 + i * 21, &FreeSans9pt7b, C565(200, 220, 240), 0);
  }
}

// ===========================================================================
// Fila e maquina de estados da notificacao
// ===========================================================================
static void startNotif(uint32_t now) {
  cur = nq[nqHead]; nqHead = (nqHead + 1) % 4; nqLen--;
  int c = random(0, 4);                          // canto: 0 TL, 1 TR, 2 BR, 3 BL
  nsx = (c == 1 || c == 2) ? 1 : -1;
  nsy = (c >= 2) ? 1 : -1;
  mode = M_GO; modeT0 = now;
}

void uiNotify(char app, const char* title, const char* text) {
  if (nqLen == 4) { nqHead = (nqHead + 1) % 4; nqLen--; }   // fila cheia: descarta a mais antiga
  Notif& n = nq[(nqHead + nqLen) % 4];
  n.app = app;
  strncpy(n.title, title, sizeof(n.title) - 1); n.title[sizeof(n.title) - 1] = 0;
  strncpy(n.text, text, sizeof(n.text) - 1);    n.text[sizeof(n.text) - 1] = 0;
  nqLen++;
}

static const float RIM = 84;      // distancia do centro do cartao ate a borda (na diagonal)
static const float D = 250;       // deslocamento do cartao fora da tela

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
// Quadro
// ===========================================================================
void uiInit() {
  for (int i = 0; i < 360; i++) {
    COS_T[i] = (int16_t)lroundf(cosf(i * 0.017453292f) * 1024);
    SIN_T[i] = (int16_t)lroundf(sinf(i * 0.017453292f) * 1024);
  }
}

void uiRender(uint32_t now, const tm& t) {
  demoTick(now);
  idleThink(now, t);
  if (mode == M_CLOCK && nqLen > 0) startNotif(now);

  // olhar suave + leve "passeio" do olhar
  float tt = now / 1000.0f;
  float wx = bd.lookUntil ? 0 : 0.18f * sinf(tt * 0.45f);
  bd.lx += (bd.tlx + wx - bd.lx) * 0.2f;
  bd.ly += (bd.tly - bd.ly) * 0.2f;

  // pulinho
  float hop = 0;
  if (now < bd.bounceUntil) hop = -fabsf(sinf((now - bd.bounceT0) * 3.14159f / 320.0f)) * 7;

  uint32_t e = now - modeT0;
  float p = (mode == M_CLOCK) ? 0 : (float)e / DUR[mode];
  if (mode != M_CLOCK && e >= DUR[mode]) {         // proxima fase
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

  const float HX = CX, HY = 118;                    // posicao de casa do buddy
  const float SS = 0.55f;                           // escala pequena durante a animacao
  switch (mode) {
    case M_CLOCK: {
      drawClockBase(t, false);
      buildBalls(HX, HY + hop, 1.0f, tt, true);
      drawBody(1.0f);
      drawTime(t.tm_hour, t.tm_min, (t.tm_sec & 1) == 0);
      drawHands(1.0f);
      drawEyes(1.0f, now, bd.expr);
    } break;

    case M_GO: {                                   // vai ate o canto e sai
      drawClockBase(t, true);
      float k = easeIn(p);
      float x = lerpf(HX, CX + nsx * 150, k), y = lerpf(HY, CY + nsy * 150, k);
      bd.tlx = nsx; bd.tly = nsy;
      drawBuddyAt(x, y, lerpf(1.0f, SS, easeOut(p)), EX_SURPRISE, now);
    } break;

    case M_PULL: {                                 // volta puxando o cartao
      drawClockBase(t, true);
      float k = easeOut(p);
      float ox = nsx * D * (1 - k), oy = nsy * D * (1 - k);
      drawCard((int)ox, (int)oy, cur, -1);
      bd.tlx = -nsx; bd.tly = -nsy;
      drawBuddyAt(CX + ox - nsx * RIM, CY + oy - nsy * RIM, SS, EX_HAPPY, now);
    } break;

    case M_HOP: {                                  // solta o cartao e sai pela borda
      drawCard(0, 0, cur, 1.0f);
      float k = easeIn(p);
      float x = lerpf(CX - nsx * RIM, CX - nsx * 160, k), y = lerpf(CY - nsy * RIM, CY - nsy * 160, k);
      drawBuddyAt(x, y, SS, EX_HAPPY, now);
    } break;

    case M_SHOW:
      drawCard(0, 0, cur, 1.0f - p);
      break;

    case M_BACKIN: {                               // volta para tirar o cartao
      drawCard(0, 0, cur, 0);
      float k = easeOut(p);
      float x = lerpf(CX - nsx * 160, CX - nsx * RIM, k), y = lerpf(CY - nsy * 160, CY - nsy * RIM, k);
      bd.tlx = nsx; bd.tly = nsy;
      drawBuddyAt(x, y, SS, EX_CURIOUS, now);
    } break;

    case M_PUSH: {                                 // empurra o cartao para fora
      drawClockBase(t, true);
      float k = easeIn(p);
      float ox = nsx * D * k, oy = nsy * D * k;
      drawCard((int)ox, (int)oy, cur, -1);
      drawBuddyAt(CX + ox - nsx * RIM, CY + oy - nsy * RIM, SS, EX_ANGRY, now);
    } break;

    case M_HOME: {                                 // volta para o meio do relogio
      drawClockBase(t, true);
      float k = easeOut(p);
      float x = lerpf(CX + nsx * 150, HX, k), y = lerpf(CY + nsy * 150, HY, k);
      bd.tlx = -nsx * (1 - k); bd.tly = -nsy * (1 - k);
      drawBuddyAt(x, y, lerpf(SS, 1.0f, k), EX_HAPPY, now);
    } break;
  }
}
