# Spec – Use Case 16: HomeKit-Bridge (Siri, Home-App, Apple-Automationen)

Status: v0.1 (Entwurf) · Datum: 2026-08-05 · Plattform: Java 25 / Quarkus (Hexagonal + DDD)

## 1. Zweck & Scope

Die vorhandenen Geräte werden Apple-nativ bedienbar: **Siri** («Storen runter»),
**Home-App** auf iPhone/iPad/Watch/Mac und **Apple-Automationen** mit Apple TV /
HomePod als Home-Hub (Zeitpläne, Geofencing, Szenen) – ohne die eigene Architektur
oder das Dashboard aufzugeben. Die App publiziert dafür das **HomeKit Accessory
Protocol (HAP)** direkt im LAN; keine Apple-Cloud-Anbindung, keine Hersteller-Cloud.

Architektur-Entscheid (revidiert 2026-08-06): **Homebridge als externe Bridge**, die
über die bestehende REST-API mit der App spricht.

> Ursprünglich war ein Driving Adapter *in* der App vorgesehen – ein Deployable bleibt
> ein Deployable. Der Spike hat das widerlegt: HAP-Java ist gegen `javax.json`
> kompiliert, Quarkus 3 entfernt diesen Namensraum (§8). Von den geprüften Auswegen
> wurde Homebridge gewählt: aktiv gepflegt, kein Fork, keine Fremdbibliothek im
> Klassenpfad der App. Preis ist ein zweites Deployable und Mapping-Logik in
> TypeScript statt in getestetem Java.

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

## 2. Technik: Homebridge + eigenes Plugin

- **Homebridge** (Node.js) als Bridge-Prozess; ein eigenes Platform-Plugin
  (`homebridge-smarthome`) übersetzt zwischen HomeKit-Profilen und unserer REST-API.
- Homebridge übernimmt HAP, Pairing, mDNS-Advertisement und die Anpassung an neue
  iOS-Versionen. Unsere Seite ist reines Mapping.
- Betrieb als eigenes Deployment im k3s mit `hostNetwork` (mDNS braucht L2) und einem
  PVC für den Pairing-Zustand.
- Das Plugin **pollt die App, nicht die Geräte** – es entsteht keine zusätzliche Last
  auf Tuya/Midea; die App pollt ohnehin.
- **Voraussetzung Netz:** iPhone/Apple TV und der Server müssen im selben
  L2-Netz sein (mDNS) – gegeben: alles hängt im AmpliFi-Subnetz `192.168.113.x`,
  die App läuft mit `hostNetwork`. ufw: Port 9123/tcp + mDNS 5353/udp aus dem LAN.
- **Pairing:** Setup-Code (Format `XXX-XX-XXX`) wird beim Koppeln in der Home-App
  eingegeben. Der Kopplungszustand (MAC, Salt, Private Key, Pairings) muss
  Neustarts überleben → Persistenz (§4), sonst verliert die Home-App die Bridge
  bei jedem Deploy.

## 3. Verhalten & Mapping-Regeln

- **Ein Weg für Zustand:** Das Plugin hält keinen eigenen Gerätezustand; jede
  Anfrage/Änderung geht über die REST-API. Damit gelten automatisch dieselben Regeln
  wie im Dashboard. `online: false` wird als **nicht erreichbar** gemeldet, nicht als
  alter Wert – sonst zeigt die Home-App einen Zustand, den es nicht mehr gibt.
- **Storen-Invertierung:** Domäne und UI rechnen 100 % = **zu**, HomeKit 100 % =
  **offen** → das Plugin invertiert (`homekit = 100 − domain`). Ein einziger
  Umrechnungsort, mit Tests.
- **Klima-Mapping:** Midea-Modi ↔ HeaterCooler-States (Auto/Heat/Cool; Fan-only
  v1 aussen vor). Soll-Temperatur-Schritte wie im Dashboard (0.5 °C).
- **Poll in v1:** Das Plugin liest die REST-Endpunkte im Intervall (Default 10 s) und
  meldet nur **Änderungen** an HomeKit. Es pollt die App, nicht die Geräte – keine
  zusätzliche Gerätelast. Ein Push-Kanal (SSE) ist Folgestufe F1.
- **Rauchmelder:** `SmokeDetected` + `StatusLowBattery`; die bestehende
  Nachrichtenzentrale/ntfy bleibt unverändert parallel bestehen (HomeKit ist ein
  zusätzlicher Kanal, kein Ersatz).

## 4. Persistenz (Pairing-Zustand)

**Gehört Homebridge, nicht uns.** Der Kopplungszustand liegt in dessen
Persistenz-Verzeichnis auf einem PVC. Keine Liquibase-Migration, kein Domänenmodell,
kein Port – die Domäne bleibt von diesem Use Case vollständig unberührt.

