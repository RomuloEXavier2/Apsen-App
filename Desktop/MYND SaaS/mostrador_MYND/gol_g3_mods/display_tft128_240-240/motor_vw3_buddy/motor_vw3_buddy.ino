/*
 * Monitor automotivo VW - ESP32-C3 SuperMini + TFT redondo 1.28" GC9A01 (240x240)
 *
 * Sequencia ao ligar:
 *   1) Toca o video (frames JPEG embutidos em frames.h)
 *   2) Fade para o mostrador MYND: hora + temperatura + progresso da Bambu + desk buddy
 *
 * Dados chegam do app MYND Watch (celular) por BLE, como TEXTO, na mesma caracteristica de antes:
 *   1731240000                 -> hora (epoch UTC). Continua aceitando so o numero, como na versao antiga.
 *   T:1731240000               -> hora (mesma coisa, com prefixo)
 *   C:25                       -> temperatura externa em graus C   (C:- apaga)
 *   P:68,R                     -> impressora: % e estado R=imprimindo P=pausada F=concluida X=falhou I=parada
 *   N:W|Fulano|Chego em 10 min -> notificacao (W=WhatsApp, I=Instagram, outra letra=generica)
 *   E:feliz                    -> forca expressao (normal, feliz, surpresa, raiva, triste, curioso)
 *   DEMO                       -> demonstracao de todas as animacoes
 * Os mesmos comandos funcionam pelo Serial Monitor (115200, "Nova linha") para testar sem o celular.
 *
 * Servico BLE:
 *   Service UUID:        6e400001-b5a3-f393-e0a9-e50e24dcca9e
 *   Characteristic UUID: 6e400002-b5a3-f393-e0a9-e50e24dcca9e  (Write / Write No Response)
 *
 * Bibliotecas: Adafruit GC9A01A, Adafruit GFX, JPEGDEC (BLE ja vem no core ESP32)
 * Arquivos na pasta do sketch: este .ino + ui.h + assets.h + frames.h
 * (assets.h = fontes e icones suavizados, gerado por assets_maker.py a partir de assets_src/)
 * Tools > Partition Scheme > "Huge APP (3MB No OTA/1MB SPIFFS)"
 */

#include <SPI.h>
#include <time.h>
#include <sys/time.h>
#include <esp_heap_caps.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <Adafruit_GFX.h>
#include <Adafruit_GC9A01A.h>
#include <JPEGDEC.h>
#include "frames.h"
#include "ui.h"

// ---------------- CONFIGURE AQUI ----------------
#define BLE_NAME      "MYND-Clock"
#define SERVICE_UUID  "6e400001-b5a3-f393-e0a9-e50e24dcca9e"
#define CHAR_UUID     "6e400002-b5a3-f393-e0a9-e50e24dcca9e"
#define VIDEO_FPS    12            // mesmo FPS dos frames exportados
#define TZ_BRASIL    "<-03>3"      // UTC-3 (Brasilia)
#define BUILD_LAG_S  60            // hora provisoria: compensa o tempo entre compilar e ligar
#define FRAME_MS     33            // ~30 quadros/s no maximo
// -------------------------------------------------

#define PIN_SCK  9
#define PIN_MOSI 8
#define PIN_DC   4
#define PIN_CS   3
#define PIN_RST  2

Adafruit_GC9A01A tft(PIN_CS, PIN_DC, PIN_RST);
JPEGDEC jpeg;

// Mensagens recebidas pelo BLE (callback roda em outra tarefa -> fila)
struct RxMsg { char s[224]; };
QueueHandle_t rxq;

// ---------------------------------------------------------------------------
// Texto: UTF-8 -> ASCII (a fonte do display so tem ASCII)
// ---------------------------------------------------------------------------
static void toAscii(char* s) {
  static const char* from[] = {"á","à","â","ã","ä","Á","À","Â","Ã","Ä","é","ê","è","É","Ê","í","Í","ó","ô","õ","ö","Ó","Ô","Õ",
                               "ú","ü","Ú","Ü","ç","Ç","ñ","Ñ","º","ª","“","”","‘","’","–","—","…"};
  static const char* to[]   = {"a","a","a","a","a","A","A","A","A","A","e","e","e","E","E","i","I","o","o","o","o","O","O","O",
                               "u","u","U","U","c","C","n","N","o","a","\"","\"","'","'","-","-","..."};
  char out[224]; int o = 0;
  for (int i = 0; s[i] && o < 222;) {
    unsigned char c = s[i];
    if (c < 0x80) { out[o++] = (c < 0x20) ? ' ' : c; i++; continue; }
    bool done = false;
    for (unsigned k = 0; k < sizeof(from) / sizeof(from[0]); k++) {
      int L = strlen(from[k]);
      if (!strncmp(s + i, from[k], L)) {
        for (int j = 0; to[k][j] && o < 222; j++) out[o++] = to[k][j];
        i += L; done = true; break;
      }
    }
    if (done) continue;
    // outro caractere multibyte (emoji etc.): pula a sequencia inteira
    int L = (c >= 0xF0) ? 4 : (c >= 0xE0) ? 3 : (c >= 0xC0) ? 2 : 1;
    i += L;
  }
  out[o] = 0;
  strcpy(s, out);
}

