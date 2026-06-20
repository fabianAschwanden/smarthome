# Spec – Use Case 1: Energieverbrauch visualisieren

Status: Entwurf v1.1 · Datum: 2026-06-19 · Plattform: Java 25 / Quarkus (app-template: Hexagonal + DDD)

## 1. Zweck & Scope

Ziel des ersten Use Case ist es, den **aktuellen Energieverbrauch** des Hauses in
Echtzeit sichtbar zu machen – und dabei die beiden vorhandenen Messquellen
(**Fronius-Wechselrichter** und **SMARTFOX**) **nebeneinander** darzustellen, inklusive
ihrer Abweichung.

In Scope:

- Live-Auslesen von Fronius (Solar API) und SMARTFOX (`values.xml`)
- Normalisierung beider Quellen auf ein gemeinsames Datenmodell
- Berechnung des Hausverbrauchs aus jeder Quelle
- Visualisierung (Live-Dashboard) mit Gegenüberstellung und Differenz-Anzeige
- Mock-Modus zum Testen ohne Hardware

Out of Scope (spätere Use Cases): Steuerung/Schalten, Überschuss-Logik,
Langzeit-Historie/Datenbank, Home-Assistant-Integration.

## 2. Vorzeichen- & Begriffskonvention (normalisiert)

Beide Quellen werden auf **eine** einheitliche Konvention abgebildet:

| Größe          | Feld          | Konvention                                   |
|----------------|---------------|----------------------------------------------|
| Netzleistung   | `gridW`       | **+ = Bezug** aus dem Netz, − = Einspeisung  |
| PV-Leistung    | `pvW`         | + = Produktion                               |
| Batterie       | `batteryW`    | + = Laden (Verbrauch), − = Entladen          |
| Hausverbrauch  | `consumptionW`| + = Verbrauch                                |

Alle Werte in **Watt (W)**. Die gerätespezifische Roh-Konvention wird in den
Clients umgerechnet (siehe Abschnitt 4). Vorzeichen sind gegen die reale Anlage
zu **verifizieren** (siehe TODOs).

## 3. Datenquellen

### 3.1 Fronius – Solar API (JSON)

- Endpoint: `GET http://<fronius-ip>/solar_api/v1/GetPowerFlowRealtimeData.fcgi`
- Antwort: JSON, relevante Felder unter `Body.Data.Site`:

| Feld     | Bedeutung                          | Hinweis                         |
|----------|------------------------------------|---------------------------------|
| `P_Grid` | Netzleistung                       | + = Bezug, − = Einspeisung      |
| `P_PV`   | PV-Produktion                      | `null` bei Nacht/keiner Prod.   |
| `P_Load` | Hausverbrauch (Last)               | i. d. R. **negativ**            |
| `P_Akku` | Batterieleistung                   | bei Anlage **ohne** Fronius-Akku `null`/0 |

### 3.2 SMARTFOX – `values.xml`

- Endpoint: `GET http://<smartfox-ip>/values.xml`
- Antwort: XML mit vielen `<value id="...">Text</value>`-Einträgen.
  Werte enthalten teils Einheiten (`" W"`, `" kW"`) und müssen geparst/skaliert werden.

An der realen Anlage (Firmware-Stand 2026-06) verifiziertes Mapping:

| `id`                | Bedeutung                                                       |
|---------------------|-----------------------------------------------------------------|
| `detailsPowerValue` | **Netzwert** am Messpunkt (W): **+ = Bezug, − = Einspeisung** (= normalisierte Konvention) |
| `toGridValue`       | derselbe Netzwert, aber in kW                                   |
| `pUserValue`        | ⚠ an dieser Anlage **immer 0 / unbrauchbar** – NICHT verwenden  |
| `hidProduction`     | PV-Produktion gesamt ✅                                          |
| `hidBatteryPower`   | Batterieleistung (0, falls kein am SMARTFOX gemessener Akku)    |
| `wr1PowerValue`     | Leistung Wechselrichter 1 (= **Fronius**, via Modbus eingelesen) |
| `powerL1/L2/L3Value`| Leistung je Phase (Summe ≈ `detailsPowerValue`)                 |

## 4. Verbrauchsberechnung

**Fronius:** `consumptionW = -P_Load`  (Fallback: `consumptionW = P_PV + P_Grid - P_Akku`)

**SMARTFOX:** `gridW = detailsPowerValue` (bereits normalisiert), und da `pUserValue`
unbrauchbar ist, wird der Verbrauch aus der Energiebilanz berechnet:
`consumptionW = PV + gridW − batteryW`. Beispiel real: PV 4380 W, Netz −1512 W
(Einspeisung) → Verbrauch 2868 W.

Energiebilanz (zur Plausibilisierung): `PV + Netzbezug − Einspeisung − Batterieladung = Verbrauch`.

## 5. Abweichungs-Analyse (Fronius vs. SMARTFOX)

Die optisch sichtbaren Differenzen sind **erwartbar** und haben mehrere Ursachen:

1. **Gleiche PV-Quelle, doppelt gemessen:** Der SMARTFOX liest den Fronius selbst
   per Modbus als „Wechselrichter 1" ein. Die PV-Zahl entsteht damit zweimal –
   direkt aus der Fronius-Solar-API und zeitversetzt über den SMARTFOX. Schon
   unterschiedliche Abfrage-Intervalle und Rundung erzeugen kleine Differenzen.
