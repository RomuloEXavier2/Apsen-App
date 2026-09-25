# Mapa de elementos — MYND_HUD_BAMBU.bin

Arquivo: `app/src/main/assets/faces/MYND_HUD_BAMBU.bin`  
Display: 240 × 296 px  
Estrutura: imagens a partir de 0x0C0 (IMG_SZ=0x84), textos em seguida (TXT_SZ=0x20), preview em 0x5E8, background RGB565 em 0x10ABC

---

## Imagens (tipo 0x06) — lidas do watch, não do canal weather

| ID | Offset | Tipo | x   | y   | Fonte                        |
|----|--------|------|-----|-----|------------------------------|
| IMG[0] | 0x0C0 | 01 | 10  | 40  | Hora dezena                  |
| IMG[1] | 0x144 | 02 | 55  | 40  | Hora unidade                 |
| IMG[2] | 0x1C8 | 03 | 111 | 40  | Minuto dezena                |
| IMG[3] | 0x24C | 04 | 156 | 40  | Minuto unidade               |
| IMG[4] | 0x2D0 | 31 | 58  | 13  | Dia da semana (ícone)        |
| IMG[5] | 0x354 | 10 | 104 | 12  | Dia dezena                   |
| IMG[6] | 0x3D8 | 11 | 120 | 12  | Dia unidade                  |
| IMG[7] | 0x45C | 06 | 62  | 0   | Ícone weather (0–15) → barra de progresso Bambu |

---

## Textos (tipo 0x05)

### Estado ORIGINAL (como veio o arquivo, antes de qualquer patch)

| ID | Offset | Tipo (byte) | Tipo# | x   | y   | w  | Label visual     | Fonte de dados              |
|----|--------|-------------|-------|-----|-----|----|------------------|-----------------------------|
| TXT[0] | 0x4E0 | 0x16 | 22 | 202 | 76  | 30 | — (segundos)     | Sensor relógio (segundos)   |
| TXT[1] | 0x500 | 0x1B | 27 | 140 | 14  | 44 | — (mês)          | Sensor relógio (mês abrev.) |
| TXT[2] | 0x520 | 0x0E | 14 |   7 | 128 | 52 | PASSOS           | Sensor relógio (passos)     |
| TXT[3] | 0x540 | **0x0D** | **13** |   7 | 190 | 52 | **BICO**     | **weather.current → "X°C"** |
| TXT[4] | 0x560 | 0x12 | 18 | 181 | 128 | 52 | BATERIA          | Sensor relógio (bateria %)  |
| TXT[5] | 0x580 | **0x05** | **5**  | 181 | 190 | 52 | **BPM**      | **Sensor óptico (BPM)**     |
| TXT[6] | 0x5A0 | 0x21 | 33 | 124 | 236 | 56 | — (barra inf.)   | weather.dia0 → "lo/hi"      |

> Neste estado o BICO exibe temperatura do bico (weather.current) e o BPM exibe batimento cardíaco real.

---

### Estado ATUAL (arquivo em assets após patches desta sessão)

| ID | Offset | Byte tipo | Tipo# | x   | y   | w   | O que exibe agora                    |
|----|--------|-----------|-------|-----|-----|-----|--------------------------------------|
| TXT[0] | 0x4E0 | 0x16 | 22  | 202 | 76  | 30  | Segundos (inalterado)               |
| TXT[1] | 0x500 | 0x1B | 27  | 140 | 14  | 44  | Mês abrev. (inalterado)             |
| TXT[2] | 0x520 | 0x0E | 14  |   7 | 128 | 52  | Passos (inalterado)                 |
| TXT[3] | 0x540 | **0x22** | **34** |   7 | 190 | **110** | **BICO** → EXPERIMENTAL tipo=34 — aguardando teste |
| TXT[4] | 0x560 | 0x12 | 18  | 181 | 128 | 52  | Bateria % (inalterado)              |
| TXT[5] | 0x580 | **0x18** | **24** | 181 | 190 | 52  | **BPM frame** → weather.current → "percent°" ex: "27°" ✓ |
| TXT[6] | 0x5A0 | **0x24** | **36** | 124 | 236 | **110** | Barra inferior → EXPERIMENTAL tipo=36 — aguardando teste |

Background: pixels do ícone coração e texto "BPM" **apagados** (preenchidos com 0x0000):
- Ícone coração: x=155–178, y=148–192
- Texto "BPM": x=183–225, y=148–158

Canal de dados enviado pelo código (BambuWeatherMapper):
- `current = s.percent` → TXT[5] tipo=24 → "27°" ✓
- `dia0 = (s.nozzle, 0)` → TXT[3] tipo=34 → ??? (testando se mostra "220°C")
- `dia1 = (remH, remM)` → TXT[6] tipo=36 → ??? (testando se mostra "8/30°C")

---

## Canal de dados weather (código PrintState.kt — BambuWeatherMapper)

```
weather.current  = s.percent        → TXT[5] tipo 24 → "22°"   (% conclusão)
weather.dia0.lo  = s.nozzle         → TXT[3] tipo 33 → "220/65°C" (bico/mesa)
weather.dia0.hi  = s.bed
weather.icon     = percent*15/100   → IMG[7] tipo 6  → barra visual 0–15
```

---

## Como restaurar o estado original rapidamente

Patchear os 4 bytes abaixo e reenviar o face pelo app:

| Endereço | Valor original | Valor atual | Descrição            |
|----------|---------------|-------------|----------------------|
| 0x541    | 0x0D (tipo 13) | 0x22 (tipo 34) | TXT[3] BICO tipo (EXPERIMENTAL) |
| 0x546    | 0x34 (w=52)    | 0x6E (w=110)   | TXT[3] BICO largura|
| 0x581    | 0x05 (tipo 5)  | 0x18 (tipo 24) | TXT[5] BPM tipo → percent ✓ |
| 0x5A1    | 0x21 (tipo 33) | 0x24 (tipo 36) | TXT[6] barra tipo (EXPERIMENTAL) |
| 0x5A6    | 0x38 (w=56)    | 0x6E (w=110)   | TXT[6] barra larg. |

E no código, restaurar em `BambuWeatherMapper.toWeather()`:
```kotlin
// Estado original:
return WatchCommands.weather(WeatherPayload(s.nozzle, city, days))
// dia0 = remH to s.percent  (horas restantes / %)
```

---

## Tipos de texto conhecidos

| Tipo# | Hex  | Nome            | Fonte de dados             | Formato exibido |
|-------|------|-----------------|----------------------------|-----------------|
| 5     | 0x05 | BPM             | Sensor óptico watch        | "91"            |
| 13    | 0x0D | Temperatura °C  | weather.current            | "220°C"         |
| 14    | 0x0E | Passos          | Sensor pedômetro watch     | "3421"          |
| 18    | 0x12 | Bateria %       | Sensor bateria watch       | "87%"           |
| 22    | 0x16 | Segundos        | Relógio interno            | "42"            |
| 24    | 0x18 | Progresso °     | weather.current            | "22°"           |
| 27    | 0x1B | Mês abrev.      | Relógio interno            | "Set"           |
| 33    | 0x21 | H/Min restantes | weather.dia0 (lo/hi)       | "220/65°C"      |
