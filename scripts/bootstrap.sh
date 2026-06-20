#!/usr/bin/env bash
# Ein-Befehl-Setup für den Mini-PC: Docker installieren, .env anlegen, Container
# bauen + starten. Voraussetzung: frisches Ubuntu Server 26.04, Repo geklont.
#
# Aufruf (im Repo-Ordner):
#   bash scripts/bootstrap.sh 192.168.1.0/24
# (CIDR = dein LAN; ohne Angabe wird 192.168.0.0/16 erlaubt.)
set -euo pipefail

HERE="$(cd "$(dirname "$0")/.." && pwd)"
LAN_CIDR="${1:-192.168.0.0/16}"

echo "==> 1/3 System vorbereiten (Docker, Firewall, Updates)"
sudo bash "$HERE/scripts/server-provision.sh" "$LAN_CIDR"

echo "==> 2/3 Konfiguration (deploy/.env)"
cd "$HERE/deploy"
if [[ ! -f .env ]]; then
  cp .env.example .env
  PW="$(openssl rand -hex 16)"
  sed -i "s|^DB_PASSWORD=.*|DB_PASSWORD=$PW|" .env
  echo "    deploy/.env erstellt, DB-Passwort generiert."
fi
if [[ ! -f "$HERE/config/application.properties" ]]; then
  cp "$HERE/config/application.properties.example" "$HERE/config/application.properties"
  echo "    config/application.properties aus Vorlage erstellt – bitte device-ids,"
  echo "    local-keys/Token, Geräte-IPs und Wetter-Standort eintragen, dann erneut starten."
fi

echo "==> 3/3 Container bauen und starten (erster Build dauert ein paar Minuten)"
sudo docker compose up -d --build

IP="$(hostname -I | awk '{print $1}')"
echo
echo "Fertig. Dashboard:  http://${IP}:8080"
echo "Logs:               cd deploy && sudo docker compose logs -f app"
echo "Update später:      git pull && cd deploy && sudo docker compose up -d --build"
