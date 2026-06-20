# Spec – Use Case 8: Umweltsensor (Temperatur/Feuchte)

Status: v1.0 (umgesetzt) · Datum: 2026-06-20 · Plattform: Java 25 / Quarkus (Hexagonal + DDD)

## 1. Zweck & Scope

Innen-Temperatur und -Feuchte eines Tuya-Sensors **nur lesend** anzeigen
(Dashboard-Kachel „Innentemperatur"). Kein Steuern.

## 2. Anbindung

Tuya-Sensor über das lokale LAN-Protokoll (siehe `docs/tuya/SPEC.md`). Standard-dps:
`temperature-dp=1` (Rohwert ÷ `temperature-scale`, meist 10 → °C), `humidity-dp=2`
(%). Protokoll je Gerät (`version`); 3.4/3.5 über den Sidecar. IP findet
`TuyaDiscovery` automatisch, kann per `address` vorbelegt werden.

- `%dev`/`%test`: Mock (plausible Werte).
- Echtbetrieb (`smarthome.real-devices=true`): `LocalTuyaSensorDevice`.

## 3. API (REST)

| Methode | Pfad           | Antwort                                            |
|---------|----------------|----------------------------------------------------|
| GET     | `/api/sensors` | Liste (id, name, room, temperature, humidity, online) |

`temperature` als °C; `humidity` in %, `-1`/Wert < -100 = unbekannt; `online=false`
wenn der Sensor nicht erreichbar ist.

## 4. Konfiguration

```properties
sensor.devices[0].id=innen
sensor.devices[0].name=Innen
sensor.devices[0].room=Wohnzimmer
sensor.devices[0].device-id=...   # nur in config/ (Secret-nah)
sensor.devices[0].local-key=...   # nur in config/ (Secret)
sensor.devices[0].version=3.4
sensor.devices[0].temperature-dp=1
sensor.devices[0].humidity-dp=2
sensor.devices[0].temperature-scale=10
```

## 5. Architektur-Einordnung (Hexagonal)

Slice `sensor`: Port `ReadSensors` (in), `SensorDevice` + Factory (out),
`SensorService` (application), Adapter `adapter/in/rest/sensor` und
`adapter/out/sensor/{mock,local}`. Nutzt die geteilten `support.tuya`-Klassen.

## 6. Offene Punkte / TODO

- [ ] dps/scale bei einem neuen Sensor verifizieren (`tinytuya status`).
