#!/usr/bin/env python3
"""
Bambu Lab  ->  HAYLOU Watch S6   (gateway MYND)

Lê o status da impressora pela rede local (MQTT) e manda para o relógio:
  * mostrador MYND_HUD_BAMBU: barra de progresso, "% / minutos restantes", temperatura do bico
  * tela de Clima do relógio: linhas com progresso, mesa, camada...
  * notificações: início, 25/50/75/90 %, pausa, erro, fim

Uso (PC na mesma rede Wi-Fi da impressora, Bluetooth do celular DESLIGADO):
  pip install bleak paho-mqtt
  python bambu_watch.py --ip 192.168.0.50 --serial 01P00A000000000 --code 12345678
  (ou rode sem argumentos: ele pergunta e salva em bambu_config.json)

Onde achar os dados na impressora:
  IP e código de acesso: tela da impressora > Configurações > Rede/WLAN (Access Code)
  Serial: tela > Configurações > Dispositivo, ou no Bambu Studio/Handy
"""
import argparse
import asyncio
import json
import os
import ssl
import sys
import threading
import time
from datetime import datetime

import paho.mqtt.client as mqtt
from bleak import BleakClient

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import haylou_s6 as H  # noqa: E402

CFG = os.path.join(HERE, "bambu_config.json")
MIN_INTERVAL = 15      # s entre atualizações do "clima" (poupa bateria do relógio)
NOTIFY_APP_ID = 110    # ícone da notificação (110 = o que o app usou no log)

NOZZLE_LEVELS = [0, 60, 100, 150, 180, 190, 200, 205, 210, 215, 220, 225, 230, 240, 250, 260]

STATE_PT = {"IDLE": "Parada", "PREPARE": "Preparando", "RUNNING": "Imprimindo", "PAUSE": "Pausada",
            "FINISH": "Concluída", "FAILED": "Falhou", "SLICING": "Fatiando"}


# ---------------- comando de clima (0x0014) ----------------
def weather_payload(current, city, days):
    """current: int (vira o 'XX°C' do mostrador, campo de texto 13)
    city: texto; days: lista de dicts {icon, lo, hi, rise, set, extra...} (até 7)"""
    fields = [(2, int(current)), (3, city)]
    for d in days:
        sub = []
        if d.get("icon"):
            sub.append((1, int(d["icon"])))
        sub += [(3, int(d["lo"])), (4, int(d["hi"])), (5, int(d["rise"])), (6, int(d["set"]))]
        for k in (8, 9, 10, 11, 15):
            if k in d:
                sub.append((k, int(d[k])))
        fields.append((4, H.pb_encode(sub)))
    return fields


def bambu_weather(st):
    pct = int(st.get("mc_percent") or 0)
    rem = int(st.get("mc_remaining_time") or 0)          # minutos
    noz = int(round(float(st.get("nozzle_temper") or 0)))
    bed = int(round(float(st.get("bed_temper") or 0)))
    bed_t = int(round(float(st.get("bed_target_temper") or 0)))
    noz_t = int(round(float(st.get("nozzle_target_temper") or 0)))
    lay, tot = int(st.get("layer_num") or 0), int(st.get("total_layer_num") or 0)
    stt = STATE_PT.get(st.get("gcode_state", ""), st.get("gcode_state", "?"))
    # v4: o ícone (0..15) mostra a temperatura do bico no mostrador (NOZZLE_LEVELS igual ao hud.py)
    icon = 0 if noz < 45 else min(range(1, 16), key=lambda i: abs(NOZZLE_LEVELS[i] - noz))
    base = datetime.now().replace(hour=6, minute=0, second=0, microsecond=0).timestamp()
    rows = [(icon, rem // 60, rem % 60),   # dia 1: horas/minutos restantes (texto 33 = '13/51') (icon, noz, noz_t), (icon, bed, bed_t), (icon, lay, tot),
            (icon, pct, 100), (icon, pct, 100), (icon, pct, 100)]
    days = []
    for k, (i, a, b) in enumerate(rows):
        d = dict(icon=i, lo=a, hi=b, rise=base + k * 86400, set=base + k * 86400 + 12 * 3600)
        d[11] = pct  # "umidade"
        days.append(d)
    city = f"Bambu {pct}% {stt}"[:24]
    return weather_payload(min(pct, 99), city, days)   # v3: 'atual' = % da impressão (texto 24)


# ---------------- MQTT (thread) ----------------
class Printer:
    def __init__(self, ip, serial, code):
        self.ip, self.serial, self.code = ip, serial, code
        self.state = {}
        self.lock = threading.Lock()
        self.version = 0
        self.connected = False
        try:
            c = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2)
        except AttributeError:  # paho < 2.0
            c = mqtt.Client()
        c.username_pw_set("bblp", code)
        c.tls_set(cert_reqs=ssl.CERT_NONE)
        c.tls_insecure_set(True)
        c.on_connect = self._on_connect
        c.on_message = self._on_msg
        c.on_disconnect = lambda *a, **k: setattr(self, "connected", False)
        self.c = c

    def start(self):
        self.c.connect_async(self.ip, 8883, 60)
        self.c.loop_start()

    def _on_connect(self, c, u, flags, rc, *a):
        ok = (rc == 0) if isinstance(rc, int) else not rc.is_failure
        print(f"[bambu] MQTT {'conectado' if ok else f'falhou ({rc}) - confira IP/código'}")
        if ok:
            self.connected = True
            c.subscribe(f"device/{self.serial}/report")
            self.pushall()

    def pushall(self):
        self.c.publish(f"device/{self.serial}/request",
                       json.dumps({"pushing": {"sequence_id": "0", "command": "pushall"}}))

    def _on_msg(self, c, u, msg):
        try:
            p = json.loads(msg.payload).get("print")
        except Exception:
            return
        if not isinstance(p, dict):
            return
        with self.lock:
            self.state.update(p)      # P1/A1 mandam só o que mudou
            self.version += 1

    def snapshot(self):
        with self.lock:
            return dict(self.state), self.version


