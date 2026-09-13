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
| GET     | `/api/battery/sun-guard` | Ohne-Sonne-Ausschalter: `enabled`, `armed`, `lastTrippedAt` |
| PUT     | `/api/battery/sun-guard` | `{ "enabled": true \| false }`                  |

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
battery.charging.power-watt=${BATTERY_CHARGING_POWER_WATT:1640}
```

Die 1640 W stammen aus dem Ladevorgang vom 29.08.2026: Median 3618 W während des Ladens
gegen 1981 W in der halben Stunde davor. Sie sind damit an der Anlage abgeleitet und nicht
geraten – aber eben eine Konstante, kein Messwert.

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

## Ohne-Sonne-Ausschalter

Im Manuell-Modus lädt die Batterie, was das Relais hergibt – ob die Sonne scheint oder
nicht. Ein Ladeauftrag, der in den Abend läuft, holt den Strom also aus dem Netz und tut
damit genau das Gegenteil dessen, wofür er gedacht war. Bis hierher wurde das über eine
feste Uhrzeit erschlagen (Zeitsteuerungs-Regel «täglich 20:00 AUS»). Der Wächter ersetzt
die Uhrzeit durch den tatsächlichen Stand der Sonne.

**Er schaltet nur AUS.** Wann geladen wird, entscheiden weiterhin die Zeitsteuerung, die
Lade-Automatik und im Automatik-Modus der SMARTFOX. Eine vierte Stelle, die einschaltet,
stritte mit allen dreien.

**Er schaltet einmal pro Sonnenuntergang.** `armed` ist der Merker «die Sonne war da».
Nur ein scharfer Wächter löst aus, danach ist er stumpf, bis wieder Sonne da war. Ohne
diesen Merker würgte er jeden nächtlichen Einschaltversuch binnen einer Minute wieder ab –
wer nachts bewusst laden will, soll das dürfen. Der Merker liegt in der Datenbank, nicht
im Speicher: Ein Neustart am Abend dürfte den Wächter nicht wieder scharf machen.

**Abgeschaltet heisst `(MANUAL, OFF)`** – dasselbe, was eine Zeitsteuerungs-Regel mit
Aktion AUS tut, und auch aus dem Automatik-Modus heraus. Wer den Wächter einschaltet,
will die Ladung abends aus haben, nicht dem Gerät überlassen.

**Ohne Messwerte passiert nichts.** Eine tote Energiequelle sieht aus wie eine Nacht; ein
Wächter, der daraufhin abschaltet, wäre schlimmer als keiner.

```properties
battery.sun-guard.tick-interval=60s
battery.sun-guard.window=20m
battery.sun-guard.sun-watt=${BATTERY_SUN_GUARD_SUN_WATT:1000}
battery.sun-guard.dark-watt=${BATTERY_SUN_GUARD_DARK_WATT:300}
```

Entschieden wird über den **Median** der PV-Leistung im Fenster, nicht über den
Momentanwert – sonst sähe eine Wolke aus wie die Nacht. **Zwei Schwellen**, damit der
Wächter am trüben Nachmittag nicht im Minutentakt scharf und stumpf wird.

`dark-watt=300` heisst wörtlich «keine Sonne mehr»: An einem klaren Septembertag (13.09.2026,
gemessen) liegt die PV-Leistung um 17:00 bei 759 W, um 18:00 bei 260 W, um 19:00 bei 47 W –
der Wächter löst also gegen 18:15 aus. Wer stattdessen will, dass **nur mit echtem
Überschuss** geladen wird, setzt den Wert in die Nähe der Ladeleistung (1640 W) plus
Hausverbrauch; dann schaltet er schon am späten Nachmittag ab.

Zustand in `battery_sun_guard` (eine Zeile, Migration `0018`). Standard: **aus** – etwas,
das von selbst schaltet, ist eine bewusste Entscheidung. Entscheidung in
`domain/service/battery/SunGuardRule` (pur, ohne Uhr und Relais), Ausführung in
`application/service/battery/SunGuardService`.

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
