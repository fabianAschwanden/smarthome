# smarthome

Lokales Smart-Home-Dashboard fürs eigene Heimnetz: Energie, Batterie, Schalter,
Storen, Wellness-Anlagen, Klimaanlage, Umweltsensor, Rauchmelder, Zeitsteuerung,
Item-Bilder und Wettervorhersage – auf einem Blick, bedienbar per Browser/iPad.

Ein Deployable (BFF): **Quarkus** (Java 25) + **Angular** (via Quinoa) in einem
Artefakt, **Hexagonal + DDD**, erzwungen durch ArchUnit. Geräte werden **rein lokal
im LAN** angesprochen (keine Hersteller-Cloud nötig). Verbindliche Konventionen:
[`docs/blueprint.md`](docs/blueprint.md).

## Schnellstart (ohne Hardware)

```bash
./mvnw quarkus:dev        # Backend :8080 + Angular-Dev-Server :4200
```

- Dashboard: <http://localhost:8080> · API-Beispiel: `/api/energy/current`
- Swagger-UI: `/q/swagger-ui` · Health: `/q/health`
- Im `%dev`/`%test`-Profil sind **Mock-Quellen** aktiv – alles läuft sofort ohne
  Geräte. PostgreSQL kommt über Dev Services (Container-Runtime nötig).

## Use Cases

| # | Bereich | Funktion | Spec |
|---|---------|----------|------|
| 1 | **Energie** | Fronius (Solar-API) & SMARTFOX gegenübergestellt, Hausverbrauch, Tages-/Relativwerte | [energy](docs/energy/SPEC.md) |
| 2 | **Batterie** | SMARTFOX-Relais 1: manuell + PV-Überschuss-Automatik | [battery](docs/battery/SPEC.md) |
| 3 | **Schalter** | Tuya/Smart-Life lokal EIN/AUS (Stehlampe, Palme, Carport, Föhn, Homecinema) | [tuya](docs/tuya/SPEC.md) |
| 4 | **Zeitsteuerung** | Schedule/Countdown/Random/Inching je Schalter (persistiert) | [schedule](docs/schedule/SPEC.md) |
| 5 | **Storen** | Tuya-Cover Auf/Ab/Stopp + Position (UI 100 % = zu, Lamellen-Visualisierung) | [cover](docs/cover/SPEC.md) |
| 6 | **Wellness** | Whirlpool & Schwimmbecken: Pumpe/Heizung/Licht/Massage + Soll-Temperatur | [appliance](docs/appliance/SPEC.md) |
| 7 | **Klimaanlage** | Midea/NetHome Plus lokal: ein/aus, Modus, Soll-/Ist-Temperatur | [climate](docs/climate/SPEC.md) |
| 8 | **Umweltsensor** | Tuya-Temp/Feuchte (nur lesend) | [sensor](docs/sensor/SPEC.md) |
| 9 | **Sicherheit** | Tuya-Rauchmelder: Alarm + Batterie, Nachrichtenzentrale | [safety](docs/safety/SPEC.md) |
| 10 | **Wetter** | Vorhersage (Open-Meteo, kein API-Key) | [weather](docs/weather/SPEC.md) |
| – | **Item-Bilder** | Foto je Gerät hinterlegen (serverseitig, geteilt) | [itemimage](docs/itemimage/SPEC.md) |

**Nachrichtenzentrale:** Geräte-Meldungen (Rauchalarm, offline, niedriger Akku) sind
über die Glocke oben rechts einsehbar; ein aktiver Alarm lässt sie rot pulsieren.

**Stand der Geräteanbindung:** Energie, Batterie, Schalter, Storen, Klima, Sensor und
Rauchmelder sind real angebunden. Die **Wellness-Anlagen** (UC 6) laufen real noch als
`pending` (offline), bis die Steuerschnittstelle feststeht – Mock/Frontend sind fertig.

## Profile

| Profil | Zweck | Geräte | Login |
|--------|-------|--------|-------|
| `%dev` / `%test` | Entwicklung/Tests | Mock | aus |
| `%dev,live` | lokaler Live-Test gegen echte Geräte (Live-Reload, Dev-Services-DB) | echt | aus |
| `%lan` | Heimbetrieb auf Mini-PC/NAS (docker-compose) | echt | aus |
| `%prod` / `%fly` | Cloud (Fly.io) | echt | OIDC |

```bash
./mvnw quarkus:dev -Dquarkus.profile=dev,live   # echte Geräte im LAN testen
```

Mock vs. echt steuert die Build-Property `smarthome.real-devices`. Geräte-Daten
(device-ids, local-keys/Token, IPs, Standort) stehen **ausschliesslich** in der
gitignored `config/application.properties` – Vorlage:
[`config/application.properties.example`](config/application.properties.example).
Im eingecheckten Source stehen nur neutrale Platzhalter.

## Heim-Server (Mini-PC) + iPad als Anzeige

Die App läuft dauerhaft auf einem kleinen Always-on-Linux-Host im LAN; das iPad ist
nur Client (`http://<server-ip>:8080`). Setup per Docker Compose:

```bash
cp config/application.properties.example config/application.properties   # ausfüllen
cd deploy && cp .env.example .env && docker compose up -d --build
```

Details, Voraussetzungen und Provisioning: [`docs/server/SETUP.md`](docs/server/SETUP.md).
Hinweis: NETGEAR ReadyNAS hat kein brauchbares Docker → kleiner Mini-PC/Raspberry Pi
(4 GB+ empfohlen) als Host.

## Architektur

Schicht zuerst, Fachbereich darunter (ein Slice je Use Case):

```
src/main/java/fabianaschwanden/smarthome/
├── domain/model/<slice>          # Records, framework-frei (Invarianten im Compact-Constructor)
├── domain/port/{in,out}/<slice>  # Use-Case- und Driven-Ports
├── domain/service/<slice>        # reine Domain-Services
├── application/service/<slice>   # Use-Case-Orchestrierung
├── adapter/in/rest/<slice>       # JAX-RS Resources (+ dto/<slice>)
├── adapter/out/<slice>/{mock,local,...}  # Geräte-Adapter
└── support/tuya                  # geteiltes Tuya-LAN-Protokoll + Discovery (kein Adapter)
webapp/src/app/features/<slice>/  # Angular-Seiten (Signals, OnPush, native control flow)
```

- Abhängigkeitsrichtung `adapter → application → domain` (ArchUnit bricht den Build).
- Liquibase besitzt das Schema (append-only), Hibernate validiert nur.
- **Sidecar:** Tuya 3.4/3.5 und Midea werden über einen kleinen Python-Dienst
  (`tools/tuya-sidecar`) gelesen, den der Java-Adapter per HTTP aufruft.

## Qualität

```bash
./mvnw verify             # Backend-Tests, ArchUnit, Coverage-Gate, Frontend-Build
cd webapp && npm test     # Frontend-Tests
```

Tests laufen strikt gegen Mocks – ein Hardening-Test stellt sicher, dass im
`%test`-Profil **nie** echte Geräte angesprochen werden.
