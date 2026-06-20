# Spec – Use Case 5: Storensteuerung (Tuya-Cover, lokal)

Status: Entwurf v1.0 · Datum: 2026-06-19 · Plattform: Java 25 / Quarkus (app-template: Hexagonal + DDD)

## 1. Zweck & Scope

Über die Smart-Life-/Tuya-App gekoppelte **Storen/Jalousien** lokal im LAN steuern:
Auf/Ab/Stopp und auf eine Prozent-Position fahren; aktuelle Position anzeigen.

In Scope: Auf/Ab/Stopp, Position 0–100 %, Ist-Position-Anzeige, Mock-Modus.
Out of Scope (später): Lamellen-Winkel, Gruppen/Szenen, Zeitsteuerung der Storen.

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

**Positionskonvention (App):** `0` = geschlossen, `100` = offen, `-1` = unbekannt.
(Tuya-intern variiert die Richtung gerätespezifisch – ggf. invertieren.)

## 3. API (REST)

| Methode | Pfad                          | Body / Antwort                          |
|---------|-------------------------------|-----------------------------------------|
| GET     | `/api/covers`                 | Liste (id, name, room, position, online)|
| POST    | `/api/covers/{id}/command`    | `{ "command": "OPEN"\|"CLOSE"\|"STOP" }`|
| POST    | `/api/covers/{id}/position`   | `{ "position": 0..100 }`                |

404 bei unbekannter ID, 503 wenn die Store nicht erreichbar ist.

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
- [ ] Optional: Zeitsteuerung (Use Case 4) auf Storen erweitern.
