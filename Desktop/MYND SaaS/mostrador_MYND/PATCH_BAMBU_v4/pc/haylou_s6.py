#!/usr/bin/env python3
"""
Ferramenta BLE para o HAYLOU Watch S6 (protocolo obtido por engenharia reversa do btsnoop).

Uso (PC com Bluetooth, Python 3.9+, `pip install bleak`):
  python haylou_s6.py scan
  python haylou_s6.py info                      [--addr XX:XX:...]
  python haylou_s6.py notify "Título" "Texto"   [--addr ...]
  python haylou_s6.py face MYND_completo.bin    [--addr ...] [--f5 N] [--f6 N]

ANTES: desligue o Bluetooth do celular (ou force a parada do app Haylou),
senão o relógio fica preso ao celular e não aparece para o PC.
"""
import argparse
import asyncio
import struct
import sys
import time

from bleak import BleakClient, BleakScanner


def u(x):
    return f"0000{x:04x}-0000-1000-8000-00805f9b34fb"


CH_CMD = u(0x8801)    # comandos (write-no-resp + notify)   handle 0x000E
CH_EVT = u(0x8802)    # eventos do relógio (notify)          handle 0x0011
CH_SYNC = u(0x8803)   # sincronização (write + notify)       handle 0x0014
CH_FCTL = u(0x9901)   # controle de arquivo (write + notify) handle 0x0018
CH_FDAT = u(0x9902)   # dados de arquivo (write-no-resp)     handle 0x001B

# campo 5 do comando 9001 = (id << 16) | CRC-16/CCITT-FALSE do arquivo inteiro
# (confirmado nos 4 mostradores do log). O id (12 bits altos) vem do modelo planeta.
DEFAULT_F5_ID = 0x0A80
DEFAULT_F6 = 111149056


def crc16_ccitt_false(data, crc=0xFFFF):
    tbl = _CRC_TBL
    for b in data:
        crc = ((crc << 8) & 0xFFFF) ^ tbl[((crc >> 8) ^ b) & 0xFF]
    return crc


def _mk_tbl():
    t = []
    for i in range(256):
        c = i << 8
        for _ in range(8):
            c = ((c << 1) ^ 0x1021) if c & 0x8000 else (c << 1)
        t.append(c & 0xFFFF)
    return t


_CRC_TBL = _mk_tbl()


def field5(blob, fid=DEFAULT_F5_ID):
    return (fid << 16) | crc16_ccitt_false(blob)
CHUNK = 2048


# ---------------- protobuf mínimo ----------------
def _varint(n):
    out = bytearray()
    while True:
        b = n & 0x7F
        n >>= 7
        out.append(b | (0x80 if n else 0))
        if not n:
            return bytes(out)


def pb_encode(fields):
    """fields: lista de (num, valor) — int => varint, bytes/str => length-delimited"""
    out = bytearray()
    for num, val in fields:
        if isinstance(val, int):
            out += _varint(num << 3) + _varint(val)
        else:
            if isinstance(val, str):
                val = val.encode()
            out += _varint((num << 3) | 2) + _varint(len(val)) + val
    return bytes(out)


def pb_decode(b):
    i, out = 0, []
    def rv():
        nonlocal i
        v = s = 0
        while True:
            x = b[i]; i += 1
            v |= (x & 0x7F) << s; s += 7
            if x < 0x80:
                return v
    while i < len(b):
        k = rv(); f, w = k >> 3, k & 7
        if w == 0:
            out.append((f, rv()))
        elif w == 2:
            n = rv(); out.append((f, bytes(b[i:i + n]))); i += n
        else:
            break
    return out


# ---------------- quadro EA ... EF ----------------
def frame(cmd, payload=b""):
    return b"\xEA" + struct.pack("<HH", len(payload) + 2, cmd) + payload + b"\xEF"


class Deframer:
    def __init__(self):
        self.buf = bytearray()

    def feed(self, data):
        if not self.buf and data[:1] != b"\xEA":
            return []
        self.buf += data
        frames = []
        while len(self.buf) >= 3:
            n = struct.unpack("<H", self.buf[1:3])[0]
            if len(self.buf) < n + 4:
                break
            f = bytes(self.buf[:n + 4])
            self.buf = self.buf[n + 4:]
            cmd = struct.unpack("<H", f[3:5])[0]
            frames.append((cmd, f[5:-1]))
        return frames


