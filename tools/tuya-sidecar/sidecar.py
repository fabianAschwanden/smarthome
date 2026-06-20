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


# ---------------------------------------------------------------------------
# Gecko in.touch2 (Pool / Whirlpool) über geckolib.
# ---------------------------------------------------------------------------

async def _gecko_facade(man, ip, ident, name):
    """Verbindet mit einem Spa und liefert die Facade (oder None)."""
    import asyncio as _asyncio
    await man.async_set_spa_info(ip, ident, name)
    # Discovery liefert den Descriptor; gezielt per IP verbinden.
    descriptors = await man.async_locate_spas(spa_address=ip)
    target = None
    for d in descriptors or []:
        if ident and d.identifier_as_string == ident:
            target = d
            break
        target = target or d
    if target is None:
        return None
    await man.async_connect_to_spa(target)
    try:
        await _asyncio.wait_for(man.wait_for_facade(), timeout=60)
    except _asyncio.TimeoutError:
        pass
    # WaterCare-Modus wird kurz nach dem Facade-Aufbau asynchron nachgeladen.
    facade = man.facade
    if facade is not None:
        for _ in range(10):
            wc = getattr(facade, "water_care", None)
            if wc is not None and getattr(wc, "state", None) not in (None, "Unknown"):
                break
            await _asyncio.sleep(0.5)
    return facade


def _watercare_mode(facade):
    """Aktueller WaterCare-Modus als Name (z. B. 'Standard'); None wenn unbekannt."""
    wc = getattr(facade, "water_care", None)
    if wc is None:
        return None
    # geckolib füllt 'state' (Name) erst kurz nach dem Connect -> 'state' bevorzugen,
    # auf den Modus-Index ('mode') zurückfallen.
    state = getattr(wc, "state", None)
    if state and state != "Unknown":
        return state
    idx = getattr(wc, "mode", None)
    modes = getattr(wc, "modes", None) or []
    if isinstance(idx, int) and 0 <= idx < len(modes):
        return modes[idx]
    return None


def _gecko_snapshot(facade) -> dict:
    wh = facade.water_heater
    return {
        "current": _num(getattr(wh, "current_temperature", None)),
        "target": _num(getattr(wh, "target_temperature", None)),
        "operation": getattr(wh, "current_operation", None),
        "pumps": {p.key: (p.mode != "OFF") for p in facade.pumps},
        "lights": {l.key: bool(l.is_on) for l in facade.lights},
        "watercare": _watercare_mode(facade),
        "online": True,
    }


def _num(v):
    try:
        return float(v)
    except (TypeError, ValueError):
        return None


# geckolib hält internen Modul-/Objekt-Zustand, der an SEINEN Event-Loop gebunden
# ist. Darum betreibt der Sidecar EINEN langlebigen Loop in einem Hintergrund-Thread;
# alle Gecko-Aufrufe laufen über genau diesen Loop (run_coroutine_threadsafe).
import asyncio as _aio
import threading as _threading

_gecko_loop = None
_gecko_lock = _threading.Lock()


def _ensure_gecko_loop():
    global _gecko_loop
    if _gecko_loop is None:
        _gecko_loop = _aio.new_event_loop()
        t = _threading.Thread(target=_gecko_loop.run_forever, name="gecko-loop", daemon=True)
        t.start()
    return _gecko_loop


def _gecko_run(ip, ident, name, action):
    """Baut einen SpaMan, verbindet, führt action(facade) aus und liefert den Snapshot."""
    import uuid
    from geckolib import GeckoAsyncSpaMan, GeckoSpaEvent

    class _Man(GeckoAsyncSpaMan):
        async def handle_event(self, event: GeckoSpaEvent, **kwargs) -> None:
            pass

    async def run():
        async with _Man(str(uuid.uuid4())) as man:
            facade = await _gecko_facade(man, ip, ident, name)
            if not facade:
                raise RuntimeError("Spa nicht erreichbar (keine Facade)")
            if action:
                await action(facade)
            return _gecko_snapshot(facade)

    # Gecko-Aufrufe serialisieren (ein Spa-Protokoll zur Zeit) und über den festen Loop fahren.
    with _gecko_lock:
        loop = _ensure_gecko_loop()
        return _aio.run_coroutine_threadsafe(run(), loop).result(timeout=120)


def spa_read(q) -> dict:
    """Liest den Zustand eines Gecko-Spas (Temperatur, Pumpen, Lichter)."""
    return _gecko_run(q["ip"][0], q.get("ident", [None])[0], q.get("name", ["Spa"])[0], None)


def spa_control(q) -> dict:
    """Setzt Soll-Temperatur / schaltet eine Pumpe oder ein Licht (per key)."""
    async def action(facade):
        if "target" in q:
            await facade.water_heater.async_set_target_temperature(float(q["target"][0]))
        if "pump" in q:  # pump=<key>, on=true|false
            on = q.get("on", ["true"])[0].lower() == "true"
            for p in facade.pumps:
                if p.key == q["pump"][0]:
                    await (p.async_turn_on() if on else p.async_turn_off())
        if "light" in q:  # light=<key>, on=true|false
            on = q.get("on", ["true"])[0].lower() == "true"
            for l in facade.lights:
                if l.key == q["light"][0]:
                    await (l.async_turn_on() if on else l.async_turn_off())
        if "watercare" in q:  # watercare=<Modusname>, z. B. 'Standard'
            await facade.water_care.async_set_mode(q["watercare"][0])

    return _gecko_run(q["ip"][0], q.get("ident", [None])[0], q.get("name", ["Spa"])[0], action)


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
            elif parsed.path == "/spa/read":
                self._send(200, spa_read(q))
            elif parsed.path == "/spa/control":
                self._send(200, spa_control(q))
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
