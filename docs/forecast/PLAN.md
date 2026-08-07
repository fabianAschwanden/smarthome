# Umsetzungsplan – Use Case 15: PV-Prognose & vorausschauende Batterieladung

Gehört zu [`SPEC.md`](SPEC.md). Sechs Etappen, jede einzeln merge-bar („vertikale
Scheiben"), jede endet grün (`./mvnw verify` + ArchUnit + Coverage ≥ 0.70).
Referenz-Muster aus dem Bestand sind je Etappe verlinkt.

---

## Etappe 1 – Domäne: Modelle + Prognose-Rechnung (pur)

**Ziel:** Die ganze Fachlichkeit steht und ist getestet, bevor irgendein I/O existiert.

1. `domain/model/forecast/`: `PlantProfile` (factorPerHour[24], maxObservedPvWatt,
   learnedAt, confidence), `PvForecast` + `HourEntry`, `ConsumptionBaseline`
   (werktags/wochenende), `SurplusWindow`, `ChargeRecommendation`, `Confidence` (enum).
   Reine records, Invarianten im Compact-Constructor (Muster: `WeatherForecast`).
2. `domain/service/forecast/`: `PlantProfileLearner` (Paare → Median je Slot,
   Nachbar-Erben, Cold-Start-Fallback), `PvForecaster` (Faktor × GTI, Cap),
   `SurplusPlanner` (Baseline, Fenster, bestes Fenster) – zustandslos, `Clock`-frei
   (Zeitpunkte kommen als Parameter rein).
3. Tests (`@QuarkusTest`, Muster: `EnergyComparisonTest`): Lernen mit Ausreissern/
   Lücken, Cold Start, Fensterbildung (zu kurz / unter Schwelle / mehrere Fenster),
   Werktag-vs-Wochenend-Baseline, Cap-Greifen.

**Fertig wenn:** Prognose & Empfehlung aus synthetischen Samples + GTI-Reihen korrekt
berechnet werden; noch keine Ports nötig.

## Etappe 2 – Ports + Open-Meteo-Irradiance-Adapter

1. `domain/port/out/forecast/`: `IrradianceGateway` (`fetch(): Optional<IrradianceSeries>`
   mit Forecast-Stunden **und** past_days-Ist), `PlantProfileRepository`
   (`load(): Optional<PlantProfile>`, `save(...)`).
2. `adapter/out/irradiance/openmeteo/OpenMeteoIrradianceGateway`
   (`@IfBuildProperty(smarthome.real-devices, "true")`): ein HTTP-Call gemäss SPEC §2.1;
   Config-Klasse `IrradianceConfig` (tilt/azimuth aus `forecast.plant.*`). Muster:
   `OpenMeteoWeatherGateway`.
   **Korrektur zum ursprünglichen Plan:** Den Standort NICHT über die bestehende
   `WeatherConfig` beziehen – das wäre eine Adapter→Adapter-Abhängigkeit, die ArchUnit
   bricht (Blueprint §3.4). Stattdessen `weather.latitude`/`weather.longitude` als rohe
   Schlüssel lesen (Muster: `NativeProxy`); die beiden stehen dafür jetzt explizit in
   `application.properties`, weil `@ConfigProperty` die `@WithDefault` eines
   `@ConfigMapping` nicht sieht.
3. `adapter/out/irradiance/mock/MockIrradianceGateway` (`@UnlessBuildProperty`,
   enableIfMissing): synthetische Glockenkurve, deterministisch.
4. Tests: Adapter direkt instanziieren gegen lokalen Fake-HTTP-Server mit
   eingefrorenem Open-Meteo-JSON (Muster: `FroniusEnergySourceTest` /
   `OpenMeteoWeatherGatewayTest`) – Parsing, Fehler-Codes, Timeout → `Optional.empty()`.

**Fertig wenn:** echte und Mock-Strahlungsreihe über den Port abrufbar.

## Etappe 3 – Persistenz: PlantProfile als JSONB-Snapshot

1. Liquibase-Migration (append-only, neue Datei): Tabelle `plant_profile`
   (`id`, `profile jsonb`, `learned_at timestamptz`) – eine Zeile, Upsert.
2. `adapter/out/persistence/PlantProfileEntity` + Panache-Repository, implementiert
   `PlantProfileRepository`; nimmt/liefert das Domänen-record
   (Muster: `PanacheEnergySampleRepository`).
3. `@QuarkusTest` gegen Dev-Services-Postgres: save → load → Gleichheit; Upsert
   überschreibt.

**Fertig wenn:** Profil übersteht einen App-Neustart.

## Etappe 4 – Application-Services + Scheduling

1. `domain/port/in/forecast/`: `PvForecastQuery`, `SurplusQuery`, `ApplyRecommendation`.
2. `application/service/forecast/ForecastService` (`@ApplicationScoped`):
   `@Scheduled(every = "{forecast.refresh-interval}")` → Gateway holen, Prognose +
   Fenster rechnen, im RAM halten (`computedAt`); implementiert die Query-Ports.
   Gateway leer → letzte Prognose behalten (SPEC §6).
3. `ProfileLearningService`: `@Scheduled(cron = "{forecast.learning.cron}")` →
   Samples der `window-days` über den Energy-Port lesen
   (**Korrektur:** `EnergySampleRepository`, nicht `EnergyHistoryQuery` – letzterer kann
   strukturell nur DAY/WEEK/MONTH und liefert keine 21 Tage Roh-Stundenwerte), `PlantProfileLearner`,
   speichern, danach `ForecastService` neu rechnen lassen. Beim Start: Profil laden,
   fehlt es → Cold-Start-Fallback.
4. `ApplyRecommendation`-Implementierung: delegiert an `ManageBatterySchedules`
   (UC 14), kein eigener Schaltpfad; keine Empfehlung → Fach-Exception (→ 409).
5. Tests mit handgeschriebenen Fake-Ports (Muster: `BatteryControlServiceTest`,
   `EnergySamplerTest`): Refresh-Verhalten, Degradation bei Gateway-Ausfall,
   Cold Start, Apply-Delegation, Neu-Lernen aktualisiert Prognose.

**Fertig wenn:** Use Cases über die Ports vollständig bedienbar (ohne REST/UI).

## Etappe 5 – REST-Adapter

1. `adapter/in/rest/forecast/ForecastResource` + DTOs in
   `adapter/in/rest/dto/forecast/` gemäss SPEC §4 (Muster: `BatteryResource` inkl.
   409-Mapping über die bestehenden `ExceptionMappers`).
2. `@QuarkusTest` + REST-assured: Happy Path je Endpoint, 409 bei Apply ohne
   Empfehlung, DTO-Felder vollständig (Muster: `EnergyResourceTest`).
3. OpenAPI prüfen (`/q/swagger-ui`) – Beschreibungen an den Resources.

**Fertig wenn:** `curl /api/forecast/pv` im Mock-Modus eine plausible Kurve liefert.

## Etappe 6 – Frontend (Angular)

1. `core/models/forecast.ts` (spiegelt DTOs) + `core/services/forecast.service.ts`
   (Signals, Muster: bestehende Services).
2. **Dashboard:** Prognose-Kurve (heute) als zweite Linie/Fläche in der bestehenden
   Energie-Chart.js-Karte; Tages-Erwartung (kWh) + Confidence-Badge („grob" bei
   Cold Start).
3. **Batterie-Seite:** Empfehlungs-Karte – Fenster, erwartete kWh, Button
   „Als Zeitplan übernehmen" (ruft Apply, zeigt den angelegten Schedule; Glassmorphism-
   Bausteine aus `shared/`).