class Watch:
    def __init__(self, client):
        self.c = client
        self.q = asyncio.Queue()
        self.defr = {CH_CMD: Deframer(), CH_EVT: Deframer(), CH_SYNC: Deframer(), CH_FCTL: Deframer()}
        self.mtu = 23

    async def start(self):
        self.mtu = getattr(self.c, "mtu_size", 23) or 23
        for ch in (CH_CMD, CH_EVT, CH_SYNC, CH_FCTL):
            await self.c.start_notify(ch, self._mk(ch))

    def _mk(self, ch):
        def cb(_, data):
            for cmd, body in self.defr[ch].feed(bytes(data)):
                try:
                    fields = pb_decode(body)
                except Exception:
                    fields = body.hex()
                self.q.put_nowait((ch, cmd, fields))
        return cb

    async def send(self, ch, cmd, fields=(), response=False):
        data = frame(cmd, pb_encode(fields))
        step = max(20, min(self.mtu - 3, 241))
        for i in range(0, len(data), step):
            await self.c.write_gatt_char(ch, data[i:i + step], response=response)

    async def wait(self, pred, timeout=10.0):
        end = time.monotonic() + timeout
        while True:
            left = end - time.monotonic()
            if left <= 0:
                raise TimeoutError("sem resposta do relógio")
            ch, cmd, fields = await asyncio.wait_for(self.q.get(), left)
            if ch == CH_SYNC and cmd == 0xBBB:  # o relógio pede confirmação: respondemos como o app
                f1 = dict(f for f in fields if isinstance(f, tuple)).get(1, 0)
                await self.send(CH_SYNC, 0xBB8, [(1, 0xBBB), (2, f1)])
                continue
            if pred(ch, cmd, fields):
                return ch, cmd, fields

    async def info(self):
        await self.send(CH_CMD, 0x0002, [(1, 3)])
        _, _, f = await self.wait(lambda ch, c, f: ch == CH_CMD and c == 0x0003)
        d = dict(f)
        return {k: (d[n].decode(errors="replace") if isinstance(d.get(n), bytes) else d.get(n))
                for k, n in (("serial", 1), ("modelo", 2), ("firmware", 3))}

    async def notify(self, title, text, app=110):
        ts = time.strftime("%Y%m%dT%H%M%S")
        await self.send(CH_CMD, 0x0013, [(3, app), (4, title), (5, text), (6, ts)])
        await self.wait(lambda ch, c, f: ch == CH_CMD and c == 0x0001 and dict(f).get(1) == 0x13)

    async def upload_face(self, blob, f5=None, f6=DEFAULT_F6, delay=0.0):
        if f5 is None:
            f5 = field5(blob)
        await self.send(CH_CMD, 0x0034, [(1, 1)])           # modo transferência (visto no log)
        await self.wait(lambda ch, c, f: ch == CH_CMD and c == 0x0001 and dict(f).get(1) == 0x34)
        size = len(blob)
        await self.send(CH_FCTL, 9001, [(1, 7), (3, size), (4, size), (5, f5), (6, f6),
                                          (7, CHUNK), (8, 1), (9, 1)])
        pkt = max(20, min(self.mtu - 3, 241))
        t0 = time.monotonic()
        next_off = 0
        while True:
            _, _, f = await self.wait(lambda ch, c, f: ch == CH_FCTL and c == 9000, timeout=20)
            d = dict(f)
            st = d.get(2)
            if st is None:            # aceito: manda o 1º bloco
                off = 0
            elif st == 3:             # pede o próximo bloco a partir de 'off'
                off = d.get(3, 0)
            elif st == 5:             # terminado com sucesso
                print(f"\rOK: {size} bytes em {time.monotonic() - t0:.1f}s" + " " * 20)
                break
            else:
                motivo = {4: "checksum/verificação do arquivo falhou"}.get(st, "código desconhecido")
                raise RuntimeError(f"relógio recusou ({motivo}): {f}")
            if off >= size:
                continue
            block = blob[off:off + CHUNK]
            for i in range(0, len(block), pkt):
                await self.c.write_gatt_char(CH_FDAT, block[i:i + pkt], response=False)
                if delay:
                    await asyncio.sleep(delay)
            next_off = off + len(block)
            print(f"\r{next_off * 100 // size:3d}%  ({next_off}/{size})", end="", flush=True)
        await self.send(CH_CMD, 0x0034, [])
        try:
            await self.wait(lambda ch, c, f: ch == CH_CMD and c == 0x0001 and dict(f).get(1) == 0x34, 5)
        except TimeoutError:
            pass


