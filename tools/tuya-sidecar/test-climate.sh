#!/usr/bin/env bash
# Live-Test des Klima-Sidecars OHNE Secrets auf der Kommandozeile.
# Liest deviceId/token/key/ip aus der gitignored config/application.properties
# und ruft /climate/read bzw. /climate/control auf.
#
#   tools/tuya-sidecar/test-climate.sh read
#   tools/tuya-sidecar/test-climate.sh control power=true mode=COOL target=24
set -euo pipefail

CFG="$(cd "$(dirname "$0")/../.." && pwd)/config/application.properties"
SIDECAR="${SIDECAR_URL:-http://127.0.0.1:8765}"

prop() { grep -E "^climate\.devices\[0\]\.$1=" "$CFG" | head -1 | cut -d= -f2-; }

ID="$(prop device-id)"
TOKEN="$(prop token)"
KEY="$(prop key)"
IP="$(prop address)"

[ -n "$ID" ] && [ -n "$TOKEN" ] && [ -n "$KEY" ] && [ -n "$IP" ] || {
    echo "Secrets fehlen in $CFG" >&2; exit 1; }

cmd="${1:-read}"; shift || true
extra=""
for kv in "$@"; do extra+="&$kv"; done

# Secrets via --data-urlencode, damit sie nicht in der Prozessliste/argv landen.
curl -sS -G "$SIDECAR/climate/$cmd" \
    --data-urlencode "id=$ID" \
    --data-urlencode "token=$TOKEN" \
    --data-urlencode "key=$KEY" \
    --data-urlencode "ip=$IP" \
    ${extra:+--data "${extra#&}"}
echo
