# Fly-Remote-Proxy (Login-Gateway)

Öffentlicher OIDC-Login-Proxy (oauth2-proxy) für den Remote-Zugang. Vollständiges
Konzept + Schritte: [`../../docs/remote/SETUP.md`](../../docs/remote/SETUP.md).

Login via **Google** (oauth2-proxy `google`-Provider). Zugriff ist auf die in
`ALLOWED_EMAILS` gelisteten Google-Konten beschränkt.

## Kurzanleitung

1. **WireGuard** vom Heim-Server in Flys 6PN aufbauen (siehe Haupt-Doku §3) und die
   `fdaa:…`-IP des Heim-Servers notieren.

2. **Google-OAuth-Client anlegen** (Google Cloud Console):
   - APIs & Services → Credentials → *Create Credentials* → *OAuth client ID*
   - Application type: **Web application**
   - Authorized redirect URI: `https://<app>.fly.dev/oauth2/callback`
   - (OAuth consent screen: *External*, im *Testing*-Modus reicht es, deine
     Google-Adresse als Test-User einzutragen.)
   - Notiere **Client ID** und **Client secret**.

3. **App anlegen & Secrets setzen:**
   ```bash
   cd deploy/fly-remote
   flyctl launch --no-deploy --copy-config
   flyctl secrets set \
     OIDC_CLIENT_ID=<google-client-id>.apps.googleusercontent.com \
     OIDC_CLIENT_SECRET=<google-client-secret> \
     COOKIE_SECRET=$(openssl rand -hex 16) \
     UPSTREAM=http://[<heim-server-6pn-ip>]:8080 \
     ALLOWED_EMAILS=du@gmail.com
   flyctl deploy
   ```

4. Aufrufen: `https://<app>.fly.dev` → „Mit Google anmelden" → Dashboard.

## Secrets (Übersicht)

| Secret | Pflicht | Bedeutung |
|--------|---------|-----------|
| `OIDC_CLIENT_ID` | ja | Google OAuth Client-ID |
| `OIDC_CLIENT_SECRET` | ja | Google OAuth Client-Secret |
| `COOKIE_SECRET` | ja | Cookie-Schlüssel. **Genau 16/24/32 Zeichen** – `openssl rand -hex 16` ergibt 32 Zeichen. (`base64 32` ergibt 44 Zeichen → oauth2-proxy startet nicht!) |
| `UPSTREAM` | ja | Heim-Server über 6PN, z. B. `http://[fdaa:…]:8080` |
| `ALLOWED_EMAILS` | ja* | erlaubte Google-Adressen (kommagetrennt) |
| `ALLOWED_EMAIL_DOMAINS` | ja* | alternativ: erlaubte Workspace-Domain |
| `OAUTH_PROVIDER` | nein | Provider (Default `google`; `oidc` für anderen IdP) |
| `OIDC_ISSUER_URL` | nur bei `oidc` | Issuer-URL (bei Google nicht nötig) |

*Mindestens eines von `ALLOWED_EMAILS` / `ALLOWED_EMAIL_DOMAINS` ist Pflicht – sonst
verweigert der Proxy den Start (sonst käme jedes Google-Konto rein).

Keiner dieser Werte gehört ins Repo – ausschliesslich als Fly-Secrets.
