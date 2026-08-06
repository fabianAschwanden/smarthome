# Spec – Use Case 16: HomeKit-Bridge (Siri, Home-App, Apple-Automationen)

Status: v0.1 (Entwurf) · Datum: 2026-08-05 · Plattform: Java 25 / Quarkus (Hexagonal + DDD)

## 1. Zweck & Scope

Die vorhandenen Geräte werden Apple-nativ bedienbar: **Siri** («Storen runter»),
**Home-App** auf iPhone/iPad/Watch/Mac und **Apple-Automationen** mit Apple TV /
HomePod als Home-Hub (Zeitpläne, Geofencing, Szenen) – ohne die eigene Architektur
oder das Dashboard aufzugeben. Die App publiziert dafür das **HomeKit Accessory
Protocol (HAP)** direkt im LAN; keine Apple-Cloud-Anbindung, keine Hersteller-Cloud.

Architektur-Entscheid (Aug 2026): **Driving Adapter in der bestehenden App**
(`adapter/in/homekit`) statt separatem Deployable – ein Deployable bleibt ein
Deployable, der Adapter ruft dieselben Driving Ports wie REST, und `hostNetwork`
liefert das nötige mDNS/Bonjour gratis.

In Scope (v1) – als **eine** HomeKit-Bridge mit Accessories:

| Gerät (Use Case) | HomeKit-Profil | Port (vorhanden) |
|---|---|---|
| 5× Tuya-Schalter (UC 3) | Switch | `ControlSwitches` |
| Storen (UC 5) | WindowCovering (Position + Halten) | `ControlCovers` |
| Klimaanlage (UC 7) | HeaterCooler (Ein/Aus, Modus, Soll-/Ist-Temp) | `ControlClimate` |
| Innen-/Aussensensor (UC 8) | TemperatureSensor + HumiditySensor | `ReadSensors` |
| Rauchmelder (UC 9) | SmokeSensor (+ StatusLowBattery) | `ReadSafety` |

Out of Scope (bewusst, siehe PLAN.md §Folgestufen): **Kameras** (HomeKit Secure
Video ist ein eigenes Grossprojekt), **Wellness** (kein passendes HomeKit-Profil;
später ggf. als HeaterCooler + Switches gemappt), **Energie/Batterie/PV** (kennt
HomeKit nicht – bleibt Dashboard-exklusiv), Matter.

## 2. Technik: HAP-Java

- Bibliothek: **`io.github.hap-java:hap` 2.0.4** – Java-Implementierung des
  HAP-Servers; produktiv erprobt (openHAB-HomeKit-Addon basiert darauf).
  Inoffizielle HAP-Implementierung, für den Privatgebrauch etabliert.
- Muster: `HomekitServer` → eine **Bridge** → Accessories je Gerät. Advertisement
  via mDNS (`_hap._tcp`), eigener TCP-Port (Default hier: **9123**, nicht 8080).
- **Voraussetzung Netz:** iPhone/Apple TV und der Server müssen im selben
  L2-Netz sein (mDNS) – gegeben: alles hängt im AmpliFi-Subnetz `192.168.113.x`,
  die App läuft mit `hostNetwork`. ufw: Port 9123/tcp + mDNS 5353/udp aus dem LAN.
- **Pairing:** Setup-Code (Format `XXX-XX-XXX`) wird beim Koppeln in der Home-App
  eingegeben. Der Kopplungszustand (MAC, Salt, Private Key, Pairings) muss
  Neustarts überleben → Persistenz (§4), sonst verliert die Home-App die Bridge
  bei jedem Deploy.

## 3. Verhalten & Mapping-Regeln

- **Ein Weg für Zustand:** Der Adapter hält keinen eigenen Gerätezustand; jede
  Anfrage/Änderung geht über die Driving Ports. Damit gelten automatisch dieselben
  Regeln wie im Dashboard (z. B. `pending`-Geräte erscheinen als «nicht erreichbar»).
- **Storen-Invertierung:** UI/Domäne rechnen 100 % = **zu**, HomeKit 100 % =
  **offen** → der Adapter invertiert (`homekit = 100 − domain`). Ein einziger
  Umrechnungsort, getestet.
- **Klima-Mapping:** Midea-Modi ↔ HeaterCooler-States (Auto/Heat/Cool; Fan-only
  v1 aussen vor). Soll-Temperatur-Schritte wie im Dashboard (0.5 °C).
- **Push statt Poll wo möglich:** HomeKit-Clients abonnieren Characteristics.
  v1: ein leichter Refresh-Loop im Adapter (`homekit.refresh-interval`, Default
  10 s) liest über die Ports und feuert Callbacks nur bei **Änderung** (gleiches
  Muster wie der Battery-Sync). Kein neues Eventing im Domänenkern nötig.
- **Rauchmelder:** `SmokeDetected` + `StatusLowBattery`; die bestehende
  Nachrichtenzentrale/ntfy bleibt unverändert parallel bestehen (HomeKit ist ein
  zusätzlicher Kanal, kein Ersatz).

## 4. Persistenz (Pairing-Zustand)

Neuer Driven Port `HomekitStateRepository` (`domain/port/out/homekit/`):
`load(): Optional<HomekitState>`, `save(HomekitState)`. `HomekitState` ist ein
reines Domänen-record (mac, salt, privateKey, pairings als Map) und wird als
**JSONB-Snapshot** persistiert – eine Liquibase-Migration (`homekit_state`,
eine Zeile, Upsert; gleiches Muster wie `plant_profile` aus UC 15).
Der Setup-Code selbst ist **Config**, nicht DB (§5).

