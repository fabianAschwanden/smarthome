# Spec – Use Case 9: Sicherheit (Rauchmelder)

Status: v1.0 (umgesetzt) · Datum: 2026-06-20 · Plattform: Java 25 / Quarkus (Hexagonal + DDD)

## 1. Zweck & Scope

Tuya-Rauchmelder **nur lesend** überwachen: Alarmzustand und Batteriestand.
Alarme erscheinen prominent (Dashboard-Banner + Nachrichtenzentrale, siehe unten).

## 2. Anbindung

Tuya-Rauchmelder über das lokale LAN-Protokoll (siehe `docs/tuya/SPEC.md`). dps:
`status-dp=1` (`"alarm"`/`"normal"`), `battery-dp=15` (%).

- `%dev`/`%test`: Mock.
- Echtbetrieb: `LocalTuyaSmokeDetector`.

### Deep-Sleep-Verhalten (wichtig)

Rauchmelder sind Batteriegeräte und schlafen tief – sie antworten kaum auf aktive
Abfragen, senden aber periodisch **UDP-Broadcasts**. Erreichbarkeit wird darum
**passiv** bewertet:

- `TuyaDiscovery` merkt sich je Gerät den Zeitpunkt der letzten Broadcast-Sichtung
  (`lastSeen`).
- Hat sich der Melder **einmal** gemeldet, gilt er als **online** und bleibt es, bis
  die letzte Sichtung **über eine Stunde** her ist (`SEEN_WINDOW = 65 min`).
- Zusätzlich hält der `SafetyService` nach der letzten erfolgreichen Abfrage eine
  kurze Toleranz (`OFFLINE_GRACE = 5 min`), damit einzelne Aussetzer nicht sofort
  „offline" bedeuten.
- Solange nur ein Broadcast (kein dp-Read) vorliegt, ist `alarm = OK` und der
  Batteriestand unbekannt (`-1`).

## 3. API (REST)

| Methode | Pfad                | Antwort                                          |
|---------|---------------------|--------------------------------------------------|
| GET     | `/api/safety/smoke` | Liste (id, name, room, alarm, battery, online)   |

`alarm` ∈ `OK`/`ALARM`. `battery` in %, `-1` = unbekannt.

## 4. Konfiguration

```properties
safety.smoke[0].id=rauchmelder
safety.smoke[0].name=Rauchmelder
safety.smoke[0].room=Wohnzimmer
safety.smoke[0].device-id=...   # nur in config/
safety.smoke[0].local-key=...   # nur in config/ (Secret)
safety.smoke[0].version=3.3
safety.smoke[0].status-dp=1
safety.smoke[0].battery-dp=15
```

## 5. Architektur-Einordnung (Hexagonal)

Slice `safety`: Port `ReadSafety` (in), `SmokeDetectorDevice` + Factory (out),
`SafetyService` (application), Adapter `adapter/in/rest/safety` und
`adapter/out/safety/{mock,local}`.

## 6. Nachrichtenzentrale (Frontend)

Geräte-Meldungen sind über die Glocke oben rechts einsehbar
(`NotificationCenterService` + `NotificationBell`). Aggregiert rein clientseitig aus
den vorhandenen Signalen: **Rauchmelder-Alarm** (rot, Glocke pulsiert), **Geräte
offline** und **niedriger Akku** (<20 %). Ein aktiver Rauchalarm erscheint zusätzlich
als prominentes Banner auf dem Dashboard.

## 7. Offene Punkte / TODO

- [ ] Optional: Offline-Meldung für Deep-Sleep-Geräte als „Info" statt „Warnung".
