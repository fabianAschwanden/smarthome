# Folgestufen – Use Case 15 (PV-Prognose): Konzept & Umsetzungsgrundlage

Status: v0.1 (Entwurf) · Datum: 2026-08-06 · Basis: UC 15 live (SPEC.md/PLAN.md), UC 16 live

Vier Ausbaustufen auf der laufenden Prognose. Jede Stufe ist ein eigener,
merge-barer Vertikalschnitt nach den bekannten Regeln (Hexagonal, pure Domain
Services, `@QuarkusTest`, Liquibase append-only). **Empfohlene Reihenfolge:
F1 → F3 → F2 → F4** — erst messen, wie gut die Prognose ist (F1), dann der
Sommer-Nutzen (F3), erst danach automatisch schalten lassen (F2, nutzt F1 als
Schutzschalter) und zuletzt Wellness (F4, teilt sich die Mechanik mit F2).

---

## F1 · Genauigkeits-Tracking (Prognose vs. Ist)

**Ziel:** Sichtbar machen, wie gut die Prognose wirklich ist — Vertrauensbasis
für F2/F4 und Tuning-Grundlage fürs Anlagenprofil.

**Konzept:** Einmal täglich (nach Tagesende) wird die Morgen-Prognose des Tages
gegen den Ist-Verlauf gehalten. Damit das die 45-Tage-Retention der EnergySamples
überlebt, werden **Tagesaggregate persistiert**, nicht Rohserien.

- `domain/model/forecast/`: `ForecastAccuracy` (record: date, forecastKwh,
  actualKwh, hourlyDeviations, mape) + `AccuracyCalculator` (Domain Service, pur:
  Stundenprognose × Ist-Stunden → MAPE/Abweichungen; nur Tagesstunden mit
  nennenswerter Prognose zählen, sonst verzerren Nachtstunden den MAPE).
- Voraussetzung: `ForecastService` legt morgens einen **Prognose-Snapshot des
  Tages** ab (die 06:00-Kurve), damit abends nicht gegen eine später
  aktualisierte Prognose verglichen wird. Persistenz beider Teile:
  Liquibase-Tabelle `forecast_accuracy` (eine Zeile/Tag, JSONB) + Snapshot-Spalte.
- Port `ForecastAccuracyRepository` (out), `AccuracyQuery` (in);
  `@Scheduled`-Job (23:50) im Application-Service.
- REST `GET /api/forecast/accuracy` (letzte N Tage + MAPE-7/30); UI: Badge an
  der Prognose-Karte («±12 % über 30 Tage») + Gestern-Vergleichschart.

**Etappen:** (1) Domäne + Calculator + Tests · (2) Snapshot + Persistenz + Job ·
(3) REST + UI. **Aufwand:** ~2 Abende.
**DoD:** Nach zwei Betriebstagen stehen echte MAPE-Werte im Dashboard.

## F2 · Auto-Apply der Ladeempfehlung (Opt-in)

**Ziel:** Das beste Überschussfenster wird ohne Klick als Batterie-Zeitplan
angelegt — die Automatik, die v1 bewusst ausgelassen hat.

**Konzept:** Kein neuer Schaltpfad — der bestehende `ApplyRecommendation`-Weg
wird von einem Morgen-Job ausgelöst, wenn der Nutzer es erlaubt hat:

- Setting `AutoApplySettings` (record: enabled, minConfidence, maxMape)
  persistiert nach dem `AlertSettings`-Muster; Port `ManageAutoApply` (in).
- `@Scheduled`-Job nach dem Prognose-Refresh (z. B. 06:30): legt den Zeitplan
  für heute an, **wenn** (a) Opt-in aktiv, (b) Confidence `LEARNED`,
  (c) F1-MAPE-30 unter `maxMape` (Schutzschalter: schlechte Prognose ⇒ keine
  Automatik), (d) noch kein Batterie-Zeitplan für heute existiert (manuelle
  Einträge gewinnen immer).
- Jede automatische Anlage erzeugt eine Meldung in der **Nachrichtenzentrale**
  («Ladefenster 11–15 Uhr automatisch angelegt») — nachvollziehbar und über die
  bestehende Glocke sichtbar; der Zeitplan bleibt manuell löschbar.
- UI: Toggle + Regeln auf der Batterie-Seite bei der Empfehlungs-Karte.

**Etappen:** (1) Settings + Port + Tests · (2) Job + Regelwerk (Fake-Ports:
alle vier Bedingungen einzeln) · (3) Meldung + UI. **Aufwand:** ~2 Abende.
**DoD:** Eine Woche Betrieb: Zeitpläne erscheinen morgens automatisch, manuelle
Eingriffe werden nie überschrieben.

## F3 · Storen-Hitzeschutz

**Ziel:** An Sonnentagen beschatten die Storen automatisch, bevor die Räume
aufheizen — der unmittelbarste Alltagsnutzen (und im August sofort erlebbar).