# ---------------- lógica de eventos ----------------
class Events:
    def __init__(self):
        self.last_state = None
        self.last_mile = -1

    def check(self, st):
        out = []
        gs = st.get("gcode_state")
        pct = int(st.get("mc_percent") or 0)
        if gs and self.last_state is None:  # primeira leitura: não dispara alertas antigos
            self.last_mile = max([m for m in (25, 50, 75, 90) if pct >= m] or [-1])
            self.last_state = gs
            return out
        name = (st.get("subtask_name") or "").replace(".gcode", "").replace(".3mf", "")[:40]
        if gs and gs != self.last_state and self.last_state is not None:
            if gs == "RUNNING" and self.last_state in ("IDLE", "PREPARE", "FINISH", "FAILED"):
                out.append(("Bambu: iniciou", name or "Impressão iniciada"))
                self.last_mile = -1
            elif gs == "PAUSE":
                out.append(("Bambu: PAUSADA", f"{name} em {pct}%"))
            elif gs == "FAILED":
                out.append(("Bambu: FALHOU", f"{name} em {pct}%"))
            elif gs == "FINISH":
                out.append(("Bambu: concluída", name or "Impressão pronta"))
        if gs == "RUNNING":
            for m in (25, 50, 75, 90):
                if pct >= m > self.last_mile:
                    self.last_mile = m
                    rem = int(st.get("mc_remaining_time") or 0)
                    out.append((f"Bambu {m}%", f"Faltam {rem // 60}h{rem % 60:02d}"))
        if gs:
            self.last_state = gs
        return out


async def run(pr):
    ev = Events()
    while True:
        try:
            addr = await H.find(None)
            async with BleakClient(addr, timeout=20) as c:
                w = H.Watch(c)
                await w.start()
                print("[relógio] conectado:", await w.info())
                sent_ver, last_push = -1, 0.0
                while c.is_connected:
                    st, ver = pr.snapshot()
                    for title, text in ev.check(st) if st else []:
                        print(f"[relógio] notificação: {title} | {text}")
                        await w.notify(title, text, app=NOTIFY_APP_ID)
                    if st and ver != sent_ver and time.monotonic() - last_push >= MIN_INTERVAL:
                        await w.send(H.CH_CMD, 0x0014, bambu_weather(st))
                        try:
                            await w.wait(lambda ch, cm, f: ch == H.CH_CMD and cm == 1 and dict(f).get(1) == 0x14, 5)
                        except TimeoutError:
                            pass
                        sent_ver, last_push = ver, time.monotonic()
                        print(f"[relógio] {datetime.now():%H:%M:%S} {st.get('gcode_state')} "
                              f"{st.get('mc_percent')}%  bico {st.get('nozzle_temper')}  "
                              f"restam {st.get('mc_remaining_time')} min")
                    if int(time.monotonic()) % 300 == 0:
                        pr.pushall()
                    await asyncio.sleep(1)
        except Exception as e:
            print(f"[relógio] conexão perdida ({type(e).__name__}: {e}). Tentando de novo em 10 s...")
            await asyncio.sleep(10)


def load_cfg(a):
    cfg = {}
    if os.path.exists(CFG):
        cfg = json.load(open(CFG))
    for k in ("ip", "serial", "code"):
        v = getattr(a, k) or cfg.get(k)
        if not v:
            v = input({"ip": "IP da impressora: ", "serial": "Serial da impressora: ",
                       "code": "Código de acesso (Access Code): "}[k]).strip()
        cfg[k] = v
    json.dump(cfg, open(CFG, "w"), indent=1)
    return cfg


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--ip"); ap.add_argument("--serial"); ap.add_argument("--code")
    a = ap.parse_args()
    cfg = load_cfg(a)
    pr = Printer(cfg["ip"], cfg["serial"], cfg["code"])
    pr.start()
    print("Rodando. Ctrl+C para sair.  (Bluetooth do celular desligado)")
    try:
        asyncio.run(run(pr))
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    main()
