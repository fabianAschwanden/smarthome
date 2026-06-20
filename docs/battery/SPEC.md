# Spec – Use Case 2: Batteriesteuerung (SMARTFOX-Relais 1)

Status: Entwurf v1.0 · Datum: 2026-06-19 · Plattform: Java 25 / Quarkus (app-template: Hexagonal + DDD)

## 1. Zweck & Scope

Die Hausbatterie wird über **Relais 1 des SMARTFOX** geladen/freigegeben. Dieser
Use Case macht dieses Relais steuerbar – **manuell** (Bedienung am Dashboard) und
**automatisch** (Laden bei PV-Überschuss).

In Scope:

- Relais 1 manuell EIN/AUS schalten (REST + Dashboard)
- Automatik-Modus: Relais schaltet anhand des PV-Überschusses (mit Hysterese)
- Umschalten Manuell ↔ Auto
- Statusanzeige (Modus, gewünschter Relais-Zustand, letzter Schaltzeitpunkt)
- Mock-Modus zum Testen ohne Hardware

Out of Scope (spätere Use Cases): Lade-Fahrpläne/Zeitfenster, mehrere Relais,
SoC-basierte Steuerung (Ladezustand der Batterie), Historie/Persistenz.

## 2. Steuer-Schnittstelle (SMARTFOX-Relais)

Der SMARTFOX schaltet ein Relais per HTTP-GET:

```
GET http://<smartfox-ip>/setswrel.cgi?rel=<n>&state=<0|1>
```

| Parameter | Bedeutung                                                       |
|-----------|-----------------------------------------------------------------|
| `rel`     | Relaisnummer (hier **1** für die Batterie)                      |
| `state=1` | manuell **EIN** – schaltet die Batterieladung ein              |
| `state=0` | **AUS** – schaltet ab / zurück auf SMARTFOX-Automatik          |

An der realen Anlage verifiziert: `GET http://<smartfox-ip>/setswrel.cgi?rel=1&state=1`
schaltet die Batterieladung sofort ein. Die URL und die state-Codes sind firmware-
abhängig und deshalb konfigurierbar (`battery.smartfox.relay-url`,
`battery.smartfox.state-on=1`, `battery.smartfox.state-off=0`), nicht festverdrahtet.

## 3. Modi

| Modus    | Verhalten                                                                 |
|----------|---------------------------------------------------------------------------|
| `MANUAL` | Der Relais-Zustand folgt direkt der letzten Benutzeraktion (EIN/AUS).      |
| `AUTO`   | Ein Scheduler wertet periodisch den PV-Überschuss aus und schaltet das Relais. |

Beim Wechsel nach `AUTO` greift sofort der nächste Auto-Tick; ein Wechsel nach
`MANUAL` friert den Zustand auf die letzte manuelle Vorgabe ein.

**Wichtig:** Die Automatik gehört *dieser App* (`SurplusChargePolicy`), nicht dem
SMARTFOX. Das Relais wird explizit von dieser App geschaltet (`state-on=1` für EIN,
`state-off=0` für AUS).

## 4. Auto-Logik: Überschuss-Laden mit Hysterese

Überschuss = Einspeisung der Referenzquelle (SMARTFOX): `surplusW = -gridWatt`.

- `surplusW ≥ chargeOnWatt`  → Relais **EIN**
- `surplusW ≤ chargeOffWatt` → Relais **AUS**
- dazwischen → Zustand **halten** (Hysterese gegen Flattern)

Default: `chargeOnWatt = 1500`, `chargeOffWatt = 300`. Reine Domänenlogik in
`SurplusChargePolicy` (framework-frei, pur, testbar).

## 5. API (REST)

| Methode | Pfad                  | Body / Antwort                                  |
|---------|-----------------------|-------------------------------------------------|
| GET     | `/api/battery`        | Status: Modus, gewünschter Relais-Zustand, Zeit |
| PUT     | `/api/battery/mode`   | `{ "mode": "MANUAL" \| "AUTO" }`                 |
| POST    | `/api/battery/relay`  | `{ "state": "ON" \| "OFF" }` (nur in `MANUAL`)  |

`POST /relay` im `AUTO`-Modus wird mit `409 Conflict` abgelehnt – die Automatik
besitzt dann den Relais-Zustand.

## 6. Konfiguration (`application.properties`)

```properties
battery.smartfox.relay-url=http://<smartfox-ip>/setswrel.cgi?rel=1&state={state}
battery.smartfox.state-on=1
battery.smartfox.state-off=0
battery.auto.charge-on-watt=1500
battery.auto.charge-off-watt=300
battery.auto.tick-interval=3s
```

`{state}` wird durch den jeweiligen state-Code ersetzt. Mock vs. echtes Relais über
die Build-Property `smarthome.real-devices`: Mock-Relais
`@UnlessBuildProperty(..., enableIfMissing=true)`, HTTP-Relais `@IfBuildProperty(... "true")`.
Die Property ist in `%prod` und `%live` auf `true` gesetzt, sonst (Dev/Test) Mock.

**Lokal gegen das echte Relais testen:** `./mvnw quarkus:dev -Dquarkus.profile=dev,live`
– schaltet im Heimnetz das echte SMARTFOX-Relais 1, mit Live-Reload und ohne Login.

## 7. Nicht-funktionale Anforderungen

- Läuft vollständig lokal im LAN, ohne Internet/Cloud.
- Modus/Zustand im RAM (kein DB-Zwang in v1, daher keine Liquibase-Migration).
- Auto-Tick ist idempotent: nur tatsächliche Zustandswechsel lösen einen HTTP-Call aus.

## 8. Architektur-Einordnung (Hexagonal)

Eigener Slice `battery/` parallel zu `energy/`. Treiber-Port `ControlBattery`
(`domain/port/in`), getriebener Port `RelaySwitch` (`domain/port/out`),
Domain-Service `SurplusChargePolicy` (pur), Application-Service
`BatteryControlService` (`application/service`, hält Modus, treibt den Auto-Tick),
Adapter (`adapter/in/rest`, `adapter/out/{smartfox,mock}`). Der Auto-Modus liest
den Energiestand über den bestehenden `CurrentEnergyQuery`-Port des Energy-Slice.

## 9. Offene Punkte / TODO

- [ ] Schalt-URL/Parameter gegen die reale SMARTFOX-Firmware verifizieren
      (`setswrel.cgi`, `rel`/`state`-Semantik).
- [ ] Bestätigen, dass `state=0` „aus" bedeutet (vs. „zurück auf interne Automatik").
- [ ] Schwellen (`chargeOnWatt`/`chargeOffWatt`) gegen reales Lastprofil einstellen.
- [ ] Prüfen, ob die Batterie zusätzlich einen Mindest-/Maximal-SoC braucht
      (würde eine zweite Lese-Quelle erfordern → späterer Use Case).