4. Vitest für Service + Komponenten-Logik; Playwright-Happy-Path (Mock-Modus):
   Dashboard zeigt Prognose, Apply legt Zeitplan an.

**Fertig wenn:** iPad-Flow rund: Prognose sehen → Empfehlung übernehmen → Zeitplan
erscheint in UC 14.

---

## Reihenfolge & Aufwand (grob)

| Etappe | Inhalt | Schätzung |
|---|---|---|
| 1 | Domäne pur | 1–2 Abende |
| 2 | Irradiance-Gateway | 1 Abend |
| 3 | Persistenz | 0.5 Abend |
| 4 | Services + Scheduling | 1–2 Abende |
| 5 | REST | 0.5 Abend |
| 6 | Frontend | 2 Abende |

Nach Etappe 5 ist der Use Case per API/Swagger nutzbar – Frontend kann getrennt folgen.

## Verifikation an der realen Anlage

1. `./mvnw quarkus:dev -Dquarkus.profile=dev,live` im Heimnetz: echte GTI-Reihe holen,
   Prognose gegen den heutigen Fronius-Ist-Verlauf halten.
2. Nach ~1 Woche Betrieb: gelerntes Profil anschauen (`plant_profile`-Zeile) –
   Verschattungs-Slots plausibel? `min-watt`/`min-duration` justieren (SPEC §8).
3. Erst danach: Empfehlung erstmals produktiv übernehmen (UC-14-Zeitplan prüfbar,
   jederzeit löschbar).

## Folgestufen (bewusst nicht in v1)

- ✅ **F1 Genauigkeit** *(2026-08-07)*: Prognose-vs-Ist je Tag persistiert
  (`forecast_accuracy`), MAPE auf der Energie-Seite. Die Prognose des Tages wird morgens
  um 07:00 festgeschrieben und danach nicht mehr angefasst – sonst schmiegte sie sich im
  Lauf des Tages an die Wirklichkeit an. Der Ist-Wert kommt nachts aus derselben
  Aggregation wie das Energie-Diagramm, damit beide Anzeigen nicht auseinanderlaufen.
- ✅ **F2 Auto-Apply** *(2026-08-07)*: Empfehlung automatisch als Zeitplan (Opt-in,
  Standard **aus**). **Die Genauigkeitsmessung aus F1 ist der Schutzschalter:**
  Geschaltet wird erst, wenn genug Tage ausgewertet sind *und* der mittlere Fehler unter
  der Schwelle liegt – sonst schaltete die Automatik ausgerechnet direkt nach der
  Inbetriebnahme los, wenn sie am wenigsten weiss. Die Nachrichtenzentrale meldet es,
  wenn die Automatik an ist und trotzdem nicht schaltet.
- ✅ **F3 Storen-Hitzeschutz** *(2026-08-07)*: GTI + Innentemperatur (UC 8) →
  Storen-Countdowns (UC 5). Beschattet wird nur, wenn **beides** zusammenkommt – ein
  strahlender Wintertag heizt das Haus nicht auf. Zugefahren wird auf **Geräte-Position 2
  («98 % zu»)**, nicht auf 0: Ein Spalt bleibt offen, der Behang sitzt nicht auf dem
  Anschlag auf und der Raum wird nicht völlig dunkel. Übernommen wird auf Knopfdruck;
  automatisch wird es erst mit F2.
- **F4 Wellness-Fenster:** Whirlpool-/Becken-Heizung (UC 6) in Überschussfenster legen.