2. **Nicht synchrone Messzeitpunkte:** Beide Geräte sampeln unabhängig; bei
   schwankender Last/Produktion ergeben sich Momentaufnahmen-Unterschiede.
3. **Verschiedene Messpunkte:** SMARTFOX misst am Netzanschluss; Fronius berechnet
   `P_Load` aus seinem eigenen Smart Meter. Liegen die Messpunkte nicht identisch,
   weichen die Werte systematisch ab.
4. **Messgenauigkeit/Kalibrierung:** Zähler haben Toleranzen (typ. ±1–2 %).
5. **Eigenbau-Batterie an separatem WR:** Der Fronius „sieht" diese Batterie **nicht**
   als Akku (`P_Akku` bleibt leer). Ihr Laden erscheint bei **beiden** Quellen als
   Teil des Hausverbrauchs – das ist konsistent, aber gut zu wissen.

**Umgang im System:**

- Beide Quellen werden **roh** angezeigt (keine Quelle „versteckt" die andere).
- Die **Differenz** (W und %) für Verbrauch, PV und Netz wird explizit ausgewiesen.
- Optional eine **Referenzquelle** (Default: SMARTFOX als Netz-/Verrechnungsreferenz)
  konfigurierbar; der Fronius liefert die detaillierte PV-Sicht.
- Differenzen werden mitgeloggt, um systematische von zufälligen Abweichungen zu trennen.

## 6. Funktionale Anforderungen

- F1: Beide Quellen werden in konfigurierbarem Intervall (Default 3 s) gepollt.
- F2: Fällt eine Quelle aus, bleibt die andere sichtbar; Status `ERROR`/`STALE` wird angezeigt.
- F3: Live-Dashboard zeigt aktuellen Verbrauch, beide Quellen und deren Differenz.
- F4: Mini-Live-Chart (letzte ~60 Messwerte) für Verbrauch beider Quellen.
- F5: Mock-Modus (`energy.mock=true`) liefert synthetische Daten ohne Hardware.

## 7. Nicht-funktionale Anforderungen

- Läuft vollständig **lokal** im LAN, ohne Internet/Cloud.
- Geringe Last (einfache HTTP-Polls), Speicher im RAM (kein DB-Zwang in v1, daher keine Liquibase-Migration).
- Java 25 + Quarkus gemäß `app-template`-Blueprint (Hexagonal + DDD, ArchUnit-erzwungen); gut testbar und versionierbar.

## 8. API (REST)

| Methode | Pfad                   | Antwort                                            |
|---------|------------------------|----------------------------------------------------|
| GET     | `/api/energy/current`  | Aktueller Snapshot: beide Readings + Differenz     |
| GET     | `/api/energy/sources`  | Status je Quelle (OK/ERROR/STALE, Zeitstempel)     |
| GET     | `/` (static)           | Live-Dashboard                                      |

## 9. Konfiguration (`application.properties`)

```properties
# %dev nutzt den Mock-Adapter (keine Hardware noetig), %prod die echten Adapter
energy.reference-source=SMARTFOX
energy.fronius.base-url=http://192.168.1.x
energy.smartfox.base-url=http://<smartfox-ip>
# Frontend-Polling-Intervall (ms)
energy.poll-interval-ms=3000
```

Mock vs. real wird ueber Quarkus-Profile gesteuert: Mock-Adapter `@UnlessBuildProfile("prod")`,
echte Adapter `@IfBuildProfile("prod")`. So liefert `./mvnw quarkus:dev` sofort Daten ohne Hardware.

## 10. Architektur-Einordnung (Hexagonal)

Der Use Case folgt dem `app-template`-Blueprint: Treiber-Port `CurrentEnergyQuery`
(`domain/port/in`), getriebener Port `EnergySourceGateway` (`domain/port/out`),
Domain-Service `EnergyComparison` (pur, framework-frei), Application-Service
`CurrentEnergyService` (`application/service`) und Adapter (`adapter/in/rest`,
`adapter/out/{fronius,smartfox,mock}`).

In v1 lesen die out-Adapter die Geräte **direkt** über deren lokale APIs – bewusst,
weil hier gerade die **Rohdifferenz** der beiden Quellen interessiert (Home Assistant
würde die Werte normalisieren und die Differenz verdecken). Da alles hinter
`EnergySourceGateway` gekapselt ist, lässt sich später ohne Änderung an Domain/Application
ein Home-Assistant-Adapter (REST/MQTT) ergänzen.

## 11. Offene Punkte / TODO

- [ ] Vorzeichen aller Felder gegen die reale Anlage verifizieren (`P_Grid`, `P_Akku`,
      `toGridValue`, `battery1Power`).
- [ ] Exakte SMARTFOX-`id`s gegen `http://<smartfox-ip>/values.xml` abgleichen
      (Firmware-abhängig).
- [ ] Fronius-IP/Hostname und Solar-API-Version (v0/v1) bestätigen.
- [ ] Entscheiden, ob `consumptionW` direkt aus `pUserValue`/`P_Load` oder berechnet wird.
- [ ] Einheiten-Skalierung (W vs. kW) je Feld prüfen.

## 12. Nächste Schritte

1. Grundgerüst im Mock-Modus starten, Dashboard prüfen.
2. Reale IPs eintragen, `energy.mock=false`, Felder/Vorzeichen verifizieren.
3. Differenz über einen Tag beobachten → systematischen Offset bestimmen.
4. Danach Use Case 2 (Schalten) bzw. Home-Assistant-Anbindung.
