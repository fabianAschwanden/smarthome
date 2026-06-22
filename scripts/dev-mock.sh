#!/usr/bin/env bash
# Lokaler Entwicklungs-Start im MOCK-Modus – läuft ohne Heimnetz/Geräte.
#
#   bash scripts/dev-mock.sh          # nur Mocks (Standard) – kein Sidecar/go2rtc
#   bash scripts/dev-mock.sh --real   # echte Geräte (Profil dev,live) + Sidecar + go2rtc
#
# Mock-Modus (Standard):
#   Quarkus dev (Backend :8080 + Angular-Dev-Server :4200), Profil dev, aber mit
#   erzwungenem smarthome.real-devices=false und neutralisierten Geräte-URLs –
#   damit eine lokale config/application.properties (die %dev evtl. auf echte
#   Geräte stellt) NICHT durchschlägt und nichts ins LAN funkt.
#
# --real: zusätzlich Sidecar (:8765) und go2rtc-Gateway (:1984/:8555) für den
#   Betrieb im Heimnetz. Strg+C beendet Quarkus und räumt die Dienste wieder auf.
set -euo pipefail

HERE="$(cd "$(dirname "$0")/.." && pwd)"
cd "$HERE"

REAL=0
[[ "${1:-}" == "--real" ]] && REAL=1

VENV_PY="$HERE/.tuya-venv/bin/python"
SIDECAR="$HERE/tools/tuya-sidecar/sidecar.py"
GO2RTC_CFG="$HERE/deploy/go2rtc/go2rtc.yaml"
GO2RTC_NAME="go2rtc"

started_sidecar=0
started_go2rtc=0

cleanup() {
  echo
  echo "==> Räume Begleitdienste auf …"
  if [[ $started_sidecar -eq 1 && -n "${SIDECAR_PID:-}" ]]; then
    kill "$SIDECAR_PID" 2>/dev/null || true
    echo "    Sidecar gestoppt."
  fi
  if [[ $started_go2rtc -eq 1 ]]; then
    docker rm -f "$GO2RTC_NAME" >/dev/null 2>&1 || true
    echo "    go2rtc-Container gestoppt."
  fi
}

# Hält ein Dienst den Port schon? (dann nicht doppelt starten)
port_busy() { lsof -nP -iTCP:"$1" -sTCP:LISTEN >/dev/null 2>&1; }

# --- MOCK (Standard) -------------------------------------------------------
if [[ $REAL -eq 0 ]]; then
  echo "==> Mock-Modus: Quarkus dev ohne echte Geräte (kein Heimnetz nötig)."
  echo "    Strg+C beendet."
  echo
  # real-devices und Geräte-URLs hart auf Mock/ungültig zwingen – sticht eine
  # lokale config/application.properties, die %dev auf echte Geräte stellen würde.
  exec ./mvnw quarkus:dev -Dquarkus.profile=dev \
    -Dsmarthome.real-devices=false \
    -Dsmarthome.fronius.enabled=false \
    -Denergy.fronius.base-url=http://fronius.invalid \
    -Denergy.smartfox.base-url=http://smartfox.invalid \
    -Dbattery.smartfox.relay-url=http://smartfox.invalid/setswrel.cgi
fi

# --- REAL: Begleitdienste + echte Geräte -----------------------------------
trap cleanup EXIT INT TERM

echo "==> 1/3 Sidecar (:8765)"
if port_busy 8765; then
  echo "    Port 8765 bereits belegt – nutze laufenden Sidecar."
elif [[ -x "$VENV_PY" ]]; then
  HOST=127.0.0.1 PORT=8765 "$VENV_PY" "$SIDECAR" >/tmp/smarthome-sidecar.log 2>&1 &
  SIDECAR_PID=$!
  started_sidecar=1
  echo "    Sidecar gestartet (PID $SIDECAR_PID, Log: /tmp/smarthome-sidecar.log)."
else
  echo "    WARN: kein venv ($VENV_PY) – Sidecar übersprungen."
  echo "          venv anlegen:  python3 -m venv .tuya-venv && .tuya-venv/bin/pip install -r tools/tuya-sidecar/requirements.txt"
fi

echo "==> 2/3 go2rtc-Kamera-Gateway (:1984/:8555)"
if port_busy 1984; then
  echo "    Port 1984 bereits belegt – nutze laufendes go2rtc."
elif [[ ! -f "$GO2RTC_CFG" ]]; then
  echo "    Keine $GO2RTC_CFG – Kamera-Gateway übersprungen."
  echo "          Vorlage:  cp deploy/go2rtc/go2rtc.example.yaml deploy/go2rtc/go2rtc.yaml"
elif ! command -v docker >/dev/null 2>&1; then
  echo "    WARN: docker nicht gefunden – Kamera-Gateway übersprungen."
else
  docker rm -f "$GO2RTC_NAME" >/dev/null 2>&1 || true
  docker run -d --name "$GO2RTC_NAME" -p 1984:1984 -p 8555:8555 \
    -v "$GO2RTC_CFG:/config/go2rtc.yaml:ro" \
    alexxit/go2rtc:latest >/dev/null
  started_go2rtc=1
  echo "    go2rtc-Container gestartet."
fi

echo "==> 3/3 Quarkus dev (Backend :8080, Angular :4200) – Profil dev,live (echte Geräte)"
echo "    Strg+C beendet alles."
echo
./mvnw quarkus:dev -Dquarkus.profile=dev,live