// ---------------------------------------------------------------------------
// Comandos (BLE ou Serial)
// ---------------------------------------------------------------------------
static void setEpoch(long long epoch) {
  if (epoch < 1700000000LL || epoch > 4102444800LL) {     // fora de 2023..2100
    Serial.printf("hora invalida (%lld)\n", epoch);
    return;
  }
  struct timeval tv = { (time_t)epoch, 0 };
  settimeofday(&tv, nullptr);
  uiSetSynced(true);
  Serial.printf("hora recebida, epoch=%lld\n", epoch);
}

void handleLine(char* s) {
  while (*s == ' ' || *s == '\r' || *s == '\n') s++;
  int n = strlen(s);
  while (n && (s[n - 1] == '\r' || s[n - 1] == '\n' || s[n - 1] == ' ')) s[--n] = 0;
  if (!n) return;

  if (s[0] >= '0' && s[0] <= '9') { setEpoch(strtoll(s, nullptr, 10)); return; }
  if (!strcasecmp(s, "DEMO")) { uiDemo(); Serial.println("demo iniciada"); return; }
  if (n < 2 || s[1] != ':') { Serial.printf("comando desconhecido: %s\n", s); return; }

  char* v = s + 2;
  switch (toupper(s[0])) {
    case 'T': setEpoch(strtoll(v, nullptr, 10)); break;

    case 'C':
      if (*v == '-' && !isdigit((unsigned char)v[1])) uiClearTemp();
      else uiSetTemp((int)lroundf(atof(v)));
      Serial.printf("temperatura: %s\n", v);
      break;

    case 'P': {
      int pct = atoi(v);
      char st = 'R';
      char* comma = strchr(v, ',');
      if (comma && comma[1]) st = toupper(comma[1]);
      if (pct < 0) st = 'I';
      uiSetPrint(pct, st);
      Serial.printf("impressora: %d%% (%c)\n", pct, st);
    } break;

    case 'N': {                                // N:W|titulo|texto
      static char none1[1], none2[1];          // texto vazio gravavel (toAscii escreve no buffer)
      none1[0] = none2[0] = 0;
      char app = toupper(*v);
      char* t1 = strchr(v, '|');
      char* title = t1 ? t1 + 1 : none1;
      char* t2 = t1 ? strchr(title, '|') : nullptr;
      char* text = none2;
      if (t2) { *t2 = 0; text = t2 + 1; }
      toAscii(title); toAscii(text);
      uiNotify(app, title, text);
      Serial.printf("notificacao %c: %s - %s\n", app, title, text);
    } break;

    case 'E':
      if (!uiExprByName(v, 4000)) Serial.printf("expressao desconhecida: %s\n", v);
      break;

    default:
      Serial.printf("comando desconhecido: %s\n", s);
  }
}

static void readSerial() {
  static char buf[224]; static int len = 0;
  while (Serial.available()) {
    char c = Serial.read();
    if (c == '\n' || c == '\r') {
      if (len) { buf[len] = 0; handleLine(buf); len = 0; }
    } else if (len < (int)sizeof(buf) - 1) buf[len++] = c;
  }
}

// ---------------------------------------------------------------------------
// BLE
// ---------------------------------------------------------------------------
class RxCallback : public BLECharacteristicCallbacks {
  void onWrite(BLECharacteristic* c) override {
    auto v = c->getValue();                 // String (core 3.x) ou std::string (core 2.x)
    if (v.length() == 0) return;
    RxMsg m;
    size_t L = v.length(); if (L > sizeof(m.s) - 1) L = sizeof(m.s) - 1;
    memcpy(m.s, v.c_str(), L); m.s[L] = 0;
    xQueueSend(rxq, &m, 0);                 // processado no loop()
  }
};

class ServerCallbacks : public BLEServerCallbacks {
  void onConnect(BLEServer* s) override { Serial.println("BLE: celular conectado"); }
  void onDisconnect(BLEServer* s) override {
    Serial.println("BLE: celular desconectado, anunciando de novo");
    BLEDevice::startAdvertising();
  }
};

