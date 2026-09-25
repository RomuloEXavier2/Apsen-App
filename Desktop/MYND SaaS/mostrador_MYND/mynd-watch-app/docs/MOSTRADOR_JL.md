# Formato do mostrador (.bin "JL") — Haylou Watch S6 (240×296)

Gerador de referência: `reference/python/jlface.py` (reconstrói os 4 mostradores oficiais byte a byte).

## Layout (offsets absolutos)

| Offset | Conteúdo |
|---|---|
| 0x000 | 8 bytes 0 + ID ascii 8 bytes (ex. `01304\0JL`) |
| 0x08C | `0x01` + 1 byte por registro: `0x06` = elemento imagem, `0x05` = campo de texto (máx ~39) |
| 0x0B4 | `01 aa/00 …` ; 0x0BC u32 = 0x4EA |
| 0x0C0 | tabela: registros de imagem (0x84 bytes) seguidos dos de texto (0x20 bytes), até 0x5E8 |
| 0x5E8 | prévia 156×214 RGB565 big-endian (sem cabeçalho) |
| 0x10AB8 | cabeçalho 4 B + fundo 240×296 RGB565 BE |
| 0x335BC | recursos (imagens); offset na tabela = posição no arquivo − 0x20B4 |

Espaço da tabela: 1320 bytes → nº_imagens×132 + nº_textos×32 ≤ 1320.

### Registro de imagem (0x84)
`u8 06 | u8 tipo | u16 x | u16 y | u16 0 | u32 offsets[31]` (um offset por imagem do conjunto)

### Registro de texto (0x20) — desenhado pelo firmware
`u8 05 | u8 tipo | u16 x | u16 y | u8 largura | u8 R | u8 G | u8 B | u8 alinhamento | u8 01 | "1000200000" + zeros`
Alinhamento: 0/1 = esquerda, 2 = centro, 3 = direita. Fonte única (~20 px), não dá para mudar tamanho.

### Cabeçalho de imagem (u32 LE)
`cf:5 | extra:5 | w:11 | h:11`
- cf 4 = RGB565 BE (2 B/px) · cf 5 = A8 + RGB565 BE (3 B/px, cor pré-multiplicada)
- **Regra crítica:** cada linha precisa ter nº de bytes múltiplo de 4 (w·3 ou w·2). Largura errada = cores trocadas/serrilhado.

## Tipos de ELEMENTO DE IMAGEM conhecidos
1 hora-dezena · 2 hora-unidade · 3 min-dezena · 4 min-unidade · 6 ícone do clima (0..15) · 8/9 mês dezena/unidade ·
10/11 dia dezena/unidade · 23 bateria (6 níveis) · 24 AM/PM · 31 dia da semana (7, DOM..SÁB começa em domingo)
Sondas de imagem D1–D3 (tipos 0,5,7,12–22,25–30,32–41) prontas em reference/sondas, ainda não rodadas.

## Tipos de CAMPO DE TEXTO (validados com as sondas A/B/C)
| tipo | mostra | | tipo | mostra |
|---|---|---|---|---|
| 01/02/03/04 | dígitos da hora/min | | 17 | calorias |
| 05 | batimento | | 18 | bateria "69%" · 21 = "69" |
| 06 | ano | | 19 | **texto fixo** (os bytes do próprio registro) |
| 07 | mês "09" | | 22 | **segundos** |
| 08 | dia | | 23 | distância km "0.21" |
| 09/28/29 | THU / Thu / THURSDAY | | 25/26/27 | Sep / September / SEP |
| 13 / 24 | temp "15°C" / "15°" | | 30 / 32 | minutos / hora (2 dígitos) |
| 14 | passos | | 31 | AM/PM |
| 16 | "0" (?) | | 33 | mín/máx do clima "15/22" |
| | | | 34 | descrição do tempo |
Tipos 0, 10–12, 15, 20, 35–71: inexistentes (firmware escreve "Text").
