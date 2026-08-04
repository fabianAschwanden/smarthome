# Spec – Use Case 15: PV-Prognose & vorausschauende Batterieladung

Status: v0.1 (Entwurf) · Datum: 2026-08-04 · Plattform: Java 25 / Quarkus (Hexagonal + DDD)

## 1. Zweck & Scope

Die App sammelt bereits eine Energie-Zeitreihe (PV-Produktion + Hausverbrauch alle 10 s,
45 Tage Retention) und kennt Open-Meteo. Dieser Use Case macht daraus eine
**PV-Ertragsprognose** für heute/morgen und leitet daraus eine **Ladefenster-Empfehlung**
für die Batterie ab: „Erwarteter Überschuss heute 11–15 Uhr, ~2.1 kW".

In Scope (v1):

- Stündliche PV-Leistungsprognose für heute + morgen (Strahlungsprognose × gelerntem
  Anlagenprofil), inkl. Tages-Ertragssumme (kWh)
- Anlagenprofil **aus der eigenen Historie lernen** (kein Datenblatt-Tuning nötig)
- Verbrauchs-Baseline aus der Historie → prognostizierte **Überschussfenster**
- **Ladefenster-Empfehlung** für das SMARTFOX-Relais 1, als Anzeige; optional per Klick
  als Batterie-Zeitplan übernehmen (bestehender Use Case 14)
- Anzeige im Dashboard (Prognose-Kurve neben Ist-Werten) und auf der Batterie-Seite
- Mock-Modus ohne Hardware/Internet

Out of Scope (Folgestufen, siehe PLAN.md §Folgestufen): automatisches Schalten ohne
Nutzeraktion, Storen-Hitzeschutz nach Sonnenstand, Wellness-Heizung in Überschussfenster,
Prognose-Genauigkeits-Tracking, SoC-basierte Steuerung.

**Grundsatz aus Use Case 2 bleibt:** Die Überschuss-*Regelung* gehört dem SMARTFOX
(AUTO-Modus). Dieser Use Case regelt nicht in Echtzeit, sondern **plant voraus** – er
empfiehlt Zeitfenster und nutzt zum Ausführen die bestehende Zeitsteuerung (UC 14),
die den Manuell-Modus setzt. Keine Duplikation der Geräte-Automatik.

## 2. Datenquellen

### 2.1 Strahlungsprognose (Open-Meteo, kein API-Key)

Gleicher Endpoint wie UC 10, zusätzliche Hourly-Felder:

```
GET https://api.open-meteo.com/v1/forecast
    ?latitude=…&longitude=…
    &hourly=global_tilted_irradiance,temperature_2m,cloud_cover
    &tilt=<neigung>&azimuth=<ausrichtung>
    &past_days=7&forecast_days=2
    &timezone=Europe/Zurich
```

- `global_tilted_irradiance` (GTI, W/m²) ist die Strahlung **in Modulebene** – Open-Meteo
  rechnet Neigung/Azimut selbst um (`tilt`/`azimuth` als Query-Parameter).
- `past_days=7` liefert die Ist-Strahlung der letzten Tage im selben Response – die
  Lern-Stufe braucht damit **keine eigene Strahlungs-Historie** in der DB.
- Azimut-Konvention Open-Meteo: 0° = Süd, −90° = Ost, +90° = West.

### 2.2 Eigene Historie (bestehend)

`EnergySample` (timestamp, pvWatt, consumptionWatt) über den bestehenden Port
`EnergySampleRepository` bzw. – konventionskonform für Slice-übergreifende Reads –
über den Driving Port `EnergyHistoryQuery` (Muster: UC 2 liest `CurrentEnergyQuery`).

## 3. Fachliches Modell

### 3.1 Anlagenprofil lernen (PlantProfile)

Statt kWp/Wirkungsgrad zu konfigurieren, lernt die App den Zusammenhang
**GTI → tatsächliche PV-Leistung** aus den letzten `forecast.learning.window-days`
(Default 21) Tagen:

- Stündliche Paare bilden: Ø-`pvWatt` der Stunde (aus EnergySamples) ↔ Ist-GTI der
  Stunde (aus `past_days`).
