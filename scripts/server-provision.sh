#!/usr/bin/env bash
# Provisioniert einen frischen Ubuntu-Server-26.04-Mini-PC für den smarthome-Server:
# Docker + Compose, Firewall, Zeitzone, automatische Sicherheitsupdates.
# Aufruf:  sudo bash scripts/server-provision.sh [LAN-CIDR]
#   z. B.  sudo bash scripts/server-provision.sh 192.168.1.0/24
set -euo pipefail

LAN_CIDR="${1:-192.168.0.0/16}"

if [[ $EUID -ne 0 ]]; then
  echo "Bitte mit sudo ausführen." >&2
  exit 1
fi

echo "==> System aktualisieren"
apt-get update -y && apt-get upgrade -y

echo "==> Basis-Pakete"
apt-get install -y ca-certificates curl gnupg ufw unattended-upgrades chrony

echo "==> Zeitzone"
timedatectl set-timezone Europe/Zurich || true

echo "==> Docker Engine + Compose-Plugin"
install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
chmod a+r /etc/apt/keyrings/docker.asc
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] \
https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" \
  > /etc/apt/sources.list.d/docker.list
apt-get update -y
apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
systemctl enable --now docker

echo "==> Aktuellen Benutzer in die docker-Gruppe (neu einloggen nötig)"
TARGET_USER="${SUDO_USER:-$USER}"
usermod -aG docker "$TARGET_USER" || true

echo "==> Firewall (ufw): SSH + App-Port 8080 nur aus dem LAN"
ufw default deny incoming
ufw default allow outgoing
ufw allow 22/tcp
ufw allow from "$LAN_CIDR" to any port 8080 proto tcp
ufw --force enable

echo "==> Automatische Sicherheitsupdates aktivieren"
dpkg-reconfigure -f noninteractive unattended-upgrades || true

echo
echo "Fertig. Nächste Schritte:"
echo "  1) Einmal aus- und wieder einloggen (docker-Gruppe)."
echo "  2) Image bauen oder laden (siehe docs/server/SETUP.md)."
echo "  3) cd deploy && cp .env.example .env && nano .env && docker compose up -d"
