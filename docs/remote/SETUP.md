# Sicherer Remote-Zugang (von ausserhalb des LAN)

Ziel: Das Dashboard von unterwegs erreichen – **mit Login**, **ohne** einen Port am
Router ins Internet zu öffnen. Das Heimnetz bleibt geschlossen.

## Architektur: Fly als Trusted-Bridge

```
   Internet
      │  HTTPS + Login (OIDC)
      ▼
  ┌─────────────────────────┐      öffentlich (Fly)
  │  Fly-App: Reverse-Proxy  │
  │  oauth2-proxy + Caddy    │
  └───────────┬─────────────┘
              │  WireGuard 6PN (.internal) – AUSGEHEND vom Heim-Server aufgebaut
              ▼
  ┌─────────────────────────┐      privat (zuhause)
  │  Heim-Server (Quarkus)   │
  │  Remote-Port NUR auf dem │
  │  WireGuard-Interface     │
  └───────────┬─────────────┘
              │  LAN
              ▼
        Geräte (Tuya / Gecko / SMARTFOX / Fronius …)
```

Kernpunkte:

- **Kein offener Router-Port.** Der Heim-Server baut die Verbindung zu Fly
  **ausgehend** auf (WireGuard). Eingehend ist zuhause nichts offen.
- **Login am Rand (Fly).** Die öffentliche Fly-App erzwingt OIDC-Login
  (oauth2-proxy). Erst nach erfolgreichem Login werden Requests weitergereicht.
- **App bleibt zuhause.** Auf Fly läuft nur der Proxy; die ganze Quarkus-App und
  die Gerätesteuerung bleiben im LAN (Geräte sind nur dort erreichbar).
- **Login am Rand, nicht am Heim-Server.** Der Heim-Quarkus braucht selbst kein OIDC:
  oauth2-proxy auf Fly erzwingt den Login, **bevor** ein Request weitergereicht wird.
  Der Heim-Server vertraut ausschliesslich der WireGuard-Strecke von Fly.
- **Erreichbarkeit nur via WireGuard.** Fly erreicht den Heim-Quarkus (`:8080`) über
  das private 6PN-Netz – nicht über das öffentliche Internet, kein Router-Forwarding.
  Das LAN-iPad nutzt `:8080` unverändert ohne Login.

## Komponenten

| Ort | Komponente | Aufgabe |
|-----|------------|---------|
| Fly | `oauth2-proxy` | OIDC-Login, lässt nur authentifizierte Requests durch; reicht sie an den Heim-Server über 6PN weiter |
| Zuhause | Quarkus (`lan`) | unverändert auf `:8080`; von Fly nur über WireGuard erreichbar |
| Zuhause | WireGuard | ausgehende Verbindung in Flys privates 6PN-Netz |

## Voraussetzungen

- Heim-Server-Deployment läuft bereits (siehe `docs/server/SETUP.md`, Profil `lan`).
- Fly.io-Account + `flyctl`.
- Ein Google-Konto (Login erfolgt per **Google**; kein eigener IdP-Server nötig).

## 1. Google-OAuth-Client anlegen

