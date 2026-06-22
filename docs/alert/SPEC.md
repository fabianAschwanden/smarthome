# Spec – Use Case 12: Alerts (Push bei kritischem Alarm)

Status: v1.0 (ntfy.sh) · Datum: 2026-06-22 · Plattform: Java 25 / Quarkus (Hexagonal + DDD)

## 1. Zweck & Scope

Bei einem **kritischen Alarm** (aktuell: Rauchalarm) eine **Push-Benachrichtigung
aufs Handy** senden. Auf einer Einstellungsseite lässt sich das Ganze ein-/ausschalten
und der Push-Topic festlegen; eine Test-Benachrichtigung kann ausgelöst werden.

In Scope: An/Aus, ntfy-Topic, Test-Push, automatischer Push bei Rauchalarm.
Out of Scope (später): weitere Auslöser (offline/Akku), mehrere Empfänger,
Ruhezeiten, andere Push-Kanäle.

## 2. Push-Weg: ntfy.sh

Der Push läuft über **[ntfy.sh](https://ntfy.sh)** – ein simpler Pub/Sub-Push-Dienst:
Das Backend sendet einen HTTP-POST an `https://ntfy.sh/<topic>`, auf dem Handy
abonniert die kostenlose **ntfy-App** denselben Topic. Kein Account, kein API-Key.

- Basis-URL konfigurierbar: `alert.ntfy.base-url` (Default `https://ntfy.sh`) – für
  einen selbstgehosteten ntfy-Server.
- **Topic = Geheimnis:** Wer den Topic kennt, sieht die Meldungen → langen, schwer
  erratbaren Namen wählen. Der Topic steht nur in der DB, nicht im Repo.

## 3. Erkennung & Flankenlogik

Ein **Scheduler** (`alert.tick-interval`, Default 30 s) prüft die Rauchmelder
(`ReadSafety`). Beim Übergang **OK→ALARM** wird **einmalig** gepusht
(Flankenerkennung pro Melder); solange der Alarm ansteht, kein erneuter Push. Klingt
der Alarm ab, wird der Melder wieder „scharf". So entsteht keine Push-Flut.

Push nur, wenn `enabled` **und** ein Topic gesetzt ist (`AlertSettings.canPush()`).

## 4. Architektur (Hexagonal)

- `domain/model/alert/AlertSettings` – record `(enabled, ntfyTopic)` + `canPush()`.
- `domain/port/in/alert/ManageAlertSettings` – `current() / save() / sendTest()`.
- `domain/port/out/alert/AlertSettingsRepository` – persistiert (eine Zeile).
- `domain/port/out/alert/AlertPublisher` – versendet den Push.
- `application/service/alert/AlertService` – implementiert den Use Case + Scheduler.
- `adapter/out/alert/NtfyAlertPublisher` – HTTP-POST an ntfy (Titel/Priorität als Header).
- `adapter/out/persistence/{AlertSettingsEntity,PanacheAlertSettingsRepository}` –
  Singleton-Row (Liquibase `0005-create-alert-settings`).
- `adapter/in/rest/alert/AlertResource` – `GET/PUT /api/alert-settings`, `POST …/test`.

## 5. REST

| Methode | Pfad | Zweck |
|---------|------|-------|
| GET  | `/api/alert-settings`      | aktuelle Einstellungen |
| PUT  | `/api/alert-settings`      | speichern (`{enabled, ntfyTopic}`) |
| POST | `/api/alert-settings/test` | Test-Push; 204 ok, 400 wenn nicht möglich |

## 6. Frontend

`features/settings/settings-page.ts` (Route `/settings`, Zahnrad in der Nav):
Ein/Aus-Toggle, Topic-Eingabe, Speichern + „Test senden". Test ist erst nach
Speichern und nur bei aktivem Topic möglich.

## 7. Einrichtung (Handy)

1. **ntfy-App** installieren (iOS/Android, kostenlos).
2. In den Einstellungen Alerts aktivieren, Topic eintragen, speichern.
3. In der ntfy-App denselben Topic abonnieren.
4. „Test senden" – die Nachricht muss am Handy ankommen.
