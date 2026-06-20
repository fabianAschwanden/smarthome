# Spec – Use Case 3: Tuya / Smart-Life-Schalter (lokal)

Status: Entwurf v1.0 · Datum: 2026-06-19 · Plattform: Java 25 / Quarkus (app-template: Hexagonal + DDD)

## 1. Zweck & Scope

Einen über die **Smart-Life-/Tuya-App** gekoppelten WLAN-Schalter (Steckdose/Relais)
aus dieser App **lokal im LAN** ein- und ausschalten und seinen Zustand anzeigen.

In Scope (v1):

- Ein Tuya-Schalter EIN/AUS (REST + Dashboard)
- Ist-Zustand vom Gerät zurücklesen (Status)
- Mock-Modus zum Testen ohne Hardware

Out of Scope (später): mehrere Geräte/Liste, Dimmer/Farbe, Energiemessung der
Steckdose, Szenen/Automatik, Cloud-API.

## 2. Anbindung: lokal (Tuya-LAN-Protokoll)

Tuya-Geräte sprechen primär mit der Tuya-Cloud, bieten aber eine lokale LAN-API.
Diese App nutzt **ausschliesslich den lokalen Weg** (kein Internet, wie SMARTFOX/Fronius).

Benötigt je Gerät:

| Wert        | Bedeutung                                                        |
|-------------|------------------------------------------------------------------|
| `device-id` | eindeutige Tuya-Geräte-ID                                        |
| `local-key` | gerätespezifischer Schlüssel (AES) für die lokale Kommunikation  |
| `address`   | IP des Geräts im LAN (am besten per DHCP-Reservierung fixieren)  |
| `version`   | Protokollversion (`3.3` / `3.4` / `3.5`, gerätespezifisch)       |
| `dp`        | „Data Point"-ID des Schalters (meist `1` für reine Schalter)     |

**Netzwerk:** das LAN/Firewall muss Tuya-Traffic erlauben (TCP 6668; Discovery
UDP 6666/6667/7000).

### 2.1 device-id und local-key beschaffen (einmalig)

Der `local-key` ist nicht in der App sichtbar; er wird über die Tuya-IoT-Plattform
ausgelesen:

1. Gerät in der **Smart Life**- (oder **Tuya Smart**-) App koppeln.
2. Auf <https://iot.tuya.com> ein (kostenloses) Cloud-Projekt anlegen, Region wählen.
3. Die Smart-Life-App per „Link App Account" mit dem Projekt verknüpfen.
4. Unter *Devices* erscheinen `device-id` und `local-key` der gekoppelten Geräte.
   (Alternativ liefert der `tinytuya wizard` eine `devices.json` mit denselben Werten.)
5. Diese Werte in die Konfiguration eintragen (§5). Die Cloud wird danach **nicht**
   mehr benötigt – der Betrieb läuft rein lokal.

## 3. Modell & Verhalten

- `SwitchState`: `ON` | `OFF`.
- Schalten ist idempotent: ein erneutes EIN auf ein bereits eingeschaltetes Gerät
  ist erlaubt und ohne Nebenwirkung.
- Status-Readback: nach dem Schalten und beim Polling wird der Ist-Zustand vom
  Gerät gelesen; ist das Gerät nicht erreichbar, gilt der Schalter als `OFFLINE`.

## 4. API (REST) – mehrere Geräte

| Methode | Pfad                  | Body / Antwort                              |
|---------|-----------------------|---------------------------------------------|
| GET     | `/api/switches`       | Liste aller Geräte (id, name, room, state, online) |
| POST    | `/api/switches/{id}`  | `{ "state": "ON" \| "OFF" }` (404 bei unbekannter ID) |

## 5. Konfiguration (`application.properties`)

Beliebig viele Geräte als Liste (`@ConfigMapping`). local-keys sind Secrets und
gehören in die gitignorete `config/application.properties` (siehe
`config/application.properties.example`), nicht in `src/main/resources`:

```properties
tuya.devices[0].id=stehlampe
tuya.devices[0].name=Stehlampe
tuya.devices[0].room=Wohnzimmer
tuya.devices[0].device-id=...
tuya.devices[0].local-key=...   # 16 Zeichen, NUR in config/ (Secret)
tuya.devices[0].address=192.168.x.x
tuya.devices[0].version=3.3
tuya.devices[0].dp=1
```

Mock vs. echtes Gerät über die Build-Property `smarthome.real-devices` (wie bei
SMARTFOX/Fronius): Mock-Factory `@UnlessBuildProperty(..., enableIfMissing=true)`,
lokale Factory `@IfBuildProperty(... "true")`. In `%dev`/`%test` → Mock.

## 6. Architektur-Einordnung (Hexagonal)

Eigener Slice `tuya`: Treiber-Port `ControlSwitches` (`domain/port/in/tuya`),
getriebener Port `SwitchDevice` + `SwitchDeviceFactory` (`domain/port/out/tuya`),
Application-Service `SwitchControlService` (`application/service/tuya`), Adapter
`adapter/in/rest/tuya` und `adapter/out/tuya/{local,mock}`. Der echte Adapter
spricht Protokoll 3.3 (`TuyaProtocol`) und 3.4 (`Tuya34Protocol`/`Tuya34Session`,
Session-Key-Handshake) – Version je Gerät konfigurierbar.

## 6a. Inbetriebnahme-Erfahrung (wichtig!)

- **IP-Findung ist der häufigste Stolperstein.** Die in der Smart-Life-App unter
  „Debug" angezeigte Adresse ist die *öffentliche* IP, nicht die LAN-IP. Die echte
  IP wandert zudem (DHCP) – unbedingt im Router **fest reservieren**. Ein falsch
  adressiertes anderes Tuya-Gerät antwortet zwar auf Port 6668, aber die
  Entschlüsselung scheitert dann → wirkt wie „falscher Key".
- **Protokollversion empirisch bestimmen.** Die Stehlampe spricht **3.3** (nicht
  3.4, wie zwischenzeitlich vermutet). Schnellster Check mit dem Diagnose-Tool
  `tinytuya` (venv `.tuya-venv`, gitignored): `OutletDevice(id, ip, key)` über
  3.3/3.4/3.5 durchprobieren – die Version, die ein `{'dps': ...}` liefert, ist es.
- Nur **eine** TCP-Verbindung gleichzeitig: Während die App pollt, schlägt ein
  paralleler Diagnose-Connect fehl. Für Probes die App kurz stoppen.

## 7. Offene Punkte / TODO

- [ ] **Echter LAN-Adapter:** Tuya-LAN-Protokoll (3.3/3.4) in reinem Java
      implementieren (AES-ECB/GCM, Handshake/Session-Key, Befehl `7` = set,
      `10`/`13` = status). Es gibt keine gepflegte Java-Bibliothek; Referenz ist
      `tinytuya` (Python). Vorerst Skelett mit dokumentiertem TODO.
- [ ] `device-id` / `local-key` / `version` der realen Geräte beschaffen (§2.1).
- [ ] Protokollversion je Gerät bestätigen (3.3 vs 3.4/3.5 – Krypto unterscheidet sich).
- [ ] `dp`-ID des Schalt-Datenpunkts verifizieren (meist `1`).

## 8. Nächste Schritte

1. Slice + Mock + UI im Mock-Modus testen (sofort lokal lauffähig).
2. Tuya-IoT-Projekt anlegen, `device-id`/`local-key` auslesen (§2.1).
3. Echten LAN-Adapter implementieren und gegen das Gerät verifizieren.
4. IP fixieren (DHCP-Reservierung), Werte in `%live` eintragen.
