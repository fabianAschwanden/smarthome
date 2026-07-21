# Spec – Use Case 2: Batteriesteuerung (SMARTFOX-Relais 1)

Status: v1.0 (umgesetzt) · Datum: 2026-06-20 · Plattform: Java 25 / Quarkus (Hexagonal + DDD)

## 1. Zweck & Scope

Die Hausbatterie wird über **Relais 1 des SMARTFOX** geladen/freigegeben. Dieser
Use Case macht dieses Relais steuerbar – **manuell** (Bedienung am Dashboard) und
**automatisch** (Laden bei PV-Überschuss).

In Scope:

- Relais 1 manuell EIN/AUS schalten (REST + Dashboard)
- Automatik-Modus: an die native SMARTFOX-Überschuss-Steuerung übergeben
- Umschalten Manuell ↔ Auto
- Statusanzeige (Modus, gewünschter Relais-Zustand, letzter Schaltzeitpunkt)
- Mock-Modus zum Testen ohne Hardware

Out of Scope (spätere Use Cases): Lade-Fahrpläne/Zeitfenster, mehrere Relais,
SoC-basierte Steuerung (Ladezustand der Batterie), Historie/Persistenz.

## 2. Steuer-Schnittstelle (SMARTFOX-Relais)

Das SMARTFOX-Relais 1 (Batterie) ist **dreiwertig** (Aus / Manuell / Automatik) und
wird per HTTP-GET gestellt:

```
GET http://<smartfox-ip>/setswrel.cgi?rel=1&state=<0|1|2>
```

| `state` | Wirkung   | liest zurück (`hidR1Mode`) |
|---------|-----------|-----------------------------|
| `1`     | **Manuell** ein (Ladung erzwingen) | `m` |
| `2`     | **Aus**   | `x` |
| `0`     | **Automatik** (geräteeigene PV-Überschuss-Steuerung) | `0` nicht ladend / `1` ladend |

An der realen Anlage verifiziert (2026-07). Die URL und Codes sind firmware-abhängig und
konfigurierbar (`battery.smartfox.relay-url`, `state-on=1`, `state-off=2`, `state-auto=0`),
nicht festverdrahtet.

## 3. Modi

Die drei Gerätezustände werden auf das Domänen-Paar (`ControlMode`, `RelayState`) abgebildet:

| Anzeige      | (mode, state)      | `hidR1Mode` |
|--------------|--------------------|-------------|
| **Aus**      | `(MANUAL, OFF)`    | `x` |
| **Manuell**  | `(MANUAL, ON)`     | `m` |
| **Automatik**| `(AUTO, Ist-Ausgang)` | `0`/`1` |

**Automatik gehört dem SMARTFOX, nicht der App.** Im `AUTO`-Modus setzt die App nur den
Gerätemodus (`state=0`) und spiegelt dessen Ist-Ausgang zurück – sie fährt **keinen
eigenen** Überschuss-Algorithmus (früher `SurplusChargePolicy`; entfernt, weil er die
native SMARTFOX-Automatik dupliziert hätte).

**Rückkopplung:** Beim Start liest die App den Ist-Zustand aus `values.xml`
(`battery.smartfox.state-field`, Default `hidR1Mode`) und übernimmt ihn (kein Schalt-
befehl). Ein Scheduler (`battery.sync-interval`, Default 15 s) gleicht die Anzeige danach
laufend mit dem echten Relais ab – so werden externe Umschaltung (native View),
Automatik-Schaltvorgänge und nicht gegriffene Befehle sichtbar. Lesefehler halten den
letzten Stand.

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
battery.smartfox.state-off=2
battery.smartfox.state-auto=0
battery.smartfox.state-field=hidR1Mode
battery.sync-interval=15s
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
