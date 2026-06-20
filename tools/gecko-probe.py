#!/usr/bin/env python3
"""
Konnektivitäts-Probe für die Gecko in.touch2-Steuerung (Pool/Whirlpool).
Sucht das Spa im LAN (Broadcast-Discovery) und – wenn gefunden – verbindet sich,
listet Heizung/Pumpen/Licht/Blower und liest Soll-/Ist-Temperatur.

Aufruf:  .tuya-venv/bin/python tools/gecko-probe.py
Optional gezielt:  .tuya-venv/bin/python tools/gecko-probe.py <spa-ip>

Das Spa muss eingeschaltet und im selben LAN sein. Reines Auslesen – schaltet nichts.
"""
import asyncio
import sys
import uuid

from geckolib import GeckoAsyncSpaMan, GeckoSpaEvent


class Probe(GeckoAsyncSpaMan):
    async def handle_event(self, event: GeckoSpaEvent, **kwargs) -> None:
        # Für die Probe nur grob mitloggen.
        print(f"  [event] {event}")


async def main() -> None:
    target_ip = sys.argv[1] if len(sys.argv) > 1 else None
    async with Probe(str(uuid.uuid4())) as man:
        print("== Discovery (LAN-Broadcast) ==")
        descriptors = await man.async_locate_spas(spa_address=target_ip)
        if not descriptors:
            print("  KEIN Spa gefunden. Spa eingeschaltet? in.touch2 im selben LAN?")
            return
        for d in descriptors:
            print(f"  gefunden: name={d.name!r} ident={d.identifier_as_string} ip={getattr(d,'ipaddress',None)}")

        d = descriptors[0]
        print(f"\n== Verbinde mit {d.name!r} ==")
        await man.async_set_spa_info(d.ipaddress, d.identifier_as_string, d.name)
        await man.async_connect_to_spa(d)
        try:
            await asyncio.wait_for(man.wait_for_facade(), timeout=60)
        except asyncio.TimeoutError:
            print("  Timeout beim Facade-Aufbau.")
        facade = man.facade
        if not facade:
            print("  Facade nicht verfügbar (Verbindung fehlgeschlagen).")
            return

        wh = facade.water_heater
        print("\n== Zustand ==")
        print(f"  Wasser: ist={getattr(wh,'current_temperature',None)} "
              f"soll={getattr(wh,'target_temperature',None)} "
              f"einheit={getattr(wh,'temperature_unit',None)} betrieb={getattr(wh,'current_operation',None)}")
        print(f"  Pumpen:  {[(p.key, p.name, p.mode) for p in facade.pumps]}")
        print(f"  Lichter: {[(l.key, l.name, l.is_on) for l in facade.lights]}")
        print(f"  Blower:  {[(b.key, b.name, getattr(b,'is_on',None)) for b in facade.blowers]}")


if __name__ == "__main__":
    asyncio.run(main())