Konsequenz fürs Backup: Der Zustand liegt damit **ausserhalb** des bestehenden Backups
(das die Datenbank sichert). Entweder das PVC mitsichern oder bewusst akzeptieren, dass
ein Volume-Verlust ein Neu-Koppeln bedeutet (PLAN Etappe 6).

## 5. Konfiguration

**In der App: keine.** Sie weiss nichts von HomeKit.

Konfiguriert wird das Plugin in der Homebridge-Config (auf dem PVC):

```json
{ "platform": "Smarthome",
  "baseUrl": "http://<app-host>:8080",
  "pollIntervalSeconds": 10 }
```

Der Setup-Code zum Koppeln wird von Homebridge verwaltet und ist wie ein local-key zu
behandeln: Wer ihn hat und im LAN ist, kann koppeln.

Zum Entwickeln genügt es, `baseUrl` auf eine lokal im Mock-Modus laufende App zu
richten – Koppeln gegen Mock-Geräte, ohne Anlage.

## 6. Nicht-funktionale Anforderungen

- Läuft vollständig lokal; kein Cloud-Kontakt, kein neuer offener Router-Port
  (nur LAN: HAP-Port und mDNS 5353/udp).
- Bridge-Ausfall/Neustart: Home-App zeigt «keine Antwort», fängt sich nach dem Start
  selbst (mDNS-Re-Announce); Pairing bleibt dank des PVC erhalten (§4).
- **Null Einfluss auf die App:** kein Code, keine Abhängigkeit, keine Migration. Fällt
  die Bridge aus, merkt die App nichts; fällt die App aus, meldet HomeKit «nicht
  erreichbar». Die Kopplung ist so lose wie möglich.
- Der Setup-Code ist ein Geheimnis (wer ihn hat und im LAN ist, kann koppeln) –
  behandeln wie Tuya-local-keys.

## 7. Architektur-Einordnung

```
homebridge-smarthome/     eigenes Verzeichnis im Repo (TypeScript):
                          Platform (Discovery + Poll) und je Geraeteklasse ein
                          Accessory-Handler mit dem Mapping HomeKit <-> REST-DTO
```

**An der Java-Anwendung ändert sich nichts** – kein Adapter, kein Port, keine
Domänenänderung, keine neue Abhängigkeit. HomeKit ist ein weiterer Konsument der
bestehenden REST-API, wie das Dashboard.

Die Kehrseite, die man kennen muss: Die Mapping-Regeln (Storen-Invertierung,
Klima-Modi) liegen damit **ausserhalb** des Java-Testbestands. Sie brauchen im Plugin
eigene Tests – sonst gibt es Fachlogik ohne Netz.

Frontend: keine Änderung.

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
      - **Entschieden (2026-08-06): Homebridge** (§1, §2). Die Alternativen waren
        Relocation-Patch, Fork mit Jakarta-Umstellung und separates JVM-Deployable.
        Ausschlaggebend: aktiv gepflegte Bibliothek statt Fork einer seit 2023
        stillstehenden, und kein BouncyCastle 1.51 (2014) im Klassenpfad der App.
- [ ] Storen: Verhalten von «Halten» (HoldPosition) mit den Tuya-Covern prüfen.
- [ ] Klima: exaktes Modus-Mapping an der realen Midea verifizieren (Auto/Fan).
- [x] Aussensensor: liefert Feuchte, aber ohne verlässliche Kalibrierung – entscheiden,
      ob nur TemperatureSensor exponiert wird.
      - **Entschieden (2026-08-06): Feuchte wird exponiert** (Etappe 3). Home-App und
        Dashboard sollen denselben Wert nennen; zwei verschiedene Zahlen für dieselbe
        Messung wären die grössere Zumutung als eine ungenaue. Über den Dienst
        entscheidet der Sensor selbst: Meldet er `humidity = -1` (unbekannt), bekommt er
        keinen HumiditySensor.
- [ ] ufw-Regeln (HAP-Port + mDNS 5353/udp) ins Provisioning des Infra-Repos.
- [ ] Pairing-Zustand liegt auf einem PVC und damit **ausserhalb** des DB-Backups –
      mitsichern oder den Verlust bewusst akzeptieren (§4).
- [ ] Entscheiden: Homebridge-Web-UI (Port 8581) an oder aus. Bequem, aber eine
      weitere Admin-Oberfläche im LAN – wenn an, dann mit Passwort.
- [ ] Mapping-Tests im Plugin: Storen-Invertierung und Klima-Modi liegen ausserhalb
      des Java-Testbestands (§7) und brauchen dort eigene Abdeckung.
- [ ] Entscheiden, ob der Rauchmelder in HomeKit als «kritischer Alarm» auch
      Apple-seitige Notfall-Benachrichtigungen nutzen soll (Home-App-Einstellung).
