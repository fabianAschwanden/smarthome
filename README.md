# smarthome

Smart-Home-Anwendung. **Use Case 1: Energieverbrauch visualisieren** – liest Fronius
(Solar API) und SMARTFOX (`values.xml`), berechnet den Hausverbrauch und stellt beide
Quellen mit ihrer Differenz gegenüber (Hintergrund/Spec: `docs/energy/SPEC.md`).

Aufgesetzt auf dem `app-template`-Blueprint (`docs/blueprint.md`): ein Deployable (BFF),
Quarkus + Angular via Quinoa, Hexagonal + DDD, erzwungen per ArchUnit.

## Entwickeln

```bash
./mvnw quarkus:dev        # Backend :8080 + Angular-Dev-Server :4200
```

- Dashboard: <http://localhost:8080>  ·  API: <http://localhost:8080/api/energy/current>
- Im `%dev`/`%test`-Profil sind **Mock-Quellen** aktiv – das Dashboard zeigt sofort
  Fronius- und SMARTFOX-Werte inkl. Differenz, ganz ohne Hardware.
- Swagger-UI: `/q/swagger-ui` · Health: `/q/health`

## Lokaler Live-Betrieb gegen echte Geräte im LAN (`%live`)

Für Tests am echten SMARTFOX/Fronius im Heimnetz – mit Live-Reload, ohne Login,
DB über Dev Services (Docker):

```bash
./mvnw quarkus:dev -Dquarkus.profile=dev,live
```

- `%live` schaltet die Adapter von Mock auf **echt** (`smarthome.real-devices=true`)
  und setzt die Geräte-IPs (SMARTFOX `<smartfox-ip>`, Fronius `<fronius-ip>`).
- Die Batterie-Seite schaltet dann das **echte** SMARTFOX-Relais 1
  (`setswrel.cgi?rel=1&state=1` = EIN). Erst „Manuell" wählen, dann EIN/AUS.
- Fällt ein Gerät aus, bleibt das andere sichtbar (Status `ERROR`).
- **TODO Fronius:** aktuell nicht im selben WLAN-Segment erreichbar (Router-Routing
  noch einzurichten) → zeigt bis dahin `ERROR`; SMARTFOX läuft bereits voll.

## Produktivbetrieb (Fly.io)

```bash
./mvnw verify
java -Dquarkus.profile=prod \
     -Denergy.fronius.base-url=http://<fronius-ip> \
     -Denergy.smartfox.base-url=http://<smartfox-ip> \
     -jar target/quarkus-app/quarkus-run.jar
```

`%prod` aktiviert ebenfalls die echten Adapter (`smarthome.real-devices=true`),
zusätzlich OIDC-Login, Security-Header und eine explizite DB-URL.
Feld-/Vorzeichen-Zuordnung gegen die Anlage prüfen – siehe `docs/energy/SPEC.md` §11.

## Struktur (Schicht zuerst, Fachbereich darunter)

```
src/main/java/fabianaschwanden/smarthome/
├── domain/model/{energy,battery}        # Records (framework-frei)
├── domain/port/{in,out}/{energy,battery}  # Use-Case- und Driven-Ports
├── domain/service/{energy,battery}      # Domain-Services (pur)
├── application/service/{energy,battery} # Use-Case-Orchestrierung
└── adapter/
    ├── in/rest/{energy,battery}         # JAX-RS Resources (+ dto/{energy,battery})
    └── out/{energy,battery}/...         # Geräte-Adapter (fronius/smartfox/mock)
webapp/src/app/features/{energy,battery}/   # Angular-Seiten (Signals, OnPush)
```

Use Cases: **1 Energie** (Fronius vs. SMARTFOX), **2 Batteriesteuerung**
(SMARTFOX-Relais 1, manuell + PV-Überschuss-Automatik; `docs/battery/SPEC.md`),
**3 Tuya-Schalter** (Smart-Life-Geräte lokal EIN/AUS, Stehlampe/Palmenbeleuchtung;
`docs/tuya/SPEC.md`), **4 Zeitsteuerung** (Schedule/Countdown/Random/Inching je
Schalter, persistiert), **5 Storensteuerung** (Tuya-Cover Auf/Ab/Stopp + Position;
`docs/cover/SPEC.md`), **6 Wellness-Anlagen** (Whirlpool & Schwimmbecken, mehrere
Funktionen Pumpe/Heizung/Licht/Massage; `docs/appliance/SPEC.md`), **7 Klimaanlage**
(ein/aus, Modus Kühlen/Heizen/Auto/Lüften, Soll-/Ist-Temperatur; `docs/climate/SPEC.md`).
Use Cases 6–7 mit noch offener Geräteschnittstelle laufen real als „pending" (offline),
bis sie angebunden ist. Das Tuya-LAN-Protokoll liegt geteilt in `support.tuya`.
Der `note`-Durchstich bleibt als generische Referenz (schicht-zuerst, ohne Fachordner).

Der Beispiel-Durchstich `Note` (mit Persistenz/Liquibase) bleibt vorerst als Referenz
für künftige Use Cases mit Datenbank (z. B. Energie-Historie) und ist unter `/notes` erreichbar.

## Qualität

```bash
./mvnw verify             # Backend-Tests, ArchUnit, Coverage-Gate, Frontend-Build
```
