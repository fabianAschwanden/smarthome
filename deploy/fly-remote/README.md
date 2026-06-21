# Fly-Remote-Proxy (Login-Gateway)

Öffentlicher OIDC-Login-Proxy (oauth2-proxy) für den Remote-Zugang. Vollständiges
Konzept + Schritte: [`../../docs/remote/SETUP.md`](../../docs/remote/SETUP.md).

## Kurzanleitung

1. **WireGuard** vom Heim-Server in Flys 6PN aufbauen (siehe Haupt-Doku §3) und die
   `fdaa:…`-IP des Heim-Servers notieren.

2. **OIDC-Client** beim IdP anlegen, Redirect-URI
   `https://<app>.fly.dev/oauth2/callback`.

3. **App anlegen & Secrets setzen:**
   ```bash
   cd deploy/fly-remote
   flyctl launch --no-deploy --copy-config
   flyctl secrets set \
     OIDC_ISSUER_URL=https://<idp>/realms/<realm> \
     OIDC_CLIENT_ID=smarthome-remote \
     OIDC_CLIENT_SECRET=… \
     COOKIE_SECRET=$(openssl rand -base64 32) \
     UPSTREAM=http://[<heim-server-6pn-ip>]:8080
   # optional zugriff einschränken:
   #   flyctl secrets set ALLOWED_EMAILS=du@example.com
   #   flyctl secrets set ALLOWED_EMAIL_DOMAINS=example.com
   flyctl deploy
   ```

4. Aufrufen: `https://<app>.fly.dev` → Login → Dashboard.

## Secrets (Übersicht)

| Secret | Pflicht | Bedeutung |
|--------|---------|-----------|
| `OIDC_ISSUER_URL` | ja | Issuer-URL des IdP |
| `OIDC_CLIENT_ID` | ja | Client-ID |
| `OIDC_CLIENT_SECRET` | ja | Client-Secret |
| `COOKIE_SECRET` | ja | 32-Byte-Zufall (`openssl rand -base64 32`) |
| `UPSTREAM` | ja | Heim-Server über 6PN, z. B. `http://[fdaa:…]:8080` |
| `ALLOWED_EMAILS` | nein | nur diese Adressen (kommagetrennt) |
| `ALLOWED_EMAIL_DOMAINS` | nein | nur diese Domains (Default: alle) |

Keiner dieser Werte gehört ins Repo – ausschliesslich als Fly-Secrets.
