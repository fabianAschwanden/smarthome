#!/bin/sh
# Startet oauth2-proxy als Login-Reverse-Proxy (Default-Provider: Google). Konfig kommt
# aus den Fly-Secrets (flyctl secrets set …). Reicht authentifizierte Requests an
# UPSTREAM (Heim-Server über das Fly-6PN, z. B. http://[fdaa:…]:8080) weiter.
set -eu

: "${OIDC_CLIENT_ID:?OIDC_CLIENT_ID fehlt (Google OAuth Client-ID)}"
: "${OIDC_CLIENT_SECRET:?OIDC_CLIENT_SECRET fehlt}"
: "${COOKIE_SECRET:?COOKIE_SECRET fehlt (openssl rand -base64 32)}"
: "${UPSTREAM:?UPSTREAM fehlt (z. B. http://[<6pn-ip>]:8080)}"

PROVIDER="${OAUTH_PROVIDER:-google}"

# --pass-access-token bewusst NICHT gesetzt: der Upstream wertet den Google-Access-Token
# nicht aus, er landete nur in dessen Logs/Traces. Was gebraucht wird, liefert
# --set-xauthrequest (X-Auth-Request-Email) – ohne Token-Material.
#
# --cookie-samesite: bewusst 'lax', NICHT 'strict'. Bei 'strict' schickt der Browser den
# CSRF-Cookie beim Rücksprung des Providers auf /oauth2/callback nicht mit, der Login
# scheitert mit "unable to obtain CSRF cookie" (oauth2-proxy/oauth2-proxy#1663). 'lax'
# blockt Cross-Site-POSTs und Subresource-GETs und ist explizit statt browserabhängig.

set -- \
  --http-address="0.0.0.0:4180" \
  --provider="$PROVIDER" \
  --client-id="$OIDC_CLIENT_ID" \
  --client-secret="$OIDC_CLIENT_SECRET" \
  --cookie-secret="$COOKIE_SECRET" \
  --upstream="$UPSTREAM" \
  --reverse-proxy="true" \
  --skip-provider-button="true" \
  --cookie-secure="true" \
  --cookie-samesite="lax" \
  --set-xauthrequest="true"

# Generischer OIDC-Provider braucht zusätzlich die Issuer-URL (Google nicht).
if [ "$PROVIDER" = "oidc" ]; then
  : "${OIDC_ISSUER_URL:?OIDC_ISSUER_URL fehlt (nur beim oidc-Provider)}"
  set -- "$@" --oidc-issuer-url="$OIDC_ISSUER_URL"
fi

# Zugriff einschränken. Bei Google ohne Filter könnte sich JEDES Google-Konto einloggen,
# darum: entweder erlaubte Adressen (ALLOWED_EMAILS) ODER eine Domain. Mind. eines setzen.
if [ -n "${ALLOWED_EMAILS:-}" ]; then
  # Kommagetrennte Liste -> eine Adresse pro Zeile (POSIX-sh, ohne 'tr').
  : > /tmp/emails.txt
  rest="$ALLOWED_EMAILS"
  while [ -n "$rest" ]; do
    case "$rest" in
      *,*) printf '%s\n' "${rest%%,*}" >> /tmp/emails.txt; rest="${rest#*,}" ;;
      *)   printf '%s\n' "$rest" >> /tmp/emails.txt; rest="" ;;
    esac
  done
  set -- "$@" --authenticated-emails-file=/tmp/emails.txt
  # email-domain=* nötig, sonst überschreibt der Default die Adressliste nicht korrekt.
  set -- "$@" --email-domain="*"
elif [ -n "${ALLOWED_EMAIL_DOMAINS:-}" ]; then
  set -- "$@" --email-domain="$ALLOWED_EMAIL_DOMAINS"
else
  echo "FEHLER: Weder ALLOWED_EMAILS noch ALLOWED_EMAIL_DOMAINS gesetzt." >&2
  echo "Ohne Beschränkung käme jedes Google-Konto rein. Abbruch." >&2
  exit 1
fi

exec oauth2-proxy "$@"
