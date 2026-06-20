# Betrieb auf NAS / Mini-PC (iPad als Anzeige)

Ziel: Die App läuft dauerhaft auf einem kleinen Always-on-Gerät im LAN
(Synology/QNAP-NAS mit Docker, Mini-PC, kleiner Linux-Server). Das **iPad** ist
nur **Client** – es öffnet in Safari `http://<server-ip>:8080`. Kein grosser
Rechner muss laufen.

> Das iPad selbst kann die App **nicht hosten** (kein Java/PostgreSQL/Python im
> Hintergrund). Es braucht ein Always-on-Gerät als Server.

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