**Konzept:** Laufende Regel statt fixem Zeitplan — die Bedingungen ändern sich
mit Wetter und Innentemperatur:

- `ShadingPolicy` (Domain Service, pur): Eingaben Innen-/Aussentemperatur
  (`ReadSensors`), GTI-Stundenprognose (bestehender `PvForecastQuery`-Datenpfad)
  und Uhrzeit. Regeln (alles konfigurierbar):
  **beschatten**, wenn Innen ≥ `indoor-threshold` (z. B. 24 °C) **und** GTI der
  aktuellen Stunde ≥ `gti-threshold` (z. B. 400 W/m²);
  **freigeben**, wenn GTI unter die Schwelle fällt oder Innen ≤ Schwelle − 1 K
  (Hysterese gegen Flattern).
- `ShadingService` (Application, `@Scheduled` alle 5 min): wertet die Policy aus
  und fährt über `ControlCovers` auf die konfigurierte Beschattungsposition.
  **Manuell gewinnt:** Eine manuelle Storen-Bedienung (Dashboard, HomeKit oder
  Wandtaster) pausiert die Automatik für diesen Storen bis `override-pause`
  (z. B. 4 h) — erkannt am Positions-Delta gegenüber dem letzten Automatik-Ziel.
- Opt-in **pro Storen** (persistiertes Setting) + globaler Toggle; jede
  Automatik-Fahrt als Meldung in die Nachrichtenzentrale (dezent, gruppiert).
- Bewusst ohne Windsensor (nicht vorhanden) — dokumentierte Grenze; bei
  Lamellenstoren ist die Beschattungsposition < 100 % wählbar.

**Etappen:** (1) Policy pur + Tests (Schwellen, Hysterese, Tagesrand) ·
(2) Service + Override-Erkennung (Fake-Ports) · (3) Settings + UI + Meldungen ·
(4) Kalibrierung an echten Tagen (Schwellenwerte). **Aufwand:** ~3 Abende.
**DoD:** Ein Hitzetag: Storen fahren selbständig, ein manueller Eingriff wird
respektiert, abends ist alles wieder offen.

## F4 · Wellness-Heizung in Überschussfenstern

**Ziel:** Whirlpool/Becken heizen bevorzugt mit eigenem PV-Strom.

**Konzept:** Temperatur-Korridor statt Fixwert — die Automatik verschiebt nur
den **Zeitpunkt** des Heizens, nie das Komfortziel:

- `SurplusHeatingPolicy` (pur): Eingaben Ist-/Soll-Temperatur der Anlage
  (`ControlAppliances`), Überschussfenster (`SurplusQuery`), Uhrzeit.
  Regeln: im aktiven Überschussfenster heizen bis `comfort-max` (z. B. 38 °C,
  «Puffer laden»); ausserhalb nur heizen, wenn unter `comfort-min` (z. B. 36 °C);
  ab `deadline` (z. B. 17 Uhr) gilt unabhängig vom Fenster wieder das normale
  Soll (abends nie kalt sitzen).
- `SurplusHeatingService` (`@Scheduled`, 10 min-Takt), Opt-in je Anlage
  (Whirlpool/Becken getrennt), Meldungen in die Nachrichtenzentrale.
- **Abgrenzung zu HomeKit (UC 16 F1):** Beide Kanäle setzen dasselbe Soll über
  `ControlAppliances` — eine manuelle Soll-Änderung (Dashboard/HomeKit) pausiert
  die Automatik analog F3 (`override-pause`). Ein Regelwerk, egal woher der
  Eingriff kommt.

**Etappen:** (1) Policy pur + Tests (Korridor, Deadline, Fensterwechsel) ·
(2) Service + Override · (3) Settings + UI. **Aufwand:** ~2–3 Abende.
**DoD:** Sonniger Tag: Heizzyklen liegen sichtbar in den Überschussfenstern
(Grafana: `smarthome_pv_watt` vs. Heizzeiten), abends stimmt die Temperatur.

---

## Gemeinsame Grundlagen (einmal bauen, dreimal nutzen)

F2/F3/F4 teilen zwei Muster, die in der ersten der drei Stufen entstehen und
wiederverwendet werden:

1. **Opt-in-Settings-Persistenz** nach `AlertSettings`-Vorbild (record + Port +
   JSONB/Tabelle + PUT-Endpoint + UI-Toggle).
2. **Override-Erkennung** («manuell gewinnt, Automatik pausiert») — als kleine,
   pure Hilfsklasse im Domänenkern (`AutomationOverride`: letztes Automatik-Ziel,
   Ist-Abweichung, Pausen-Ablauf), von F3 und F4 genutzt.

Alle Automatik-Aktionen laufen über die **bestehenden Driving Ports** und die
**Nachrichtenzentrale** — kein neuer Schalt- oder Meldeweg, volle
Nachvollziehbarkeit im Dashboard.
