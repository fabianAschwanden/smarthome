#!/usr/bin/env bash
# Ein-Befehl-Update & Start des Heim-Servers: holt den neuesten Stand aus dem Netz,
# stellt den Fly-Tunnel sicher und (neu) baut + startet alle Container.
#
# Aufruf auf dem Linux-Server (im geklonten Repo):
#   bash scripts/server-update.sh
#
# Macht der Reihe nach:
#   1. git pull        – aktuelle Compose-/Config-Dateien vom Repo (origin/main)
#   2. Konfiguration   – deploy/.env und config/application.properties sicherstellen
#   3. Fly-Tunnel      – WireGuard (wg-quick@fly) aktiv? sonst aktivieren
#   4. docker compose  – vorgebaute ghcr-Images ziehen (pull) und starten
#
# Die App-/Sidecar-Images kommen als versioniertes Release aus der GitHub Container
# Registry (ghcr.io) – kein lokaler Build. Die gewünschte Version steuert IMAGE_TAG
# in deploy/.env ('latest' oder z. B. 1.2.0).
#
# Voraussetzungen (einmalig, siehe docs/server/SETUP.md & docs/remote/SETUP.md):
#   - Docker + docker compose installiert
#   - /etc/wireguard/fly.conf eingerichtet (flyctl wireguard create ... > fly.conf)
#   - config/application.properties mit den echten Geräte-Daten gefüllt
#   - bei privatem Repo: docker login ghcr.io (sonst sind die Images öffentlich lesbar)
set -euo pipefail

COMPOSE="docker-compose.release.yml"

HERE="$(cd "$(dirname "$0")/.." && pwd)"
cd "$HERE"

BRANCH="${SMARTHOME_BRANCH:-main}"
WG_IFACE="fly"   # entspricht /etc/wireguard/fly.conf -> wg-quick@fly

echo "==> 1/4 Neuesten Stand holen (origin/$BRANCH)"
git fetch --quiet origin "$BRANCH"
git checkout --quiet "$BRANCH"
BEFORE="$(git rev-parse --short HEAD)"
git pull --ff-only origin "$BRANCH"
AFTER="$(git rev-parse --short HEAD)"
if [[ "$BEFORE" == "$AFTER" ]]; then
  echo "    Bereits aktuell ($AFTER)."
else
  echo "    Aktualisiert: $BEFORE -> $AFTER"
fi

echo "==> 2/4 Konfiguration prüfen"
if [[ ! -f deploy/.env ]]; then
  cp deploy/.env.example deploy/.env
  PW="$(openssl rand -hex 16)"
  sed -i "s|^DB_PASSWORD=.*|DB_PASSWORD=$PW|" deploy/.env
  echo "    deploy/.env erstellt, DB-Passwort generiert."
else
  echo "    deploy/.env vorhanden."
fi
if [[ ! -f config/application.properties ]]; then
  cp config/application.properties.example config/application.properties
  echo "    WARN: config/application.properties aus Vorlage erstellt – bitte device-ids,"
  echo "          local-keys/Token, IPs und Wetter-Standort eintragen, dann erneut starten."
fi

echo "==> 3/4 Fly-Tunnel (wg-quick@$WG_IFACE) sicherstellen"
if ! command -v wg-quick >/dev/null 2>&1; then
  echo "    WARN: wireguard-tools nicht installiert – Tunnel übersprungen."
  echo "          sudo apt-get install -y wireguard-tools"
elif [[ ! -f "/etc/wireguard/$WG_IFACE.conf" ]]; then
  echo "    WARN: /etc/wireguard/$WG_IFACE.conf fehlt – Tunnel nicht eingerichtet."
  echo "          Einmalig: flyctl wireguard create <org> <region> smarthome-home > fly.conf"
  echo "          dann: sudo cp fly.conf /etc/wireguard/$WG_IFACE.conf"
elif systemctl is-active --quiet "wg-quick@$WG_IFACE"; then
  echo "    Tunnel läuft bereits."
else
  echo "    Tunnel nicht aktiv – aktiviere (systemctl enable --now)…"
  sudo systemctl enable --now "wg-quick@$WG_IFACE"
  echo "    Tunnel gestartet."
fi

echo "==> 4/4 Release-Images ziehen und starten"
cd "$HERE/deploy"
# go2rtc nur starten, wenn eine Kamera-Config vorhanden ist (sonst würde der Container failen).
if [[ -f go2rtc/go2rtc.yaml ]]; then
  SERVICES=""
else
  echo "    Hinweis: deploy/go2rtc/go2rtc.yaml fehlt – starte ohne Kamera-Gateway."
  echo "             (Vorlage: cp deploy/go2rtc/go2rtc.example.yaml deploy/go2rtc/go2rtc.yaml)"
  SERVICES="db sidecar app"
fi
sudo docker compose -f "$COMPOSE" pull $SERVICES
sudo docker compose -f "$COMPOSE" up -d $SERVICES
# Alte, nicht mehr referenzierte Images aufräumen (hält die Platte schlank).
sudo docker image prune -f >/dev/null 2>&1 || true

IP="$(hostname -I 2>/dev/null | awk '{print $1}')"
echo
echo "Fertig. Lokal:   http://${IP:-<server-ip>}:8080"
echo "Remote:          https://smarthome-remote.fly.dev  (über Fly-Login + Tunnel)"
echo "Status:          cd deploy && sudo docker compose ps"
echo "Logs:            cd deploy && sudo docker compose logs -f app"
