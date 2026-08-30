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

## Ladeenergie: geschätzt, nicht gemessen

**Weder SMARTFOX noch Wechselrichter messen das Lade-Relais separat.** Der SMARTFOX führt
für Relais 1 nur Status, Rest- und Laufzeit – keinen kWh-Zähler; der Fronius meldet
`P_Akku: None`, er sieht die Batterie gar nicht (beides am 26.08.2026 abgefragt).

Was bleibt, ist der Hausverbrauch – und darin steckt das Ladegerät. Weil die App den
Schaltzeitpunkt kennt, ist der **Verbrauchssprung beim Einschalten** die Ladeleistung:

| Schritt | Wie |
|---|---|
| Vergleich davor | Median des Verbrauchs über `baseline-window` (**30 min**) vor dem Einschalten |
| Ladeleistung | Median über den **ganzen Ladevorgang** minus Vergleich, nie negativ |
| Anlauf | `settle-time` nach dem Einschalten wird übersprungen |
| Energie | Leistung × Dauer (ohne die Pause der Gegenmessung) |

**Warum so lange Fenster (Korrektur vom 30.08.2026):** Die erste Fassung verglich zwei
Minuten vor dem Einschalten mit zwei Minuten danach. Am 29.08. lag in diesen zwei Minuten
zufällig eine Haushaltsspitze — 2572 W statt der sonst typischen 1981 W. Die Differenz
wurde negativ, und negativ heisst 0: Ein Ladevorgang über viereinhalb Stunden stand mit
**0 kWh** in der Liste. Dieselben Daten über 30 Minuten Vergleich und den ganzen
Ladevorgang gerechnet ergeben rund 1600 W.

Ein Haus schwankt um ±1000 W — in derselben Grössenordnung wie die gesuchte Ladeleistung.
Zwei Minuten sind dagegen kein Mass.

Die Mediane rechnet **die Datenbank** (`percentile_cont`), nicht die Anwendung: Über
Stunden wären das Zehntausende Messpunkte, und gebraucht wird davon eine einzige Zahl.

Median statt Mittelwert: Ein einzelner Ausreisser – der Backofen, der zufällig anspringt –
verschöbe einen Mittelwert, den Median kaum.

**Grenzen, die man kennen muss.** Schaltet gleichzeitig eine andere grosse Last, wandert
deren Leistung in die Rechnung. Und die Leistung gilt als konstant über den ganzen Vorgang;
ein Ladegerät, das gegen Ende abregelt, wird überschätzt. Für die Grössenordnung taugt das,
als Abrechnungsgrundlage nicht. Wer eine belastbare Zahl braucht, braucht einen eigenen
Zähler – der SMARTFOX bringt dafür einen Ladestations-Kanal mit (`ccEnergyValue`).

### Gegenmessung mitten im Ladevorgang

Nach `verify-after` (Standard 7 min) wird **einmal je Ladevorgang** kurz abgeschaltet und
wieder eingeschaltet. Der Verbrauch fällt dabei um die Ladeleistung – eine zweite,
unabhängige Messung, und die belastbarere: Sie entsteht im eingeschwungenen Zustand,
während die erste unmittelbar nach dem Einschalten fällt, wo das Ladegerät noch anläuft.
Für die Energie zählt deshalb die Gegenmessung, sobald es eine gibt; die Pause zählt nicht
als Ladezeit.

Die Gegenmessung dient nur noch dem **Vergleich**, nicht mehr als Grundlage der Energie:
Ihre zweiminütige Pause ist demselben Rauschen ausgesetzt, das die kurzen Fenster schon
einmal auf 0 kWh gebracht hat. Weichen beide Zahlen um mehr als ein Fünftel ab, markiert
die Oberfläche das mit `*` — dann lief in einer der beiden Messungen etwas anderes mit.

**Das ist ein Eingriff an der Anlage**, kein reines Mitlesen: Das Relais schaltet zweimal
zusätzlich je Ladevorgang, und für `verify-pause` wird nicht geladen. Deshalb:

- abschaltbar über `battery.charging.verify-enabled`,
- **nur im Manuell-Modus** – im Automatik-Modus gehört das Relais dem SMARTFOX, und ein
  Eingriff von aussen arbeitete gegen dessen Regelung,
- der Stand steht in der Datenbank, nicht im Speicher: Ein Neustart mitten in der Pause
  muss erkennen können, dass er wieder einschalten muss, sonst bliebe die Anlage
  ausgeschaltet zurück.

Ein Vorgang wird **beim Einschalten sofort** in `charging_session` festgehalten und erst
beim Ausschalten vervollständigt; läge der Beginn nur im Speicher, verschluckte jeder
Neustart den laufenden Vorgang. Lässt sich nichts schätzen, wird der Eintrag **verworfen**
statt mit 0 kWh geführt – eine 0 sähe aus wie «nicht geladen».

Erfasst werden auch Ladevorgänge, die **direkt am SMARTFOX** gestartet wurden: Beobachtet
wird der Relais-Zustand, nicht der eigene Schaltbefehl.

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
