# Umsetzungsplan – Use Case 16: HomeKit-Bridge über Homebridge

Gehört zu [`SPEC.md`](SPEC.md). Sechs Etappen, jede einzeln merge-bar.

**Dieser Plan ersetzt den ursprünglichen Plan „Driving Adapter in der App".** Der
Spike vom 2026-08-06 (PLAN v1, Etappe 1) hat gezeigt, dass HAP-Java unter Quarkus 3
nicht lauffähig ist: Die Bibliothek ist gegen `javax.json` kompiliert, Quarkus entfernt
diesen Namensraum im Zuge der Jakarta-Migration aus dem Laufzeit-Artefakt. Der Befund
steht in SPEC §8; der Spike-Stand liegt auf `spike/homekit-hap` und wird nicht gemergt.

Gewählt wurde **Option 4: Homebridge** – eine externe, aktiv gepflegte Bridge, die über
die bestehende REST-API mit der App spricht.

## Was diese Wahl bedeutet

**Der grosse Gewinn:** Die App bleibt unberührt. Kein neuer Adapter, kein Domänenmodell,
keine Liquibase-Migration, keine Fremdbibliothek im Klassenpfad – insbesondere kein
BouncyCastle 1.51 von 2014. Pairing, mDNS und die Anpassung an neue iOS-Versionen pflegt
die Homebridge-Community statt wir.

**Der Preis:** Das Prinzip „ein Deployable" fällt. Es kommt ein zweites Image, ein
zweites Deployment mit `hostNetwork`, ein persistentes Volume und ein zweites
Konfigurationssystem dazu. Und die Mapping-Logik (Storen-Invertierung, Klima-Modi) lebt
künftig in TypeScript statt in getestetem Java – sie braucht dort eigene Tests.

**Was die API angeht, ist nichts zu tun.** Alle fünf Geräteklassen sind bereits
vollständig lesbar und schaltbar (am 2026-08-06 gegen die laufende Instanz geprüft):

| Gerät | Lesen | Schalten |
|---|---|---|
| Schalter | `GET /api/switches` | `POST /api/switches/{id}` |
| Storen | `GET /api/covers` | `POST /api/covers/{id}/position`, `/{id}/command` |
| Klima | `GET /api/climate` | `POST /api/climate/{id}/{power,mode,target,boost}` |
| Sensoren | `GET /api/sensors` | – |
| Rauchmelder | `GET /api/safety/smoke` | – |

Zusätzliche Last für die Geräte entsteht nicht: Das Plugin pollt die **App**, nicht die
Geräte. Die App pollt ohnehin schon.

---

## Etappe 1 – De-Risk: Homebridge im Cluster, ein Schalter *(Spike)*

Wieder steht das Risiko am Anfang – diesmal ist es nicht die Bibliothek, sondern der
Betrieb: mDNS aus einem Container, Pairing-Persistenz, Firewall.

1. Homebridge-Deployment im k3s (`apps/smarthome/` oder eigener Ordner):
   offizielles Image, **`hostNetwork: true`** (mDNS/Bonjour braucht L2), PVC für
   `/homebridge` (Pairing + Config müssen Neustarts überleben).
2. Ohne eigenes Plugin starten: ein generisches HTTP-Plugin gegen **einen** Schalter
   (`GET /api/switches` lesen, `POST /api/switches/stehlampe` schalten).
3. ufw: mDNS `5353/udp` und der HAP-Port aus dem LAN.
4. Verifizieren (Definition of Done):
   - `dns-sd -B _hap._tcp` auf dem Mac zeigt die Bridge.
   - iPhone koppelt, Stehlampe schaltet, Dashboard zeigt dieselbe Änderung.
   - Pod neu starten → Home-App verbindet **ohne** Neu-Koppeln.

**Fertig wenn:** Siri schaltet die Stehlampe, und ein Pod-Neustart kostet kein Pairing.

> Scheitert schon das: Fallback ist Option 2 (Fork von HAP-Java mit Jakarta-Umstellung).
> Auch dann ist wenig verloren – bis hierher entsteht nur Infrastruktur, kein Code.

## Etappe 2 – Eigenes Plugin: Gerüst und Discovery

1. `homebridge-smarthome/` im Repo (TypeScript, eigenes `package.json`): Platform-Plugin
   nach dem `DynamicPlatformPlugin`-Muster.