- Je **Stunden-Slot (0–23)** den Median des Quotienten `pvWatt / gti` bilden →
  `factorPerHour[24]` (W pro W/m²). Der Slot-Ansatz fängt Verschattung (Bäume, Kamin,
  Horizont) ein, die ein globaler Faktor nicht abbilden kann.
- Robustheit: nur Stunden mit `gti ≥ 50 W/m²` verwenden (Dämmerung/Nacht raus),
  Median statt Mittelwert (Schnee-/Ausreissertage), Slot ohne genügend Datenpunkte
  (`< 5`) erbt den Nachbar-Median.
- **Cold Start:** solange kein Profil gelernt ist, gilt ein globaler Faktor aus der
  Nennleistung (Prognose als „grob" markiert):
  `factor = kwp × 1000 × 0.85 / 1000` (W pro W/m²) – für 10 kWp also **8.5**.
  Herleitung: Bei Standard-Testbedingung (1000 W/m²) liefert die Anlage ihre
  Nennleistung, abzüglich Wirkungsgrad.
  *Korrektur (2026-08-04):* Hier stand ursprünglich `kwp × 0.85 / 1000` = 0.0085. Das ist
  um den Faktor 1000 zu klein – die Prognose wären 8.5 W statt 8500 W gewesen. Beim Test
  der Etappe 4 aufgefallen.

Das Profil ist ein Value Object und wird als **JSONB-Snapshot** persistiert (eine Zeile,
Konvention Blueprint §6) – neu lernen überschreibt.

### 3.2 PV-Prognose (PvForecaster, pur)

`expectedPvWatt(hour) = factorPerHour[hourOfDay] × gtiForecast(hour)`,
gedeckelt auf das historische Maximum (`maxObservedPvWatt`, Teil des Profils) –
verhindert Ausreisser bei GTI-Spitzen. Tagessumme = Integral über die Stunden (kWh).

### 3.3 Verbrauchs-Baseline & Überschussfenster (SurplusPlanner, pur)

- Baseline: Median-`consumptionWatt` je Stunden-Slot über dieselben `window-days`,
  getrennt **Werktag/Wochenende** (zwei 24er-Profile).
- `expectedSurplusWatt(hour) = expectedPvWatt(hour) − baseline(hour)`.
- **Überschussfenster** = zusammenhängende Stunden mit
  `expectedSurplusWatt ≥ forecast.surplus.min-watt` (Default 500) und Dauer
  `≥ forecast.surplus.min-duration` (Default 2 h). Pro Tag wird das **beste** Fenster
  (max. Energiesumme) zur Ladeempfehlung.

### 3.4 Ladeempfehlung (ChargeRecommendation)

Für heute (und morgen, informativ): `window (from–to)`, `expectedSurplusKwh`,
`peakSurplusWatt`, `confidence` (LEARNED | ROUGH bei Cold-Start-Fallback).
„Übernehmen" erzeugt über `ManageBatterySchedules` (UC 14) einen einmaligen
Zeitplan-Eintrag für das Fenster – die Ausführung (Manuell-Modus setzen, danach zurück)
bleibt vollständig beim bestehenden Use Case. Kein neuer Schaltpfad.

## 4. API (REST)

| Methode | Pfad | Antwort / Body |
|---------|------|----------------|
| GET | `/api/forecast/pv` | Stundenprognose heute+morgen: `[{hour, expectedPvWatt, gti}]`, Tagessummen (kWh), `confidence`, `learnedAt` |
| GET | `/api/forecast/surplus` | Fenster + Empfehlung: Baseline-Kurve, `windows[]`, `recommendation {from, to, expectedKwh, peakWatt}` |
| POST | `/api/forecast/recommendation/apply` | erzeugt den Batterie-Zeitplan aus der aktuellen Empfehlung; Antwort = angelegter Schedule (DTO aus UC 14). `409`, wenn keine Empfehlung vorliegt |

DTOs in `adapter/in/rest/dto/forecast/`; Frontend spiegelt die DTOs (Blueprint §5).

## 5. Konfiguration (`application.properties`)

```properties
# Anlage (einmalig; Standort kommt aus weather.latitude/longitude)
forecast.plant.tilt=30
forecast.plant.azimuth=0
forecast.plant.kwp=<kwp>                 # nur Cold-Start-Fallback (§3.1)

# Prognose & Lernen
forecast.refresh-interval=1h             # Strahlungsprognose holen + Prognose neu rechnen
forecast.learning.cron=0 0 3 * * ?       # Profil nächtlich neu lernen
forecast.learning.window-days=21

# Überschussfenster
forecast.surplus.min-watt=500
forecast.surplus.min-duration=2h
```

Mock vs. echt über die Build-Property `smarthome.real-devices` (wie alle Slices):
`MockIrradianceGateway` liefert eine synthetische Glockenkurve (sonniger Tag), damit
Dashboard und Tests ohne Internet funktionieren. Echte Werte in der gitignored
`config/application.properties` (tilt/azimuth/kwp sind standortbezogen).

## 6. Nicht-funktionale Anforderungen

- Läuft lokal; einzige externe Abhängigkeit ist Open-Meteo (wie UC 10). Ist Open-Meteo
  nicht erreichbar, bleibt die letzte Prognose stehen (Alter im UI sichtbar via
  `computedAt`); die App degradiert, sie fällt nicht aus.
- Prognose-Berechnung und Lernen sind **pur** (Domain Services, `Clock` injizierbar) –
  vollständig ohne Container testbar, aber als `@QuarkusTest` (Coverage-Gate,
  Blueprint §10).
- Eine Liquibase-Migration (append-only): Tabelle `plant_profile`
  (JSONB-Snapshot + `learned_at`).
- Keine neuen Sidecar-/Geräteabhängigkeiten; kein zusätzlicher Scheduler-Takt unter 1 h.

## 7. Architektur-Einordnung (Hexagonal)

Eigener Slice `forecast/` parallel zu `energy/`:

```
domain/model/forecast/        PlantProfile, PvForecast(+HourEntry), ConsumptionBaseline,
                              SurplusWindow, ChargeRecommendation, Confidence
domain/service/forecast/      PlantProfileLearner, PvForecaster, SurplusPlanner   (pur)
domain/port/in/forecast/      PvForecastQuery, SurplusQuery, ApplyRecommendation
domain/port/out/forecast/     IrradianceGateway (Forecast + past_days-Ist),
                              PlantProfileRepository
application/service/forecast/ ForecastService (@Scheduled refresh, hält aktuelle Prognose),
                              ProfileLearningService (@Scheduled cron)
adapter/out/irradiance/       openmeteo/ + mock/
adapter/out/persistence/      PlantProfileEntity + Panache-Repository
adapter/in/rest/forecast/     ForecastResource
```

Slice-übergreifend (nur über Driving Ports, ArchUnit-konform):
Historie via `EnergyHistoryQuery`/`EnergySampleRepository` (energy), Zeitplan-Anlage via
`ManageBatterySchedules` (batteryschedule). Kein direkter Zugriff auf fremde Adapter.

Frontend: `features/dashboard` (Prognose-Kurve in der Energie-Karte, Chart.js vorhanden),
`features/battery` (Empfehlungs-Karte mit „Als Zeitplan übernehmen").

## 8. Offene Punkte / TODO

- [ ] Neigung/Azimut der realen Anlage ermitteln und in der lokalen Config eintragen.
- [x] **Geklärt (2026-08-04): `global_tilted_irradiance` liefert bei `past_days` Werte.**
      Gegen die echte API geprüft (Standort aus `weather.*`, `tilt=30&azimuth=0`,
      `past_days=3`): 72 Vergangenheits-Stunden, davon 45 ungleich null, Maximum
      917 W/m² – plausibel über `shortwave_radiation` (822 W/m²), wie es für eine
      geneigte Südfläche sein muss. Der Fallback auf `shortwave_radiation` + eigenen
      Neigungsfaktor wird **nicht** gebraucht.
      Einschränkung: Es sind Modell-/Reanalyse-Werte, keine Messung am Standort – für
      das Lernen des Verhältnisses GTI→Leistung ist das die richtige Grössenordnung,
      ersetzt aber keinen Einstrahlungssensor.
- [ ] `min-watt`/`min-duration` gegen das reale Lastprofil kalibrieren (Batterie-Ladeleistung?).
- [ ] Entscheiden, ob die Empfehlung „morgen" schon in v1 per Apply übernehmbar ist
      oder nur heute (Zeitplan-Semantik UC 14 prüfen).
- [ ] Folgestufe: Prognose-vs-Ist-Abgleich persistieren (Genauigkeit sichtbar machen).