void initBLE() {
  BLEDevice::init(BLE_NAME);
  BLEDevice::setMTU(247);                   // notificacoes cabem num pacote so
  BLEServer* server = BLEDevice::createServer();
  server->setCallbacks(new ServerCallbacks());

  BLEService* svc = server->createService(SERVICE_UUID);
  BLECharacteristic* ch = svc->createCharacteristic(
      CHAR_UUID,
      BLECharacteristic::PROPERTY_WRITE | BLECharacteristic::PROPERTY_WRITE_NR);
  ch->setCallbacks(new RxCallback());
  svc->start();

  BLEAdvertising* advertising = BLEDevice::getAdvertising();
  advertising->addServiceUUID(SERVICE_UUID);
  advertising->setScanResponse(true);
  BLEDevice::startAdvertising();
  Serial.println("BLE: anunciando como " BLE_NAME);
}

// ---------------------------------------------------------------------------
// Video: JPEGs embutidos no firmware
// ---------------------------------------------------------------------------
int jpgDraw(JPEGDRAW* p) {
  tft.drawRGBBitmap(p->x, p->y, p->pPixels, p->iWidth, p->iHeight);
  return 1;
}

void playVideo() {
  uint32_t t0 = millis();
  int last = -1, shown = 0;
  while (true) {
    int idx = (int)((millis() - t0) * VIDEO_FPS / 1000);
    if (idx >= N_FRAMES) break;
    if (idx == last) { delay(1); continue; }
    last = idx;
    if (jpeg.openFLASH((uint8_t*)FRAMES[idx], FRAME_LEN[idx], jpgDraw)) {
      jpeg.decode(0, 0, 0);
      jpeg.close();
      shown++;
    }
  }
  Serial.printf("Video: %d de %d quadros em %lu ms\n", shown, N_FRAMES, (unsigned long)(millis() - t0));
}

// ---------------------------------------------------------------------------
// Hora provisoria (ate o celular mandar a hora)
// ---------------------------------------------------------------------------
void setBuildTime() {
  static const char* M = "JanFebMarAprMayJunJulAugSepOctNovDec";
  char mon[4]; int d, y, hh, mm, ss;
  sscanf(__DATE__, "%3s %d %d", mon, &d, &y);
  sscanf(__TIME__, "%d:%d:%d", &hh, &mm, &ss);
  struct tm t = {};
  t.tm_year = y - 1900; t.tm_mon = (strstr(M, mon) - M) / 3; t.tm_mday = d;
  t.tm_hour = hh; t.tm_min = mm; t.tm_sec = ss; t.tm_isdst = -1;
  struct timeval tv = { mktime(&t) + BUILD_LAG_S, 0 };
  settimeofday(&tv, nullptr);
}

void push() { tft.drawRGBBitmap(0, 0, canvas.getBuffer(), W, H); }

void showClockFadeIn() {
  const uint16_t IN_MS = 600;
  uint32_t t0 = millis(), e;
  while ((e = millis() - t0) < IN_MS) {
    time_t now = time(nullptr);
    struct tm t; localtime_r(&now, &t);
    uiRender(millis(), t);
    fadeCanvas(fadeFactor((float)e / IN_MS));
    push();
  }
}

// ---------------------------------------------------------------------------
void setup() {
  Serial.begin(115200);
  rxq = xQueueCreate(8, sizeof(RxMsg));
  uiInit();
  randomSeed(esp_random());
  SPI.begin(PIN_SCK, -1, PIN_MOSI, PIN_CS);
  tft.begin(40000000);
  tft.fillScreen(0);

  Serial.printf("RAM livre: %u bytes, maior bloco: %u bytes\n",
                (unsigned)ESP.getFreeHeap(), (unsigned)heap_caps_get_largest_free_block(MALLOC_CAP_8BIT));
  if (!canvas.getBuffer()) {           // sem RAM para o canvas de 240x240 (115 KB)
    tft.fillScreen(ALERT);
    tft.setTextColor(0xFFFF); tft.setTextSize(2);
    tft.setCursor(40, 90);  tft.print("SEM MEMORIA");
    tft.setCursor(40, 120); tft.printf("livre %u", (unsigned)ESP.getFreeHeap());
    tft.setCursor(40, 145); tft.printf("bloco %u", (unsigned)heap_caps_get_largest_free_block(MALLOC_CAP_8BIT));
    while (true) { Serial.println("ERRO: canvas nao alocado (falta RAM)"); delay(2000); }
  }

  setenv("TZ", TZ_BRASIL, 1);
  tzset();
  setBuildTime();
  initBLE();                           // anuncia durante o video; o celular ja pode conectar

  playVideo();
  showClockFadeIn();
}

void loop() {
  RxMsg m;
  while (xQueueReceive(rxq, &m, 0) == pdTRUE) handleLine(m.s);
  readSerial();

  static uint32_t last = 0;
  uint32_t now = millis();
  if (now - last < FRAME_MS) { delay(1); return; }
  last = now;

  time_t tt = time(nullptr);
  struct tm t; localtime_r(&tt, &t);
  uiRender(now, t);
  push();
}