2. Konfiguration: Basis-URL der App, Poll-Intervall, optional Geräte-Filter.
3. Ein Poll-Zyklus liest alle fünf Endpunkte und legt Accessories an bzw. entfernt sie.
   Geräte werden über ihre stabile `id` wiedererkannt (nicht über den Namen – der ist
   in der App änderbar).
4. `online: false` → Accessory als **nicht erreichbar** melden (HomeKit-Fehler statt
   stiller Falschwert; sonst zeigt die Home-App einen alten Zustand als aktuell).
5. Vitest o. ä. für die reine Mapping-Logik.

**Fertig wenn:** Die Bridge zeigt alle Geräte mit korrekten Namen und Räumen an.

## Etappe 3 – Schalter, Sensoren, Rauchmelder

Die drei einfachen Profile:

1. **Switch** je Tuya-Schalter (`state: "ON"|"OFF"`).
2. **TemperatureSensor + HumiditySensor** innen, **TemperatureSensor** aussen
   (der Aussensensor liefert Feuchte, aber ohne verlässliche Kalibrierung – SPEC §8).
3. **SmokeSensor** + **StatusLowBattery** (`alarm: "OK"`, `battery: -1` = unbekannt →
   Batteriestatus dann **nicht** melden statt „leer" zu behaupten).

**Fertig wenn:** Siri schaltet die Stehlampe; die Temperaturen stimmen mit dem Dashboard
überein; ein Testalarm erscheint in der Home-App.

## Etappe 4 – Storen (WindowCovering)

1. ~~**Invertierung an genau einer Stelle:** Die Domäne rechnet 100 % = zu, HomeKit
   100 % = offen → `homekit = 100 − domain`.~~ **Falsche Annahme, korrigiert am
   2026-08-06:** Die REST-API rechnet in der Geräteskala **0 = zu, 100 = offen** – wie
   HomeKit. Es wird **nicht** invertiert (belegt im `Cover`-Record, im Port
   `ControlCovers` und in `docs/cover/SPEC.md` §2). Der Plan hatte die Anzeige des
   Dashboards («% zu», dort gespiegelt) für die Schnittstelle gehalten. Tests für 0, 100
   und Zwischenwerte halten die Richtung fest.
2. Zielposition über `POST /{id}/position`, Stopp über `/{id}/command`.
3. `PositionState` (öffnet/schliesst/steht) aus dem Vergleich Ist↔Ziel ableiten.

**Fertig wenn:** «Storen auf 50 %» und die Dashboard-Anzeige stimmen überein – in beide
Richtungen.

## Etappe 5 – Klimaanlage (HeaterCooler)

1. `Active`, `CurrentHeaterCoolerState`, `TargetHeaterCoolerState`, Ist-/Soll-Temperatur.
2. Modus-Matrix Midea ↔ HomeKit als **reine Funktion mit Tests**, inklusive der Modi,
   die HomeKit nicht kennt (Fan/Dry) → dokumentierter Fallback statt stillem Raten.
3. Boost bleibt vorerst Dashboard-exklusiv (kein HomeKit-Gegenstück).

**Fertig wenn:** Klima aus der Home-App bedienbar, Zustände konsistent mit dem Dashboard.

## Etappe 6 – Betrieb

1. ✅ **Image in der CI** – [`deploy/Dockerfile.homebridge`](../../deploy/Dockerfile.homebridge)
   baut das Plugin, lässt Lint und Tests laufen und legt das fertige Paket als Tarball
   unter `/opt/smarthome/` ins offizielle Homebridge-Image. Der Release-Workflow baut es
   als drittes Image (`ghcr.io/…/smarthome-homebridge`) multi-arch mit.

   > **Warum ein Tarball und nicht direkt `node_modules`:** Homebridge startet mit
   > `-P /var/lib/homebridge/node_modules --strict-plugin-resolution`, und dieses
   > Verzeichnis liegt auf dem PVC. Alles, was das Image dorthin legt, wäre vom Mount
   > verdeckt. Die `package.json` der Brücke verweist deshalb per `file:` auf den
   > Tarball – derselbe Weg, den auch Plugins aus der Registry gehen.

2. ✅ **ufw** – `51826/tcp` (HAP) und `5353/udp` (mDNS) aus dem LAN, im Provisioning des
   Infra-Repos.
3. ✅ **Backup** – der Kopplungszustand wird **mitgesichert**: `backup.sh` packt
   `persist/`, `accessories/` und `config.json` aus dem PVC. Ein Volume-Verlust hätte
   sonst nicht nur ein Neu-Koppeln bedeutet, sondern auch jedes Gerät wieder von Hand in
   seinen Raum – samt Szenen und Automationen. Die paar Kilobyte sind das billiger.
4. ✅ **Doku** – Use-Case-Tabelle (16), «Koppeln»-Abschnitt und Troubleshooting im
   README, Plugin-README unter `homebridge-smarthome/`.
5. ✅ **Web-UI bleibt aus.** Die Konfiguration kommt deklarativ aus dem Secret; eine
   zweite Admin-Oberfläche im LAN wäre ein Zugang mehr, den niemand braucht und den man
   absichern müsste. Wer etwas ändern will, ändert die `config.json` und deployt.

### Der Wechsel auf das eigene Plugin

Bis hierher ist alles vorbereitet, aber **noch nicht scharf**: In der Brücke läuft
weiterhin das generische HTTP-Plugin aus Etappe 1 mit der einen Stehlampe. Das Manifest
allein schaltet nicht um – es zieht nur das neue Image, das den Tarball ungenutzt
mitbringt. Scharf wird es erst mit diesen beiden Änderungen an der (gitignorten)
Brücken-Konfiguration im Infra-Repo:

1. `homebridge-package.json`: `homebridge-http-switch` raus,
   `"homebridge-smarthome": "file:/opt/smarthome/homebridge-smarthome.tgz"` rein.
2. `homebridge-config.json`: den `accessories`-Eintrag entfernen und stattdessen
   ```json
   "platforms": [{ "platform": "Smarthome", "baseUrl": "http://127.0.0.1:8080" }]
   ```
   Der **`bridge`-Block bleibt unverändert** – Name, `username` und `pin` sind die
   Identität der Brücke.

Danach `pre-deploy.sh` (erzeugt das Secret neu) und `kubectl apply -k`.

**Erwartung, die zu prüfen ist:** Die Kopplung überlebt, weil sie an `username`/`pin`
hängt und in `persist/` liegt – dort ändert sich nichts. Die *Stehlampe* dagegen war
bisher ein Accessory des HTTP-Plugins und kommt neu über die Plattform; sie erscheint
in der Home-App als **neues Gerät** und muss ihrem Raum wieder zugewiesen werden. Ein
Release-Tag muss vorher gebaut sein, sonst zeigt `:latest` ins Leere.

---

## Reihenfolge & Aufwand (grob)

| Etappe | Inhalt | Schätzung |
|---|---|---|
| 1 | Homebridge im Cluster + ein Schalter | 1 Abend |
| 2 | Plugin-Gerüst + Discovery | 1–2 Abende |
| 3 | Schalter/Sensoren/Rauch | 1 Abend |
| 4 | Storen | 0.5 Abend |
| 5 | Klima | 1 Abend |
| 6 | Betrieb & Doku | 1 Abend |

Nach Etappe 3 ist die Bridge alltagstauglich; Storen und Klima kommen inkrementell dazu.

## Verifikation an der realen Anlage

1. Apple TV/HomePod übernimmt die Hub-Rolle → Fernzugriff über Apple prüfen (läuft über
   Apples verschlüsselten Hub-Relay, **kein** offener Router-Port).
2. Eine echte Automation («bei Sonnenuntergang Stehlampe ein») und eine Szene
   («Filmabend»: Homecinema ein, Storen zu) – der eigentliche Mehrwert gegenüber dem
   Dashboard.
3. Rauchmelder-Probealarm auf allen Apple-Geräten.

## Folgestufen (bewusst nicht in v1)

- **F1 Push statt Poll:** SSE- oder WebSocket-Endpunkt in der App, damit Zustandswechsel
  ohne Poll-Verzögerung in der Home-App landen.
- **F2 Wellness:** Whirlpool/Becken als HeaterCooler + Switches.
- **F3 Kameras:** HomeKit Secure Video über go2rtc – eigenes Projekt.
- **F4 PV-Prognose sichtbar machen:** HomeKit kennt keine Energie-Profile; denkbar wäre
  ein virtueller Schalter «Überschuss erwartet» als Automations-Auslöser.
