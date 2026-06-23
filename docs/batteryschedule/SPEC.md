# Spec – Use Case 14: Zeitsteuerung der Batterie

Status: v1.0 · Datum: 2026-06-23 · Plattform: Java 25 / Quarkus (Hexagonal + DDD)

## 1. Zweck & Scope

Das Batterie-Lade-Relais (Use Case 2) **zeitgesteuert** schalten – analog zur
Schalter-Zeitsteuerung (Use Case 4), aber auf die Batterie zugeschnitten.

| Typ        | Bedeutung                                                       |
|------------|----------------------------------------------------------------|
| `SCHEDULE` | feste Uhrzeit (optional an Wochentagen), Aktion Laden EIN/AUS  |
| `COUNTDOWN`| einmalig zu einem Zeitpunkt; deaktiviert sich danach           |

RANDOM/INCHING (bei Schaltern vorhanden) sind für die Batterie unüblich und bewusst
weggelassen.

## 2. Aktion: Relais EIN/AUS (im Manuell-Modus)

Eine Regel schaltet das Lade-Relais auf `ON`/`OFF`. Da manuelles Schalten nur im
**MANUAL**-Modus erlaubt ist (sonst besitzt die PV-Überschuss-Automatik den Zustand),
versetzt die Ausführung die Batterie **zuerst in MANUAL** und schaltet dann das Relais.
Ein zeitgesteuertes „Laden AUS um 22:00" überschreibt also die Automatik bewusst.

## 3. API (REST)

| Methode | Pfad                                          | Body / Antwort                       |
|---------|-----------------------------------------------|--------------------------------------|
| GET     | `/api/battery-schedules`                      | alle Regeln                          |
| POST    | `/api/battery-schedules`                      | Regel anlegen (typ-spezifische Felder) |
| PUT     | `/api/battery-schedules/{id}/enabled/{bool}`  | aktivieren/deaktivieren              |
| DELETE  | `/api/battery-schedules/{id}`                 | löschen                              |

400 bei ungültigen/fehlenden Feldern je Typ (globaler `IllegalArgumentException`-Mapper),
404 bei unbekannter ID (`BatteryScheduleNotFound`).

## 4. Persistenz

Tabelle `battery_schedule` (Liquibase `0006-create-battery-schedule.xml`); ein Tisch
deckt SCHEDULE und COUNTDOWN ab (typ-spezifische Spalten nullable). Repository
nimmt/liefert Domänen-Modelle (`BatterySchedule`), nie JPA-Entities.

## 5. Ausführung

`BatteryScheduleService` ist Application-Service **und** Träger des `@Scheduled`-Ticks
(`battery-schedule.tick-interval`, Default 30 s). Die reine Fälligkeitsprüfung `isDue`
ist gekapselt und unit-testbar. Idempotenz: SCHEDULE feuert höchstens einmal pro
Tag-Slot (Merker im Speicher); COUNTDOWN ist einmalig und deaktiviert sich selbst.
Die Ausführung ruft den `ControlBattery`-Port (`changeMode(MANUAL)` + `switchRelay`).

## 6. Architektur (Hexagonal)

- `domain/model/batteryschedule/{BatterySchedule,BatteryScheduleType}` – Aggregat + Typ.
- `domain/port/in/batteryschedule/{ManageBatterySchedules,BatteryScheduleNotFound}`.
- `domain/port/out/batteryschedule/BatteryScheduleRepository`.
- `application/service/batteryschedule/BatteryScheduleService` – CRUD + Tick.
- `adapter/out/persistence/{BatteryScheduleEntity,PanacheBatteryScheduleRepository}`.
- `adapter/in/rest/batteryschedule/{BatteryScheduleResource,BatteryScheduleNotFoundMapper}` (+ dto).

## 7. Frontend

`features/battery/battery-schedule-page.ts` (Route `/battery/schedule`): Liste + Anlegen
je Typ (Zeitplan/Countdown), Aktion Laden EIN/AUS. Verlinkt von der Batterie-Seite
(„Zeitsteuerung →").
