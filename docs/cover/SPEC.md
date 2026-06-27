# Spec – Use Case 5: Storensteuerung (Tuya-Cover, lokal)

Status: v1.1 (umgesetzt; UI 100 % = zu) · Datum: 2026-06-20 · Plattform: Java 25 / Quarkus (Hexagonal + DDD)

## 1. Zweck & Scope

Über die Smart-Life-/Tuya-App gekoppelte **Storen/Jalousien** lokal im LAN steuern:
Auf/Ab/Stopp und auf eine Prozent-Position fahren; aktuelle Position anzeigen.

In Scope: Auf/Ab/Stopp, Position 0–100 %, Ist-Position-Anzeige, Mock-Modus.
Out of Scope (später): Lamellen-Winkel, Gruppen/Szenen.

## 2. Anbindung: Tuya-Cover (Datenpunkte)

Wie die Schalter rein lokal (kein Internet), über die geteilten Protokoll-Klassen
in `support.tuya` (v3.3/v3.4). Standard-Datenpunkte einer Tuya-Store:

| dp           | Bedeutung                                            | Default |
|--------------|------------------------------------------------------|---------|
| `control-dp` | Grundbefehl als String: `"open"`/`"close"`/`"stop"`  | 1       |
| `position-dp`| Soll-Position (percent_control, 0..100)              | 2       |
| `state-dp`   | Ist-Position (percent_state, 0..100)                 | 3       |

Manche Geräte nutzen abweichende dps (z. B. 101). Pro Gerät konfigurierbar; gegen
die reale Store verifizieren (tinytuya `status` zeigt die belegten dps).

**Positionskonvention:**
- **REST/Domäne (Geräteskala):** die rohe Tuya-Position über `cover.devices[i].position`
  bzw. den `state-dp`. `-1` = unbekannt.
- **UI (`webapp`):** zeigt „% geschlossen" mit **`100` = ganz zu, `0` = offen**. Die
  Spiegelung passiert in der Frontend-Komponente (`100 − Geräteposition`), inkl.
  Slider, Status-Text und Lamellen-Visualisierung. Beim Setzen wird zurückgerechnet.
- Welcher dp die echte Ist-Position trägt, ist gerätespezifisch (`state-dp`); bei
  der realen Store verifizieren – manche Module melden über einen anderen dp einen
  konstanten Wert statt der Position.

## 3. API (REST)

| Methode | Pfad                          | Body / Antwort                          |
|---------|-------------------------------|-----------------------------------------|
| GET     | `/api/covers`                 | Liste (id, name, room, position, online)|
| POST    | `/api/covers/{id}/command`    | `{ "command": "OPEN"\|"CLOSE"\|"STOP" }`|
| POST    | `/api/covers/{id}/position`   | `{ "position": 0..100 }`                |

404 bei unbekannter ID, 503 wenn die Store nicht erreichbar ist.

### Zeitsteuerung der Storen

Pro Store lassen sich Zeitsteuerungs-Regeln hinterlegen, die die Store zu einer
festen Zeit (oder einmalig per Countdown) auf eine Zielposition fahren – z. B.
nachts geschlossen, morgens um 06:30 kurz öffnen und bei 98 % „zu"
(Geräte-Position 2) anhalten. Ein direkter Positionsbefehl fährt die Store auf
genau diese Position und stoppt dort; es braucht keine Auf/Stopp-Sequenz.

| Methode | Pfad                                       | Body / Antwort                          |
|---------|--------------------------------------------|-----------------------------------------|
| GET     | `/api/cover-schedules?coverId={id}`        | Liste der Regeln (optional je Store)    |
| POST    | `/api/cover-schedules`                      | `{ coverId, type, position, time?, weekdays?, countdownSeconds? }` |
| PUT     | `/api/cover-schedules/{id}/enabled/{bool}` | Regel aktivieren/deaktivieren           |
| DELETE  | `/api/cover-schedules/{id}`                | Regel löschen                           |

`type`: `SCHEDULE` (feste Uhrzeit, optional Wochentage – leer = täglich) oder
`COUNTDOWN` (einmalig, deaktiviert sich nach dem Auslösen selbst). `position` in
Geräteskala (0 = zu, 100 = offen); die UI rechnet in „% zu" und invertiert beim
Speichern. Ein Scheduler-Tick (`cover-schedule.tick-interval`, Default 30 s) wertet
die aktiven Regeln aus; SCHEDULE feuert höchstens einmal pro Minuten-Slot.

## 4. Konfiguration

```properties
cover.devices[0].id=store1
cover.devices[0].name=Store 1
cover.devices[0].room=Wohnzimmer
cover.devices[0].device-id=...
cover.devices[0].local-key=...   # 16 Zeichen, NUR in config/ (Secret)
cover.devices[0].address=192.168.x.x
cover.devices[0].version=3.3
cover.devices[0].control-dp=1
cover.devices[0].position-dp=2
cover.devices[0].state-dp=3
```

Mock vs. echt über `smarthome.real-devices` (wie Schalter): Mock-Factory
`@UnlessBuildProperty(..., enableIfMissing=true)`, lokale Factory
`@IfBuildProperty(... "true")`. Geräte ohne local-key erscheinen offline.

## 5. Architektur-Einordnung (Hexagonal)

Eigener Slice `cover`: Treiber-Port `ControlCovers` (`domain/port/in/cover`),
getriebener Port `CoverDevice` + `CoverDeviceFactory` (`domain/port/out/cover`),
Application-Service `CoverControlService` (`application/service/cover`), Adapter
`adapter/in/rest/cover` und `adapter/out/cover/{local,mock}`. Das Tuya-LAN-Protokoll
liegt geteilt in `support.tuya` (von Schalter- UND Storen-Adapter genutzt, ohne
Adapter-Querverweis – ArchUnit-konform).

## 6. Offene Punkte / TODO

- [ ] Reale Storen identifizieren (tinytuya scan + device-id/local-key auslesen,
      wie bei den Lampen). IP per DHCP-Reservierung fixieren.
- [ ] Belegte dps je Store bestätigen (1/2/3 vs. 101 …) und ggf. Positionsrichtung
      invertieren.
- [x] Zeitsteuerung der Storen (eigene Slice `cover-schedule`, je Store, SCHEDULE + COUNTDOWN).
