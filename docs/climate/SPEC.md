# Spec – Use Case 7: Klimaanlage

Status: v1.1 (Midea angebunden) · Datum: 2026-06-20 · Plattform: Java 25 / Quarkus (app-template: Hexagonal + DDD)

## 1. Zweck & Scope

Klimaanlage(n) steuern: ein/aus, Betriebsmodus und Soll-Temperatur setzen,
Ist-Temperatur anzeigen.

In Scope: Power, Modus (Kühlen/Heizen/Auto/Lüften), Soll-Temperatur (16–30 °C),
Ist-Temperatur-Anzeige, Mock-Modus.
Out of Scope (später): Lüfterstufen, Swing/Lamellen, Timer/Zeitsteuerung.

## 2. Anbindung: Midea / NetHome Plus (lokal)

Reale Geräte sind **Midea**-Klimaanlagen (App „NetHome Plus"). Steuerung **lokal im
LAN** über die Bibliothek `msmart-ng` (V3-Authentifizierung mit `token`/`key`). Das
Midea-LAN-Protokoll wird **nicht** in Java nachgebaut – analog zu Tuya 3.4 läuft es
über den lokalen **Sidecar** (`tools/tuya-sidecar/sidecar.py`, Endpunkte
`/climate/read` und `/climate/control`). Alles hinter dem Driven Port `ClimateDevice`:

- `%dev`/`%test`: **MockClimateDevice** (in-memory, plausible Ist-Temperatur).
- Echtbetrieb (`smarthome.real-devices=true`): **LocalMideaClimateDevice**
  (`adapter/out/climate/local`) ruft den Sidecar. Geräte ohne vollständige Angaben
  (deviceId/token/key/ip) bleiben **PendingClimateDevice** (offline, 503).

Mode-Mapping Domäne ↔ msmart: `COOL`/`HEAT`/`AUTO` direkt, `FAN` ↔ `FAN_ONLY`.

> Midea-Geräte erlauben nur **eine** LAN-Session zur Zeit: ein Befehl direkt gefolgt
> von einem Read kann kurzzeitig „connection reset" liefern. `readState()` gibt dann
> `empty` zurück, der Service meldet `online:false` mit dem letzten Stand (kein Fehler).

## 3. API (REST)

| Methode | Pfad                        | Body / Antwort                              |
|---------|-----------------------------|---------------------------------------------|
| GET     | `/api/climate`              | Liste (power, boost, mode, targetTemp, currentTemp, outdoorTemp, online) |
| POST    | `/api/climate/{id}/power`   | `{ "on": true\|false }`                     |
| POST    | `/api/climate/{id}/mode`    | `{ "mode": "COOL"\|"HEAT"\|"AUTO"\|"FAN" }` |
| POST    | `/api/climate/{id}/target`  | `{ "temperature": 16..30 }`                 |
| POST    | `/api/climate/{id}/boost`   | `{ "on": true\|false }` (Turbo/maximale Leistung) |

404 unbekannte Anlage, 400 Temperatur ausserhalb 16–30 °C, 503 nicht erreichbar.

## 4. Konfiguration

```properties
climate.devices[0].id=klima
climate.devices[0].name=Klimaanlage
climate.devices[0].room=Wohnzimmer
# Midea/NetHome Plus – Secrets NUR in gitignored config/ bzw. Env:
climate.devices[0].device-id=<numerische Midea-ID>
climate.devices[0].token=<V3-Token>
climate.devices[0].key=<V3-Key>
climate.devices[0].address=<LAN-IP>
```

deviceId/token/key/ip stammen aus dem Midea-Discover (`msmart discover`); sie sind
gerätegebunden, gehören aber als Secrets nur in `config/application.properties` (gitignored),
nie ins Repo. Sidecar-URL über `smarthome.tuya-sidecar-url` (Default `http://127.0.0.1:8765`).

Mock vs. echt über `smarthome.real-devices` (wie die anderen Slices). Temperatur-
Grenzen (16–30 °C) sind in `Climate` als Invariante verankert.

## 5. Architektur-Einordnung (Hexagonal)

Eigener Slice `climate`: Treiber-Port `ControlClimate` (`domain/port/in/climate`),
getriebener Port `ClimateDevice` + `ClimateDeviceFactory` (`domain/port/out/climate`),
Application-Service `ClimateControlService` (`application/service/climate`), Adapter
`adapter/in/rest/climate` und `adapter/out/climate/{mock,local,pending}`. Der
Sidecar-Client liegt geteilt in `support.tuya.TuyaSidecarClient` (kein Adapter, damit
mehrere Adapter ihn nutzen dürfen).

## 6. Offene Punkte / TODO

- [x] Echter Adapter (Midea/NetHome Plus via Sidecar) – `LocalMideaClimateDevice`.
- [x] Power/Mode/Target gegen reales Gerät verifiziert (Live-Test 2026-06-20).
- [ ] Optional: Lüfterstufen und Timer ergänzen.
- [ ] Optional: Retry/Backoff im Sidecar gegen die Single-Session-Resets.
