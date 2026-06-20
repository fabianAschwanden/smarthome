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
