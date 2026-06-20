# Betrieb auf NAS / Mini-PC (iPad als Anzeige)

Ziel: Die App läuft dauerhaft auf einem kleinen Always-on-Gerät im LAN
(Synology/QNAP-NAS mit Docker, Mini-PC, kleiner Linux-Server). Das **iPad** ist
nur **Client** – es öffnet in Safari `http://<server-ip>:8080`. Kein grosser
Rechner muss laufen.

> Das iPad selbst kann die App **nicht hosten** (kein Java/PostgreSQL/Python im
> Hintergrund). Es braucht ein Always-on-Gerät als Server.

## Welcher Server? (NETGEAR-ReadyNAS-Hinweis)

NETGEAR **ReadyNAS** hat **kein offizielles/brauchbares Docker** und meist eine zu
schwache CPU für JVM + PostgreSQL. Empfehlung: **NAS bleibt Storage**, ein kleiner
separater Always-on-Host fährt die App. Bewährt und sparsam:

- **Raspberry Pi 4 oder 5** (64-bit Raspberry Pi OS), ~5–7 W, oder
- ein beliebiger **Mini-PC** (Intel NUC o.ä.) mit Linux.

Das untenstehende `docker-compose.yml` läuft auf beidem unverändert. Auf dem
Raspberry Pi (ARM64) gibt es alle Basis-Images (Java/Python/Postgres) nativ.

### RAM-Empfehlung (Raspberry Pi)

Im Betrieb laufen gleichzeitig: JVM (~250–400 MB) + PostgreSQL (~100–150 MB) +
Sidecar (~50 MB) + OS (~200 MB) ≈ **0,7 GB**.

| Pi-Variante | Eignung |
|-------------|---------|
| **1 GB** | Nur „App hosten, iPad als Anzeige" – **knapp**, läuft aber mit JVM-Heap-Limit + Swap. Image **extern bauen** (1 GB reicht für den Maven/Angular-Build **nicht**). Chromium-Kiosk auf demselben Pi: **zu wenig RAM**. |
| **4 GB+** (empfohlen) | Entspannt: App + DB + Sidecar, dazu optional Chromium-Kiosk am TV, und Bauen direkt auf dem Pi möglich. |

> **Tipp Raspberry Pi:** Der App-Image-Build (Maven + Angular) ist auf dem Pi
> langsam und RAM-hungrig (auf 1 GB scheitert er meist). Besser auf einem Mac/PC
> **für ARM64 vorbauen** und das Image auf den Pi übertragen – siehe Abschnitt
> „Image extern für ARM bauen".

## Was läuft im Container-Verbund (docker-compose.yml)

| Service   | Aufgabe                                           |
|-----------|---------------------------------------------------|
| `app`     | Quarkus-Backend **inkl. Angular-Frontend** (Port 8080) |
| `db`      | PostgreSQL (Daten persistent in Volume `db-data`) |
| `sidecar` | Python-Dienst für Klimaanlage (Midea) + Tuya 3.4  |

Alle Container laufen im **Host-Netz** (`network_mode: host`). Das ist nötig,
weil die Geräte-Erkennung UDP-Broadcasts nutzt (Tuya 6666/6667), die ein
Docker-Bridge-Netz nicht durchquert. Funktioniert auf einem **Linux**-Host
(NAS/Mini-PC). Docker Desktop (macOS/Windows) unterstützt host-mode nicht voll –
dort nur zum Bauen/Testen.

## Voraussetzungen auf dem Server

- Docker + Docker Compose v2
- Der Server hängt **im selben LAN** wie die Geräte (am besten LAN-Kabel)
- Geräte-IPs im Router fest reserviert (DHCP), siehe IP-Übersicht in
  `config/application.properties`

## Einrichtung

1. Repo auf den Server holen:
   ```bash
   git clone https://github.com/fabianAschwanden/smarthome.git
   cd smarthome
   ```

2. Lokale Konfiguration anlegen (Secrets/IPs – **nicht** im Repo):
   ```bash
   cp config/application.properties.example config/application.properties
   # device-ids, local-keys/Token, IPs und Wetter-Standort eintragen.
   # Wichtig: auch die %lan.*-Einträge setzen (das compose-Profil heisst 'lan').
   ```
   Tipp: Die `%lan.*`-Zeilen entsprechen den `%dev.*`-Gerätezeilen.

3. Starten:
   ```bash
   docker compose up -d --build      # erstes Mal: baut die Images
   docker compose logs -f app        # bis "Listening on: http://0.0.0.0:8080"
   ```

4. Am iPad in Safari öffnen: `http://<server-ip>:8080`
   - Tipp: „Zum Home-Bildschirm hinzufügen" → die App startet wie eine native App.
   - Optional: Geführter Zugriff / Safari als einzige App, damit das iPad ein
     reines Wandpanel wird.

## Betrieb

```bash
docker compose ps             # Status
docker compose logs -f app    # Backend-Logs
docker compose restart app    # nur App neu starten
docker compose down           # alles stoppen (DB-Daten bleiben im Volume)
docker compose up -d --build  # nach git pull aktualisieren
```

## Image extern für ARM bauen (Raspberry Pi)

Wenn der Build auf dem Pi zu langsam ist: das App-Image auf dem Mac/PC für ARM64
bauen und auf den Pi übertragen.

```bash
# Auf dem Mac/PC (Docker mit buildx):
docker buildx build --platform linux/arm64 \
  -f src/main/docker/Dockerfile.jvm -t smarthome-app:arm64 --load .
docker buildx build --platform linux/arm64 \
  -t smarthome-sidecar:arm64 ./tools/tuya-sidecar

# Auf den Pi übertragen (oder via privater Registry):
docker save smarthome-app:arm64 smarthome-sidecar:arm64 | ssh pi@<pi-ip> docker load
```

Auf dem Pi dann ein `docker-compose.yml`, das diese Images per `image:` nutzt
statt `build:`. Alternativ einfach `docker compose up -d --build` direkt auf dem
Pi laufen lassen (dauert beim ersten Mal länger, ist aber am einfachsten).

## Sicherheit

- Im `lan`-Profil ist **kein Login (OIDC)** aktiv – bewusst, da das iPad im
  vertrauenswürdigen Heimnetz hängt. Den Server **nicht** ins Internet exponieren
  (kein Port-Forwarding auf 8080). Für Fernzugriff stattdessen VPN ins Heimnetz.
- `config/application.properties` enthält Secrets und ist per `.gitignore`
  ausgeschlossen – nur lokal auf dem Server halten.

## Stolpersteine

- **Geräte offline / Discovery findet nichts:** host-mode nötig (Linux-Host) und
  Server im selben Subnetz wie die Geräte. WLAN-Gast-/IoT-Netze trennen oft
  Broadcasts – Server und Geräte ins gleiche (V)LAN.
- **Klimaanlage offline:** der `sidecar`-Container muss laufen
  (`docker compose logs sidecar`).
- **DB-Fehler beim Start:** `db` zuerst hochfahren lassen; `app` hat
  `depends_on` + `restart: unless-stopped` und verbindet sich nach.
