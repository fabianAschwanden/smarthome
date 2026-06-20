#!/usr/bin/env python3
"""
Tuya-Sidecar – kleiner lokaler HTTP-Dienst, der Tuya-Geräte über tinytuya liest.

Hintergrund: Das Tuya-LAN-Protokoll 3.3 setzt die App in reinem Java um. Für 3.4/3.5
(Session-Key-Handshake) ist tinytuya die erprobte Referenz; dieser Sidecar kapselt sie.
Der Java-Adapter ruft ihn nur für 3.4/3.5-Geräte:

    GET /read?id=<deviceId>&key=<localKey>&ip=<ip>&version=3.4
    -> 200 {"dps": {"1": 251, "2": 51, ...}}   |   503 {"error": "..."}

Start (im venv mit tinytuya):
    .tuya-venv/bin/python tools/tuya-sidecar/sidecar.py            # Port 8765
    PORT=9000 .tuya-venv/bin/python tools/tuya-sidecar/sidecar.py

Bindet nur an localhost – wird vom selben Host (Quarkus) aufgerufen.
"""
import json
import os
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse, parse_qs

import tinytuya

PORT = int(os.environ.get("PORT", "8765"))
# Standard: nur localhost (sicher beim lokalen Start). Im Container HOST=0.0.0.0 setzen,
# damit das Backend (anderer Container/Host) den Sidecar erreicht.
HOST = os.environ.get("HOST", "127.0.0.1")
TIMEOUT = float(os.environ.get("TUYA_TIMEOUT", "4"))


def read_device(device_id: str, local_key: str, ip: str, version: str) -> dict:
    d = tinytuya.Device(device_id, ip, local_key)
    d.set_version(float(version))
    d.set_socketTimeout(TIMEOUT)
    status = d.status()
    if not isinstance(status, dict) or "dps" not in status:
        raise RuntimeError(str(status))
    return status["dps"]


def _snapshot(dev) -> dict:
    return {
        "power": dev.power_state,
        "mode": dev.operational_mode.name if dev.operational_mode else None,
        "target": dev.target_temperature,
        "current": dev.indoor_temperature,
        "online": dev.online,
    }


def _run_with_retry(coro_factory, attempts: int = 3, pause: float = 1.5):
    """
    Führt eine asyncio-Coroutine aus und wiederholt bei Verbindungsfehlern.
    Midea-Geräte erlauben nur EINE LAN-Session und werfen die Verbindung gern ab
    ("connection reset" / "no response"), wenn kurz zuvor eine andere bestand. Eine
    kurze Pause + Wiederholung fängt das zuverlässig ab.
    """
    import asyncio

    last = None
    for i in range(attempts):
        try:
            return asyncio.run(coro_factory())
        except Exception as e:  # noqa: BLE001 – jeder LAN-Fehler ist retrybar
            last = e
            if i < attempts - 1:
                time.sleep(pause)
    raise last


def climate_read(q) -> dict:
    """Liest den Zustand der Midea/NetHome-Plus-Klimaanlage über msmart-ng."""
    from msmart.device import AirConditioner as AC

    async def run():
        dev = AC(ip=q["ip"][0], port=6444, device_id=int(q["id"][0]))
        await dev.authenticate(q["token"][0], q["key"][0])
        await dev.refresh()
        return _snapshot(dev)

    return _run_with_retry(run)


def climate_control(q) -> dict:
    """Schaltet/setzt die Klimaanlage (power/mode/target) über msmart-ng."""
    from msmart.device import AirConditioner as AC

    async def run():
        dev = AC(ip=q["ip"][0], port=6444, device_id=int(q["id"][0]))
        await dev.authenticate(q["token"][0], q["key"][0])
        await dev.refresh()
        if "power" in q:
            dev.power_state = q["power"][0].lower() == "true"
        if "mode" in q:
            dev.operational_mode = AC.OperationalMode[q["mode"][0].upper()]
        if "target" in q:
            dev.target_temperature = float(q["target"][0])
        await dev.apply()
        return _snapshot(dev)

    return _run_with_retry(run)


class Handler(BaseHTTPRequestHandler):
    def do_GET(self):  # noqa: N802
        parsed = urlparse(self.path)
        q = parse_qs(parsed.query)
        try:
            if parsed.path == "/read":
                dps = read_device(q["id"][0], q["key"][0], q["ip"][0], q.get("version", ["3.4"])[0])
                self._send(200, {"dps": dps})
            elif parsed.path == "/climate/read":
                self._send(200, climate_read(q))
            elif parsed.path == "/climate/control":
                self._send(200, climate_control(q))
            else:
                self._send(404, {"error": "unknown path"})
        except KeyError as e:
            self._send(400, {"error": f"missing param {e}"})
        except Exception as e:  # noqa: BLE001 – Sidecar meldet jeden Fehler als 503
            self._send(503, {"error": str(e)})

    def _send(self, code: int, body: dict):
        payload = json.dumps(body).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)

    def log_message(self, *args):  # Konsole ruhig halten
        pass


if __name__ == "__main__":
    server = ThreadingHTTPServer((HOST, PORT), Handler)
    print(f"Tuya-Sidecar läuft auf http://{HOST}:{PORT}  (GET /read?id=&key=&ip=&version=)")
    server.serve_forever()
