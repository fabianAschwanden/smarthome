#!/usr/bin/env python3
"""
Liest die rohen Tuya-Datenpunkte (dps) der konfigurierten Storen aus, um zu sehen,
welcher dp die echte Position/Kippung trägt. Secrets kommen aus der gitignored
config/application.properties bzw. den dort referenzierten Env-Variablen – nichts
landet auf der Kommandozeile.
"""
import os
import re
import sys

import tinytuya

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CFG = os.path.join(ROOT, "config", "application.properties")


def props():
    out = {}
    with open(CFG) as f:
        for line in f:
            line = line.strip()
            if line and not line.startswith("#") and "=" in line:
                k, v = line.split("=", 1)
                out[k.strip()] = v.strip()
    return out


def resolve(value):
    """Löst ${ENV:default} auf."""
    m = re.fullmatch(r"\$\{([^:}]+)(?::([^}]*))?\}", value or "")
    if m:
        return os.environ.get(m.group(1), m.group(2) or "")
    return value


def main():
    p = props()

    def val(base, field, default=""):
        # %dev-Profil hat Vorrang (dort liegen die echten Werte), sonst Basis-Key.
        return p.get("%dev." + base + field, p.get(base + field, default))

    for i in (0, 1):
        base = f"cover.devices[{i}]."
        name = val(base, "name", val(base, "id", f"#{i}"))
        dev_id = resolve(val(base, "device-id"))
        key = resolve(val(base, "local-key"))
        ip = resolve(val(base, "address"))
        ver = float(val(base, "version", "3.3"))
        if not dev_id or not key or not ip or ip == "0.0.0.0":
            print(f"[{name}] unvollständig konfiguriert (id/key/ip) – übersprungen")
            continue
        d = tinytuya.Device(dev_id, ip, key)
        d.set_version(ver)
        d.set_socketTimeout(5)
        status = d.status()
        print(f"[{name}] {ip} -> {status.get('dps', status)}")


if __name__ == "__main__":
    main()
