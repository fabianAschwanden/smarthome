# Tuya-Sidecar (3.4/3.5)

Kleiner lokaler HTTP-Dienst, der Tuya-Geräte über **tinytuya** liest. Die App spricht
das Tuya-LAN-Protokoll **3.3** in reinem Java; für **3.4/3.5** (Session-Key-Handshake)
ist tinytuya die erprobte Referenz, die dieser Sidecar kapselt.

Der Java-Sensor-Adapter (`LocalTuyaSensorDevice`) ruft den Sidecar **nur für
3.4/3.5-Geräte**; 3.3-Geräte (Schalter, Storen) laufen weiter direkt über Java.

## Start

```bash
# einmalig: venv mit tinytuya (liegt unter .tuya-venv, gitignored)
python3 -m venv .tuya-venv && .tuya-venv/bin/pip install tinytuya

# Sidecar starten (Port 8765, nur localhost)
.tuya-venv/bin/python tools/tuya-sidecar/sidecar.py
```

## API

```
GET /read?id=<deviceId>&key=<localKey>&ip=<ip>&version=3.4
-> 200 {"dps": {"1": 250, "2": 51}}      # 1=Temp×10, 2=Feuchte
-> 503 {"error": "..."}                   # nicht erreichbar
```

## Konfiguration in der App

`tuya.sidecar.url` (Default `http://127.0.0.1:8765`). Geräte mit `version=3.4`/`3.5`
werden automatisch über den Sidecar gelesen.

## Hinweis

Der Sidecar muss laufen, damit 3.4/3.5-Geräte (z. B. der Innensensor) online sind.
Ist er aus, erscheinen diese Geräte „offline" – 3.3-Geräte sind nicht betroffen.
Mittelfristig kann der 3.4-Handshake in `support.tuya.Tuya34Session` fertig
implementiert werden, dann entfällt der Sidecar.

## Betriebsfalle: das Socket-Leck (August 2026)

Der Sidecar wurde alle vier bis sechs Stunden von seinem Speicherlimit (256 MiB)
erschlagen. Sichtbar war das nur im Kernel-Log des Hosts
(`Memory cgroup out of memory: Killed process … (python)`), nach aussen als kurzzeitig
ausfallende Tuya-, Gecko- und Klima-Geräte.

**Ursache:** `async with GeckoAsyncSpaMan(...)` ruft nur `__aexit__`, und das beendet
lediglich die Tasks. Die UDP-Verbindungen zum Spa blieben offen – rund zwei Sockets je
Abfrage, bei einer Abfrage alle paar Sekunden. Erst `async_reset()` ruft
`facade.disconnect()` und `spa.disconnect()`.

**Diagnose fürs nächste Mal:**

```bash
kubectl -n smarthome exec deploy/sidecar -- sh -c 'ls /proc/1/fd | wc -l'   # Sockets
kubectl -n smarthome exec deploy/sidecar -- grep VmRSS /proc/1/status       # Speicher
```

Bleiben beide Zahlen über Minuten stabil, ist alles in Ordnung. Wachsen sie stetig, hängt
irgendwo eine Verbindung.

**Behoben** durch `async_reset()` in einem `finally` – und zwar *ausserhalb* des
`wait_for`: Läuft die Abfrage in den Timeout, wird die innere Coroutine abgebrochen, und
ein Aufräumen dort käme nie zum Zug. Ausgerechnet beim nicht erreichbaren Spa bliebe der
Socket dann liegen. Beide Fälle deckt `test_gecko_cleanup.py` ab.
