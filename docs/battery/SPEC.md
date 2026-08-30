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

## Ladeenergie: gerechnet aus einer konfigurierten Leistung

**Weder SMARTFOX noch Wechselrichter messen das Lade-Relais separat.** Der SMARTFOX führt
für Relais 1 nur Status, Rest- und Laufzeit – keinen kWh-Zähler; der Fronius meldet
`P_Akku: None`, er sieht die Batterie gar nicht (beides am 26.08.2026 abgefragt).

Der Umweg über den Hausverbrauch wurde versucht und **verworfen**: Ein Haus schwankt um
±1000 W – in derselben Grössenordnung wie die gesuchte Ladeleistung. Am 29.08.2026 lag in
den zwei Minuten vor dem Einschalten zufällig eine Haushaltsspitze (2572 W statt der sonst
typischen 1981 W); die Differenz wurde negativ, und ein Ladevorgang über viereinhalb
Stunden stand mit **0 kWh** da. Auch mit 30-Minuten-Fenstern bleibt das Verfahren eine
Schätzung mit grosser Streuung.

**Deshalb: Energie = konfigurierte Leistung × Laufzeit.**

```properties
battery.charging.power-watt=${BATTERY_CHARGING_POWER_WATT:1500}
```

Änderbar über die Umgebungsvariable im Deployment oder als
`%lan.battery.charging.power-watt` in der Geräte-Config – beides ohne neues Image.

Der aus dem Hausverbrauch abgeleitete Wert wird **weiter mitgeführt** (`measured_watt`),
aber nur zum Vergleich: Weicht er dauerhaft von der Konstanten ab, gehört diese
nachjustiert. Die Oberfläche markiert eine Abweichung über einem Fünftel mit `*`.

Eine ehrliche Konstante ist mehr wert als eine Messung, die im Rauschen ertrinkt. Wer
eine belastbare Zahl braucht, braucht einen eigenen Zähler – der SMARTFOX bringt dafür
einen Ladestations-Kanal mit (`ccEnergyValue`).

Ein Vorgang wird **beim Einschalten sofort** in `charging_session` festgehalten und erst
beim Ausschalten vervollständigt; läge der Beginn nur im Speicher, verschluckte jeder
Neustart den laufenden Vorgang. Erfasst werden auch Ladevorgänge, die **direkt am
SMARTFOX** gestartet wurden: Beobachtet wird der Relais-Zustand, nicht der eigene
Schaltbefehl.

Die **Gegenmessung** (kurz abschalten, Abfall messen) ist damit gegenstandslos und
standardmässig **aus** – sie sollte die Leistung messen, und das tut jetzt die Konstante.
Ohne diesen Zweck bliebe nur ihr Preis: zwei zusätzliche Relais-Schaltungen je
Ladevorgang und eine Pause im Laden.

## Betriebsfalle: der tote HTTP-Client (16.08.2026)

Das Relais liess sich aus der App nicht mehr schalten, und die Anzeige stand auf «Aus»,
obwohl es eingeschaltet war. Im Log:

```
RelaySwitchFailed: Relais konnte nicht auf Modus MANUAL / ON gestellt werden:
selector manager closed
```

Der Adapter hielt einen `HttpClient` in einem statischen Feld. Verliert ein solcher
Client seinen Selector-Manager, scheitert **jeder** weitere Aufruf dauerhaft – und weil
derselbe Client auch `values.xml` liest, fielen Schalten *und* Ist-Abgleich zusammen aus.
Die Anzeige blieb deshalb auf dem letzten selbst gesetzten Wert stehen; sie war nie
falsch berechnet, sondern nur nie aktualisiert.

Bemerkenswert: Der Energie-Adapter las weiter, denn er hält einen **eigenen** Client. Nur
der eine war tot.

**Sofortmassnahme** war ein Pod-Neustart (`initFromDevice` liest den Ist-Zustand).
**Behoben** durch `support/http/RecoveringHttpClient`: Er prüft vor jedem Aufruf, ob der
Client beendet ist, und wiederholt einmal mit einem frischen. Alle vier Adapter mit
diesem Muster nutzen ihn.

**Fürs nächste Mal:** «Anzeige stimmt nicht» und «Schalten geht nicht» können *eine*
Ursache haben. Der Log-Eintrag `RelaySwitchFailed` steht in `kubectl logs deploy/smarthome`.

## 9. Offene Punkte / TODO

- [ ] Schalt-URL/Parameter gegen die reale SMARTFOX-Firmware verifizieren
      (`setswrel.cgi`, `rel`/`state`-Semantik).
- [ ] Bestätigen, dass `state=0` „aus" bedeutet (vs. „zurück auf interne Automatik").
- [ ] Schwellen (`chargeOnWatt`/`chargeOffWatt`) gegen reales Lastprofil einstellen.
- [ ] Prüfen, ob die Batterie zusätzlich einen Mindest-/Maximal-SoC braucht
      (würde eine zweite Lese-Quelle erfordern → späterer Use Case).