In der [Google Cloud Console](https://console.cloud.google.com/):

1. Projekt wählen/anlegen → **APIs & Services → OAuth consent screen**:
   User Type *External*, App-Name vergeben. Im *Testing*-Modus deine Google-Adresse
   unter *Test users* eintragen (dann ist keine Google-Verifizierung nötig).
2. **APIs & Services → Credentials → Create Credentials → OAuth client ID**:
   - Application type: **Web application**
   - Authorized redirect URI: `https://<deine-fly-app>.fly.dev/oauth2/callback`
3. Notiere **Client ID** (`…apps.googleusercontent.com`) und **Client secret**.

Zugriff wird auf die in `ALLOWED_EMAILS` erlaubten Google-Konten beschränkt
(siehe §4) – sonst könnte sich jedes Google-Konto einloggen.

## 2. Heim-Server: keine Änderung nötig

Der Heim-Server läuft unverändert im `lan`-Profil auf `:8080` (siehe
`docs/server/SETUP.md`). Es ist **kein** zusätzliches Quarkus-Profil und **kein**
zweiter Port nötig: Der Login passiert am Fly-Proxy, und erreichbar ist `:8080` von
aussen nur über die WireGuard-Strecke (das LAN bleibt geschlossen).

> Wichtig: `:8080` darf weiterhin **nicht** im Router geforwardet werden. Die einzige
> externe Tür ist die Fly-App.

## 3. WireGuard: Heim-Server ↔ Fly 6PN

```bash
# Einmalig: WireGuard-Peer-Config für den Heim-Server von Fly erzeugen
flyctl wireguard create <org> <region> smarthome-home > home.conf
# Auf dem Heim-Server installieren und aktivieren
sudo cp home.conf /etc/wireguard/fly.conf
sudo systemctl enable --now wg-quick@fly
# Test: Fly-6PN erreichbar?
ping6 _api.internal
```

Die `fdaa:…`-Adresse des Heim-Servers steht in `home.conf` unter `[Interface] Address`
bzw. per `ip -6 addr show dev fly`. Diese IP brauchst du als `UPSTREAM` (§4).

`wg-quick@fly` ist via `systemctl enable` dauerhaft aktiv und baut die Verbindung nach
einem Reboot automatisch wieder auf (ausgehend – kein Router-Port). Prüfen:
`sudo wg show` (zeigt den Handshake mit dem Fly-Gateway).

## 4. Fly-Proxy deployen

Siehe `deploy/fly-remote/` (eigene Fly-App, nur Proxy):

```bash
cd deploy/fly-remote
flyctl launch --no-deploy --copy-config        # App anlegen
flyctl secrets set \
  OIDC_CLIENT_ID=<google-client-id>.apps.googleusercontent.com \
  OIDC_CLIENT_SECRET=<google-client-secret> \
  COOKIE_SECRET=$(openssl rand -hex 16) \   # genau 32 Zeichen; base64 ergäbe 44 -> Fehler
  UPSTREAM=http://[<HEIM-SERVER-6PN-IP>]:8080 \
  ALLOWED_EMAILS=du@gmail.com
flyctl deploy
```

(Login per Google; `ALLOWED_EMAILS` = deine erlaubte(n) Google-Adresse(n),
kommagetrennt. Details siehe `deploy/fly-remote/README.md`.)

`UPSTREAM` zeigt auf den Heim-Server über das 6PN-Netz (die `fdaa:…`-WireGuard-IP
des Heim-Servers, Port 8080). Der Proxy erreicht ihn, weil beide im selben Fly-6PN
sind.

## 5. Nutzung

- Von unterwegs: `https://<deine-fly-app>.fly.dev` → OIDC-Login → Dashboard.
- Im LAN: weiterhin `http://<heim-server>:8080` (ohne Login, unverändert).

## Sicherheit

- Nur die Fly-App ist öffentlich; sie erzwingt Login. Der Heim-Server hat **keinen**
  öffentlich erreichbaren Port und **kein** Router-Forwarding.
- Der Remote-Port bindet nur auf das WireGuard-Interface → selbst im LAN nicht
  erreichbar.
- TLS terminiert Fly (automatische Zertifikate). Cookies signiert (oauth2-proxy).
- Zugriff ist über `ALLOWED_EMAILS` auf die erlaubten Google-Konten beschränkt; der
  Proxy startet nicht ohne eine solche Beschränkung (sonst käme jedes Google-Konto rein).

## Alternative ohne Fly (kurz)

Wer doch direkt forwarden will: dedizierten Port (`%remote` + OIDC, gebunden auf LAN)
am Router auf 8443 forwarden, gültiges TLS-Zertifikat (Let's Encrypt) davor. Grössere
Angriffsfläche – nicht empfohlen, daher hier nicht im Detail.
