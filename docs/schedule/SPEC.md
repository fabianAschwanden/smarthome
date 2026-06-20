# Spec – Use Case 4: Zeitsteuerung der Schalter

Status: v1.0 (umgesetzt) · Datum: 2026-06-20 · Plattform: Java 25 / Quarkus (Hexagonal + DDD)

## 1. Zweck & Scope

Tuya-Schalter (Use Case 3) **zeitgesteuert** schalten. Vier Regeltypen je Schalter,
persistiert in der Datenbank, ausgewertet von einem Scheduler-Tick.

| Typ         | Bedeutung                                                              |
|-------------|-----------------------------------------------------------------------|
| `SCHEDULE`  | feste Uhrzeit (optional an Wochentagen), Aktion EIN/AUS               |
| `COUNTDOWN` | einmalig zu einem Zeitpunkt (`fire-at`); deaktiviert sich danach      |
| `RANDOM`    | zufällige Zeit in einem Fenster (`window-start`..`window-end`)        |
| `INCHING`   | Puls: schaltet EIN und nach `pulse-seconds` automatisch wieder AUS    |

Idempotenz: SCHEDULE/RANDOM feuern höchstens einmal pro Tages-Slot; COUNTDOWN und
INCHING sind einmalig und deaktivieren sich nach dem Auslösen selbst.

## 2. API (REST)

| Methode | Pfad                                  | Body / Antwort                       |
|---------|---------------------------------------|--------------------------------------|
| GET     | `/api/schedules?switchId=<id>`        | Regeln eines Schalters               |
| POST    | `/api/schedules`                      | Regel anlegen (Typ-spezifische Felder) |
| PUT     | `/api/schedules/{id}/enabled/{bool}`  | aktivieren/deaktivieren              |
| DELETE  | `/api/schedules/{id}`                 | löschen                              |

400 bei ungültigen/fehlenden Feldern je Typ, 404 bei unbekannter Regel-ID.

## 3. Persistenz

Tabelle `switch_schedule` (Liquibase `0002-create-switch-schedule.xml`); ein Tisch
deckt alle Typen ab (typ-spezifische Spalten nullable). Repository nimmt/liefert
Domänen-Modelle (`SwitchSchedule`), nie JPA-Entities.

## 4. Ausführung

`ScheduleService` ist Application-Service **und** Träger des `@Scheduled`-Ticks
(`schedule.tick-interval`, Default 30 s). Die reine Fälligkeitsprüfung (`isDue`) ist
gekapselt und unit-getestet. Beim Auslösen wird der Use-Case `ControlSwitches`
aufgerufen (echtes Schalten via Tuya-Adapter).

## 5. Architektur-Einordnung (Hexagonal)

Slice `schedule`: Port `ManageSchedules` (in), `ScheduleRepository` (out),
`ScheduleService` (application), Adapter `adapter/in/rest/schedule` +
`adapter/out/persistence` (`PanacheScheduleRepository`, `SwitchScheduleEntity`).

## 6. Offene Punkte / TODO

- [ ] Optional: Zeitsteuerung auch für Storen/Klima (derzeit nur Schalter).
- [ ] Optional: UI zum Verwalten der Regeln (aktuell REST + Schalter-Detailseite).
