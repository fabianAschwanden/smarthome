# Umsetzungsplan – Use Case 16: HomeKit-Bridge

Gehört zu [`SPEC.md`](SPEC.md). Sechs Etappen, jede einzeln merge-bar, jede endet
grün (`./mvnw verify` + ArchUnit + Coverage ≥ 0.70).

**Anders als bei UC 15 steht hier der Spike am Anfang, nicht die Domäne:** Das
fachliche Mapping ist trivial, das Risiko liegt in der Bibliothek (HAP-Java ×
Java 25 × Quarkus-Netty × mDNS im hostNetwork-Pod). Das wird zuerst entschärft –
scheitert der Spike, ist der Fallback (separates Deployable oder Homebridge)
billig, weil noch nichts anderes gebaut wurde.

---

## Etappe 1 – Spike: Bridge mit einem Dummy-Schalter *(de-risk)*

1. Dependency `io.github.hap-java:hap:2.0.4` in die `pom.xml`; auf
   Netty-Konflikte prüfen (`./mvnw dependency:tree -Dincludes=io.netty` –
   HAP-Java-Netty ggf. via `dependencyManagement` auf die Quarkus-BOM-Version
   alignen).
2. Minimaler `HomekitBridge`-Startup-Bean (`homekit.enabled=true` im `%dev`):
   `HomekitServer` auf Port 9123, eine Bridge, **ein hartkodierter
   Dummy-Switch** (loggt nur EIN/AUS). Auth-State v1 im RAM.
3. Manuell verifizieren (Definition of Done dieser Etappe):
   - Mac im gleichen WLAN: `dns-sd -B _hap._tcp` zeigt die Bridge.
   - iPhone-Home-App: Bridge koppeln (Setup-Code), Dummy-Switch schalten,
     Log-Ausgabe erscheint.
   - Dasselbe einmal auf dem Server (`%lan`, hostNetwork-Pod) – mDNS-Bindung
     und ufw (9123/tcp, 5353/udp) klären.
4. Erkenntnisse (Netty-Alignment, mDNS-Interface, ufw) in SPEC §8 abhaken bzw.
   nachführen.

**Fertig wenn:** iPhone koppelt und schaltet den Dummy – auf Mac **und** Server.

## Etappe 2 – Pairing-Persistenz

1. `domain/model/homekit/HomekitState` (record, Invarianten im Compact-
   Constructor) + `domain/port/out/homekit/HomekitStateRepository`.
2. Liquibase-Migration `homekit_state` (JSONB-Snapshot, eine Zeile, Upsert –
   Muster `plant_profile`), `HomekitStateEntity` + Panache-Repository.
3. `HomekitStateStore` im Adapter: implementiert den HAP-Java-Auth-Callback
   gegen den Port (laden beim Start, speichern bei Pairing-Änderung).
4. Tests: Repository-Roundtrip (`@QuarkusTest` gegen Dev-Services), Store mit
   Fake-Port. Manuell: App neu starten → Home-App verbindet ohne Neu-Koppeln.

**Fertig wenn:** Deploy/Neustart überlebt das Pairing.

## Etappe 3 – Schalter, Sensoren, Rauchmelder

Die drei einfachsten Profile, gleiches Bau-Muster für alles Weitere:

1. `SwitchAccessory` (je Tuya-Schalter): `ControlSwitches` lesen/schalten;
   Namen aus der bestehenden Geräte-Config.
2. `SensorAccessories`: TemperatureSensor + HumiditySensor (innen),
   TemperatureSensor (aussen) über `ReadSensors`.
3. `SmokeAccessory`: `ReadSafety` → SmokeDetected + StatusLowBattery.
4. `RefreshLoop` (`@Scheduled(every = "{homekit.refresh-interval}")`): liest über
   die Ports, feuert HAP-Callbacks nur bei Änderung (Muster Battery-Sync).
5. Tests (`@QuarkusTest`, Fake-Ports): Mapping-Logik je Accessory (Zustände,
   `pending` → «nicht erreichbar»), Änderungs-Erkennung des RefreshLoop.

