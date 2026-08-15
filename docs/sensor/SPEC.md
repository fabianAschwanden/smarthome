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

## Historie: zwei Speicher mit verschiedenen Aufgaben

| Speicher | Auflösung | Aufbewahrung | Wofür |
|---|---|---|---|
| Prometheus | alle 30 s (Scrape) | 30 Tage | Dashboard, kurzfristige Verläufe |
| `sensor_sample` (Postgres) | alle 10 min, nach einem Jahr stündlich | unbegrenzt | Jahresvergleiche |

**Warum zwei:** Prometheus kennt nur *eine* Aufbewahrungsdauer je Instanz – keine Retention
je Metrik und kein Downsampling. «Nach einem Jahr nur noch stündlich» lässt sich dort nicht
ausdrücken; dafür bräuchte es Thanos/Mimir (Objektspeicher) oder VictoriaMetrics Enterprise.
Also behält Prometheus die feine Auflösung der letzten Wochen, und die eigene Tabelle die
Jahre.

**Verdichtung** (`sensor-history.compact-cron`, nachts): Alles vor
`sensor-history.raw-days` wird auf den *ersten* Messpunkt je Stunde und Sensor reduziert –
als eine SQL-Anweisung, nicht als Schleife in Java. Deterministisch und damit wiederholbar:
Ein zweiter Lauf findet nichts mehr.

**Grössenordnung:** 10-Minuten-Takt sind rund 105 000 Zeilen im Jahr (zwei Sensoren);
nach der Verdichtung bleiben davon etwa 17 500. Das trägt Postgres jahrzehntelang.

Aufgezeichnet werden nur belastbare Werte: Ein offline gemeldeter Sensor oder ein
Platzhalter erzeugt **keinen** Punkt. Eine Lücke ist ehrlicher als eine erfundene Zahl.

## Prometheus im Detail

Innen- und Aussenwerte gehen als Gauges nach `/q/metrics` und landen damit in Prometheus;
angesehen werden sie im Grafana-Board «Smarthome – Haus + Server», Zeile *Klima*.

| Metrik | Label |
|---|---|
| `smarthome_sensor_temperature_celsius` | `sensor="innen"` / `"aussen"` |
| `smarthome_sensor_humidity_percent` | dito |

Bewusst **keine** eigene Zeitreihen-Tabelle wie bei der Energie: Ein zweiter
Zeitreihen-Speicher neben dem, der ohnehin läuft, wäre Doppelarbeit. Der Preis ist die
Aufbewahrung – sie richtet sich nach der Prometheus-Retention (derzeit 30 Tage), nicht
nach einer eigenen Regel.

Ein Wert, der nicht vorliegt (Sensor offline oder Platzhalter `-1000`/`-1`), meldet
**NaN** – in Grafana eine Lücke statt einer falschen Null. Ein durchgezogener Strich auf
0 °C sähe aus wie Frost, nicht wie ein Ausfall.

## 6. Offene Punkte / TODO

- [ ] dps/scale bei einem neuen Sensor verifizieren (`tinytuya status`).
