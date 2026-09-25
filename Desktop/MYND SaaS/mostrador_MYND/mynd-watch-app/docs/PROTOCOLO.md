# Protocolo BLE — HAYLOU Watch S6

Modelo `HAYLOU Watch S6`, firmware `V25.26.001.2.1.9.1.26`, chip provável JieLi.
Tudo abaixo veio do btsnoop do app oficial e foi validado no relógio real (Python em `reference/python`).

## GATT

| Serviço | Característica | Handle | Props | Uso |
|---|---|---|---|---|
| 0x8800 | 0x8801 | 0x000E | write-no-resp + notify | comandos / respostas |
| 0x8800 | 0x8802 | 0x0011 | notify | eventos espontâneos do relógio |
| 0x8800 | 0x8803 | 0x0014 | write-no-resp + notify | sync (0x0BBB → responder 0x0BB8) |
| 0x9900 | 0x9901 | 0x0018 | write-no-resp + notify | controle de arquivo |
| 0x9900 | 0x9902 | 0x001B | write-no-resp | dados de arquivo |

- UUID completo: `0000XXXX-0000-1000-8000-00805f9b34fb`.
- Sem pareamento/criptografia (nenhum SMP no log).
- Ao conectar: habilitar notify (CCCD `01 00`) em 8801, 8802, 8803, 9901.
- MTU: Android pede 247 → pacote útil 244. No PC Windows veio 23 (funciona, só mais lento).
- **O relógio aceita UMA conexão.** App Haylou aberto = nosso app não conecta.

## Quadro

```
EA | len u16 LE | cmd u16 LE | payload protobuf | EF        len = 2 + tamanho do payload
```
Quadros maiores que o MTU são fatiados em escritas consecutivas (o 1º pedaço começa com EA).

## Comandos conhecidos (canal 8801)

| cmd | direção | payload | significado |
|---|---|---|---|
| 0x0001 | ← | {1: cmd} | ACK genérico |
| 0x0002 | → | {1: 3} | pedir info → resposta 0x0003 |
| 0x0003 | ← | {1 serial, 2 modelo, 3 firmware, 5 ?, 7 MAC, 8,9,10…} | info |
| 0x0002 | → | {1: 9} | pedir bateria → 0x0009 {2: %} |
| 0x0004 | → | {2,3,4,6} | perfil do usuário (idade/altura/peso/sexo – mapear) |
| 0x0005 | → | {1 ano,2 mês,3 dia,4 h,5 min,6 s,7:1,8:2,9: dia-semana seg=0} | acertar hora |
| 0x000A, 0x000B, 0x0011, 0x0032, 0x0033, 0x001D | → | vários | handshake do app oficial (não necessários até agora) |
| 0x0013 | → | {3 appId, 4 título, 5 texto, 6 "yyyyMMddTHHmmss"} | notificação (appId 110 = usado p/ WhatsApp) |
| 0x0014 | → | {2 temp atual, 3 cidade, 4×N dia{1 ícone,3 mín,4 máx,5 nascer,6 pôr,8,9,10,11 umid,15 UV}} | clima |
| 0x0034 | → | {1:1} / {} | entra/sai do modo transferência |

Eventos (8802, ←): 0x07D1 {1:8}, 0x07D6 {1:244}, 0x07D7 {5:76, 8:76} — significado ainda não mapeado (bateria/passos?).

## Envio de arquivo (mostrador) — canais 9901/9902

```
→ 34 {1:1}                                   (8801)
→ 9001 {1:7, 3:size, 4:size, 5:F5, 6:111149056, 7:2048, 8:1, 9:1}   (9901)
← 9000 {1:9001}                  aceito → mande bloco 0
→ 2048 bytes em pacotes de MTU-3 (9902, write-no-resp)
← 9000 {1:9001, 2:3, 3:offset}   mande a partir de offset
   ... repete ...
← 9000 {1:9001, 2:5}             sucesso
← 9000 {1:9001, 2:4}             RECUSADO (checksum/validação)
→ 34 {}                                      (8801)
```
**F5 = (0x0A80 << 16) | CRC-16/CCITT-FALSE(arquivo inteiro)** — validado nos 4 mostradores oficiais.
Os 12 bits altos parecem um ID; 0x0A80 (do mostrador "planeta") é aceito para qualquer arquivo.

## Clima como canal de dados

O mostrador mostra dados do último comando 0x0014:
- texto tipo 13 → `temp atual` + "°C"   · tipo 24 → `temp atual` + "°"
- texto tipo 33 → `dia0.mín/dia0.máx` (sempre "a/b", inteiros, sem zero à esquerda)
- texto tipo 34 → descrição derivada do ícone
- imagem tipo 6 → índice = `dia0.ícone` (0..15)
O app MYND usa isso para a Bambu (ver BambuWeatherMapper).