**Fertig wenn:** Siri schaltet die Stehlampe; Rauchmelder-Test löst die
Home-App-Meldung aus (Mock: Testalarm im Dev-Modus).

## Etappe 4 – Storen (WindowCovering)

1. `CoverAccessory` über `ControlCovers`: Zielposition, Ist-Position,
   Fahr-Status; **Invertierung** (HomeKit 100 % = offen, Domäne 100 % = zu) an
   genau einer Stelle, mit expliziten Grenzfall-Tests (0/100, Zwischenwerte).
2. HoldPosition → Stopp-Befehl des Ports; Verhalten an den realen Tuya-Covern
   verifizieren (SPEC §8).

**Fertig wenn:** «Hey Siri, Storen auf 50 %» stimmt mit der Dashboard-Anzeige
überein (und umgekehrt).

## Etappe 5 – Klimaanlage (HeaterCooler)

1. `ClimateAccessory` über `ControlClimate`: Active, Current/Target-State
   (Auto/Heat/Cool), Ist-/Soll-Temperatur (0.5-°C-Schritte).
2. Modus-Matrix Midea ↔ HomeKit als kleine pure Mapping-Klasse + Tests
   (inkl. nicht abbildbarer Modi → sinnvoller Fallback, dokumentiert).

**Fertig wenn:** Klima aus der Home-App bedienbar, Zustände konsistent mit dem
Dashboard.

## Etappe 6 – Feinschliff & Betrieb

1. Konfig-Feinschliff: `%lan.homekit.enabled=true`, Setup-Code in die
   gitignored Config (+ `config/application.properties.example` ergänzen).
2. Infra-Repo: ufw-Regeln (9123/tcp, 5353/udp aus dem LAN) ins Provisioning;
   README-Zeile in `apps/smarthome/`.
3. Doku: README-Use-Case-Tabelle (UC 16), kurzer «Koppeln»-Abschnitt
   (Home-App → Gerät hinzufügen → Code), Troubleshooting (mDNS, «keine Antwort»).
4. Aufräumen: Dummy-Switch aus Etappe 1 entfernen.

---

## Reihenfolge & Aufwand (grob)

| Etappe | Inhalt | Schätzung |
|---|---|---|
| 1 | Spike Bridge + Dummy | 1–2 Abende (Risiko-Puffer) |
| 2 | Pairing-Persistenz | 1 Abend |
| 3 | Schalter/Sensoren/Rauch | 1–2 Abende |
| 4 | Storen | 1 Abend |
| 5 | Klima | 1 Abend |
| 6 | Feinschliff | 0.5 Abend |

Nach Etappe 3 ist die Bridge bereits alltagstauglich (Schalter + Sensoren +
Rauchmelder) – Storen und Klima kommen inkrementell dazu.

## Verifikation an der realen Anlage

1. Nach Etappe 3: Bridge im `%lan`-Betrieb koppeln, Apple TV/HomePod übernimmt
   automatisch die Hub-Rolle → Fernzugriff über Apple prüfen (Home-App ausserhalb
   des WLAN – läuft dann über Apples Ende-zu-Ende-verschlüsselten Hub-Relay,
   nicht über offene Ports).
2. Eine echte Automation einrichten (z. B. «bei Sonnenuntergang Stehlampe ein»)
   und eine Szene («Filmabend»: Homecinema ein, Storen zu) – der eigentliche
   Mehrwert gegenüber dem Dashboard.
3. Rauchmelder-Probealarm: Home-App-Kritisch-Meldung auf allen Apple-Geräten.

## Folgestufen (bewusst nicht in v1)

- **F1 Wellness:** Whirlpool/Becken als HeaterCooler (Soll-Temp) + Switches
  (Pumpe/Licht/Massage) mappen.
- **F2 Kameras:** HomeKit-Video (Stream-Bridge go2rtc ↔ HAP) – eigenes Projekt,
  Aufwand hoch.
- **F3 Matter:** falls die Geräte auch für Nicht-Apple-Ökosysteme sichtbar sein
  sollen; Java-seitig derzeit ohne reife Bibliothek → beobachten.
