#!/bin/sh
# Startet oauth2-proxy als OIDC-Login-Reverse-Proxy. Konfiguration kommt aus den
# Fly-Secrets (flyctl secrets set …). Reicht authentifizierte Requests an UPSTREAM
# (Heim-Server über das Fly-6PN, z. B. http://[fdaa:…]:8080) weiter.
set -eu

: "${OIDC_ISSUER_URL:?OIDC_ISSUER_URL fehlt (flyctl secrets set OIDC_ISSUER_URL=…)}"
: "${OIDC_CLIENT_ID:?OIDC_CLIENT_ID fehlt}"
: "${OIDC_CLIENT_SECRET:?OIDC_CLIENT_SECRET fehlt}"
: "${COOKIE_SECRET:?COOKIE_SECRET fehlt (openssl rand -base64 32)}"
: "${UPSTREAM:?UPSTREAM fehlt (z. B. http://[<6pn-ip>]:8080)}"

# Wer darf rein? Standard: jede vom IdP authentifizierte Person. Über ALLOWED_EMAILS
# (kommagetrennte Adressen) bzw. ALLOWED_EMAIL_DOMAINS einschränken.
set -- \
  --http-address="0.0.0.0:4180" \
  --provider="oidc" \
  --oidc-issuer-url="$OIDC_ISSUER_URL" \
  --client-id="$OIDC_CLIENT_ID" \
  --client-secret="$OIDC_CLIENT_SECRET" \
  --cookie-secret="$COOKIE_SECRET" \
  --email-domain="${ALLOWED_EMAIL_DOMAINS:-*}" \
  --upstream="$UPSTREAM" \
  --reverse-proxy="true" \
  --skip-provider-button="true" \
  --cookie-secure="true" \
  --pass-access-token="true" \
  --set-xauthrequest="true"

# Optional: nur bestimmte E-Mail-Adressen zulassen (eine pro Zeile via Secret).
if [ -n "${ALLOWED_EMAILS:-}" ]; then
  printf '%s\n' "$ALLOWED_EMAILS" | tr ',' '\n' > /tmp/emails.txt
  set -- "$@" --authenticated-emails-file=/tmp/emails.txt
fi

exec oauth2-proxy "$@"