async def find(addr):
    if addr:
        return addr
    print("Procurando o relógio (10 s)...")
    devs = await BleakScanner.discover(timeout=10)
    for d in devs:
        if d.name and "haylou" in d.name.lower():
            print(f"Achei: {d.name} {d.address}")
            return d.address
    sys.exit("Relógio não encontrado. Desligue o Bluetooth do celular e tente de novo.")


def menu():
    """Sem argumentos (ex.: botão Run do PyCharm): pergunta o que fazer."""
    import os
    here = os.path.dirname(os.path.abspath(__file__))
    bins = sorted(f for f in os.listdir(here) if f.lower().endswith(".bin"))
    print("\n=== Haylou S6 - MYND ===")
    print("Lembrete: Bluetooth do CELULAR desligado.\n")
    opts = [("Testar conexão (info)", ["info"]),
            ("Mandar notificação de teste", ["notify", "MYND", "Teste do PC"]),
            ("Procurar dispositivos (scan)", ["scan"]),
            ("Enviar clima de TESTE (para a sonda_E)", ["clima-teste"])]
    for b in bins:
        opts.append((f"Enviar mostrador: {b}", ["face", os.path.join(here, b)]))
    for i, (t, _) in enumerate(opts, 1):
        print(f"  {i}. {t}")
    esc = input("\nEscolha o número: ").strip()
    try:
        argv = list(opts[int(esc) - 1][1])
    except (ValueError, IndexError):
        sys.exit("Opção inválida.")
    if argv[0] == "face" and input("Modo lento? (s/N): ").strip().lower() == "s":
        argv.append("--lento")
    return argv


async def main():
    if len(sys.argv) == 1:
        sys.argv += menu()
    ap = argparse.ArgumentParser()
    ap.add_argument("acao", choices=["scan", "info", "notify", "face", "clima-teste"])
    ap.add_argument("args", nargs="*")
    ap.add_argument("--addr")
    ap.add_argument("--f5", type=int, default=None, help="força o campo 5 (padrão: calculado)")
    ap.add_argument("--f6", type=int, default=DEFAULT_F6)
    ap.add_argument("--lento", action="store_true", help="pausa 8 ms entre pacotes (use se travar no meio)")
    a = ap.parse_args()

    if a.acao == "scan":
        for d in await BleakScanner.discover(timeout=10):
            print(d.address, d.name)
        return

    addr = await find(a.addr)
    async with BleakClient(addr, timeout=20) as c:
        w = Watch(c)
        await w.start()
        print("Conectado. MTU:", w.mtu)
        print(await w.info())
        if a.acao == "notify":
            await w.notify(a.args[0] if a.args else "MYND", a.args[1] if len(a.args) > 1 else "Teste do PC")
            print("Notificação enviada.")
        elif a.acao == "clima-teste":
            # valores diferentes em cada campo para descobrir o que cada texto do mostrador usa
            import datetime as _dt
            base = int(_dt.datetime.now().replace(hour=6, minute=0, second=0, microsecond=0).timestamp())
            def day(k, icon, lo, hi, extra):
                sub = ([(1, icon)] if icon else []) + [(3, lo), (4, hi), (5, base + k * 86400), (6, base + k * 86400 + 43200)]
                return (4, pb_encode(sub + extra))
            fields = [(2, 11), (3, "CIDADE TESTE"),
                      day(0, 5, 22, 33, [(8, 66), (9, 77), (10, 88), (11, 44), (15, 55)]),
                      day(1, 7, 91, 92, [(11, 93), (15, 94)]),
                      day(2, 9, 81, 82, [(11, 83), (15, 84)])]
            await w.send(CH_CMD, 0x0014, fields)
            print("Clima de teste enviado: atual=11 | dia0 min=22 max=33 ícone=5 f8=66 f9=77 f10=88 umid=44 uv=55 | dia1 91/92 | dia2 81/82")
        elif a.acao == "face":
            import os
            path = a.args[0]
            if not os.path.exists(path):
                path = os.path.join(os.path.dirname(os.path.abspath(__file__)), path)
            blob = open(path, "rb").read()
            if blob[14:16] != b"JL":
                sys.exit("Arquivo não parece um mostrador JL.")
            await w.upload_face(blob, a.f5, a.f6, 0.008 if a.lento else 0.0)


if __name__ == "__main__":
    try:
        asyncio.run(main())
    except Exception as e:
        print(f"\nERRO: {type(e).__name__}: {e}")
    if sys.stdin.isatty() or "PYCHARM_HOSTED" in __import__("os").environ:
        input("\nEnter para fechar...")