## 5. Konfiguration (`application.properties`)

```properties
homekit.enabled=false                  # Default aus; %lan.homekit.enabled=true
homekit.name=Smarthome                 # Bridge-Name in der Home-App
homekit.port=9123
homekit.pin=<XXX-XX-XXX>               # Setup-Code – gitignored config, wie local-keys
homekit.refresh-interval=10s
```

`homekit.enabled` ist eine **Laufzeit**-Property (kein `@IfBuildProperty`):
So lässt sich die Bridge auch im `%dev`-Mock-Modus starten und mit dem iPhone
gegen **Mock-Geräte** koppeln – Entwickeln/Testen ohne Anlage. In `%test` bleibt
sie aus (kein mDNS im CI).

## 6. Nicht-funktionale Anforderungen

- Läuft vollständig lokal; kein Cloud-Kontakt, kein neuer offener Router-Port
  (nur LAN: 9123/tcp, 5353/udp).
- Bridge-Ausfall/Neustart: Home-App zeigt «keine Antwort», fängt sich nach dem
  Start selbst (mDNS-Re-Announce); Pairing bleibt dank §4 erhalten.
- Kein Einfluss auf bestehende Use Cases: Adapter-only-Feature; `domain/` wächst
  nur um den State-record + Port. ArchUnit-Regeln gelten unverändert
  (HAP-Java-Imports nur in `adapter/in/homekit/`).
- Setup-Code ist ein Geheimnis (wer ihn hat + LAN-Zugang, kann koppeln) –
  behandeln wie Tuya-local-keys.

## 7. Architektur-Einordnung (Hexagonal)

```
adapter/in/homekit/           HomekitBridge (Lifecycle: Start/Stop, Server, Bridge),
                              HomekitStateStore (HAP-Java-Auth-Callback -> Port),
                              RefreshLoop (@Scheduled, Änderungs-Push)
adapter/in/homekit/accessory/ SwitchAccessory, CoverAccessory, ClimateAccessory,
                              SensorAccessories, SmokeAccessory
                              (je: HAP-Interface -> Driving Port + Mapping)
domain/model/homekit/         HomekitState (record)
domain/port/out/homekit/      HomekitStateRepository
adapter/out/persistence/      HomekitStateEntity + Panache-Repository
```

Der Adapter konsumiert ausschliesslich `domain/port/in/*` der bestehenden Slices
(`ControlSwitches`, `ControlCovers`, `ControlClimate`, `ReadSensors`,
`ReadSafety`) – kein Adapter-zu-Adapter-Zugriff, keine Domänenänderung an den
Geräte-Slices. Frontend: **keine Änderung** (HomeKit ist ein paralleler
Driving-Kanal zum REST/SPA-Weg).

## 8. Offene Punkte / TODO

- [x] **Spike durchgefuehrt (2026-08-06) – BLOCKIERT, Entscheid noetig.**
      - *Netty: kein Problem.* HAP pinnt 4.1.72, die Quarkus-BOM hebt es auf 4.1.136
        derselben Linie. Das im Plan vermutete Hauptrisiko existiert nicht.
      - *Blocker: `javax.json`.* HAP-Java (2.0.4 wie 2.0.7, identische Abhaengigkeiten,
        letztes Release 2023) ist gegen JSON-P 1.0 im `javax`-Namensraum kompiliert.
        Quarkus 3 entfernt diese Artefakte im Zuge der Jakarta-Migration aus dem
        Laufzeit-Artefakt – nachgewiesen: weder transitiv noch explizit deklariert
        landen `javax.json:javax.json-api` / `org.glassfish:javax.json` in
        `target/quarkus-app/lib/main`. Die Bridge stirbt beim Start mit
        `NoClassDefFoundError: javax/json/JsonValue` in
        `HomekitRoot.start()` → `AccessoryInformationService`.
      - *Umfang des Problems:* nur **10 von 418** HAP-Klassen berueheren `javax.json`
        (Controller + Characteristic-Basisklassen). Ein Relocation-Patch
        (`javax.json` → `jakarta.json`) ist damit technisch ueberschaubar.
      - *Nicht verifiziert (weil vorher blockiert):* mDNS-Bindung im hostNetwork-Pod,
        ufw-Regeln, Kopplung mit dem iPhone.
      - **Offener Entscheid:** Bibliothek patchen (Architektur aus §1 bleibt) vs.
        separates Deployable vs. Homebridge. Siehe PLAN.md §Etappe 1.
- [ ] Storen: Verhalten von «Halten» (HoldPosition) mit den Tuya-Covern prüfen.
- [ ] Klima: exaktes Modus-Mapping an der realen Midea verifizieren (Auto/Fan).
- [ ] Aussensensor ohne Feuchte: nur TemperatureSensor exponieren.
- [ ] ufw-Regel für 9123/5353 in `scripts/`-Provisioning des Infra-Repos ergänzen.
- [ ] Entscheiden, ob der Rauchmelder in HomeKit als «kritischer Alarm» auch
      Apple-seitige Notfall-Benachrichtigungen nutzen soll (Home-App-Einstellung).
