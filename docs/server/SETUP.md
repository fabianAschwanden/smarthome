# Heim-Server aufsetzen – Minix NEO Z95 (Intel N95)

Ziel: den Mini-PC mit **möglichst wenig Aufwand** als Smart-Home-Server betreiben.
Distribution: **Ubuntu Server 26.04 LTS** (beste N95-Hardware-Unterstützung, 5 Jahre
Support; Debian 13 „trixie" geht auch, gleicher Stack). Auf dem Server wird **nur Docker**
gebraucht – die App kommt als vorgebautes Container-Image (kein Java/Node/Maven nötig).

Der erste Teil ist die **Schritt-für-Schritt-Erstinstallation** für das konkrete Setup
(feste IP `192.168.113.117`, ghcr-Release-Images, Remote über Fly-Tunnel). Darunter folgt
der **Referenzteil** (Netzwerk/Ports, Auth, Betrieb, alternativer Build-Weg).

Legende: 🖥️ = am Mini-PC (per SSH) · 💻 = an deinem Mac · ☁️ = Fly/Browser.

---

# Teil A — Erstinstallation (Schritt für Schritt)

## Phase 0 – BIOS & OS

🖥️ **BIOS** (beim Start `Entf`/`F7`):
- *Restore on AC Power Loss* → **Power On** (nach Stromausfall automatisch starten)
- Sleep/Suspend **aus** (Server soll nie schlafen)
- Boot-Reihenfolge: USB-Stick zuerst · Secure Boot kann an bleiben

🖥️ **Ubuntu Server 26.04 LTS** installieren:
1. ISO: <https://ubuntu.com/download/server> → USB-Stick (balenaEtcher/Rufus)
2. Installer: Sprache/Tastatur, Netzwerk (DHCP reicht erst mal), **„Install OpenSSH server"
   ankreuzen**, Benutzer `fabian` anlegen. Keine zusätzlichen Snaps nötig.
3. Stick raus, neu starten.

💻 **Feste IP: `192.168.113.117`** – am einfachsten per **DHCP-Reservierung im Router**
(MAC des Servers). Liegt bewusst im selben Subnetz wie die Geräte (192.168.113.x), damit
die Tuya-Discovery (UDP-Broadcast) ohne Subnetz-Grenzen funktioniert.
Alternativ direkt am Server (Netplan) – siehe Teil B.

💻 Einloggen:
```bash
ssh fabian@192.168.113.117
```

## Phase 1 – Repo & Provisioning

🖥️ Repo klonen und das System vorbereiten (Docker, Firewall, Zeitzone, Auto-Updates,
wireguard-tools):
```bash
git clone https://github.com/fabianAschwanden/smarthome.git
cd smarthome
sudo bash scripts/server-provision.sh 192.168.113.0/24    # dein LAN-CIDR
```

🖥️ **Einmal ab- und wieder anmelden** (damit die docker-Gruppe greift):
```bash
exit
ssh fabian@192.168.113.117
cd smarthome
docker ps        # muss ohne sudo funktionieren
```

## Phase 2 – Konfiguration (Geräte + DB)

🖥️ Gerätedaten anlegen (gitignored, nie ins Repo committen):
```bash
cp config/application.properties.example config/application.properties
nano config/application.properties
```
Fülle die **`%lan.*`**-Einträge: device-ids, local-keys/Token, **feste Geräte-IPs**,
Wetter-Standort. (Vorlage zeigt jede Zeile.)

🖥️ DB-Passwort + Image-Tag:
```bash
cp deploy/.env.example deploy/.env
nano deploy/.env
```
- `DB_PASSWORD=` → sicheres Passwort (z. B. `openssl rand -hex 16`)
- `IMAGE_OWNER=fabianaschwanden` · `IMAGE_TAG=latest` (oder `1.0.0` für die fixe Version)

🖥️ Kamera-Gateway (optional, falls Tuya-Cam genutzt wird):
```bash
cp deploy/go2rtc/go2rtc.example.yaml deploy/go2rtc/go2rtc.yaml
nano deploy/go2rtc/go2rtc.yaml    # echte rtsp://<kamera-ip>:554/... eintragen
```

## Phase 3 – Start (zieht die ghcr-Images, kein Build)

🖥️
```bash
bash scripts/server-update.sh
```
Das Skript: `git pull` · sichert `.env`/config · prüft den Fly-Tunnel (noch nicht da,
Hinweis ist ok) · `docker compose -f docker-compose.release.yml pull` + `up -d`.

🖥️ Prüfen:
```bash
cd deploy && docker compose -f docker-compose.release.yml ps
curl -s localhost:8080/q/health
```
**LAN-Test:** im Browser `http://192.168.113.117:8080` → Dashboard. ✅

## Phase 4 – Remote-Zugang (Fly-Tunnel + Proxy)

Der Mini-PC braucht einen **eigenen** WireGuard-Peer (`smarthome-laptop` bleibt unberührt).
Danach zeigt der Fly-Proxy auf den Mini-PC statt auf den Laptop. Hintergrund:
[`../remote/SETUP.md`](../remote/SETUP.md).

💻 **(am Mac)** Peer für den Server erzeugen und die 6PN-IP merken:
```bash
flyctl wireguard create personal fra smarthome-home > home.conf
grep -E "Address|Endpoint" home.conf      # die fdaa:…-Address ist die UPSTREAM-IP
```

💻 `home.conf` auf den Server kopieren:
```bash
scp home.conf fabian@192.168.113.117:/home/fabian/
```

🖥️ **(am Mini-PC)** Tunnel als Dienst installieren (dauerhaft, ausgehend):
```bash
sudo cp /home/fabian/home.conf /etc/wireguard/fly.conf
rm /home/fabian/home.conf
sudo systemctl enable --now wg-quick@fly
sudo wg show          # 'latest handshake' muss erscheinen
```

💻 **(am Mac)** Den Fly-Proxy auf die Mini-PC-6PN-IP umstellen (UPSTREAM-Secret):
```bash
flyctl secrets set -a smarthome-remote \
  UPSTREAM='http://[<MINI-PC-6PN-IP>]:8080'
```
(Die anderen Secrets – OIDC_CLIENT_ID/SECRET, COOKIE_SECRET, ALLOWED_EMAILS – bleiben.
Das Setzen triggert einen Redeploy.)

☁️ **Test:** `https://smarthome-remote.fly.dev` (am besten Handy über Mobilfunk)
→ Google-Login → Dashboard vom Mini-PC. ✅

> Kamera remote schwarz? go2rtc läuft, der Backend-Proxy reicht `/go2rtc/*` durch –
> im LAN zuerst testen (`http://192.168.113.117:8080` → Kameras).

## Phase 5 – Betrieb & Updates

🖥️ Update (neues Release) – einfach erneut:
```bash
cd ~/smarthome && bash scripts/server-update.sh
```

🖥️ Alltag (im `deploy/`-Ordner):
```bash
docker compose -f docker-compose.release.yml ps          # Status
docker compose -f docker-compose.release.yml logs -f app # Logs
docker compose -f docker-compose.release.yml restart app # neu starten
```

🖥️ DB-Backup (Cron empfohlen):
```bash
docker compose -f docker-compose.release.yml exec -T db \
  pg_dump -U smarthome smarthome > ~/backup-$(date +%F).sql
```

## Wenn etwas klemmt

Öffne eine SSH-Sitzung und füge die Ausgabe hier ein – ich gehe Befehl für Befehl mit.
Typische Stolpersteine: docker-Gruppe (neu einloggen nötig), Geräte-IPs nicht fest
(Discovery scheitert über Subnetze), Fly-Tunnel-Handshake fehlt (`sudo wg show`),
UPSTREAM zeigt noch auf den Laptop statt den Mini-PC.

---

# Teil B — Referenz

## Feste IP per Netplan (statt Router-Reservierung)

`/etc/netplan/01-static.yaml`:
```yaml
network:
  version: 2
  ethernets:
    enp1s0: # mit `ip a` den echten Namen prüfen
      dhcp4: false
      addresses: [192.168.113.117/24]
      routes:
        - to: default
          via: 192.168.113.1   # Gateway deines Routers prüfen
      nameservers:
        addresses: [192.168.113.1, 9.9.9.9]
```
```bash
sudo netplan apply
```

## Netzwerk & Firewall

Die App läuft auf **TCP 8080**; die Firewall (ufw, vom Provisioning gesetzt) erlaubt 8080
nur aus dem LAN-CIDR. Da der Server im **selben Subnetz** wie die Geräte liegt
(192.168.113.x), funktioniert die Geräte-Discovery direkt. Ports:

- Server → Geräte: SMARTFOX/Fronius **TCP 80**, Tuya **TCP 6668** + **UDP 6666/6667**
  (Discovery-Broadcasts), Midea **TCP 6444**, Gecko **UDP 10022**, Kamera-RTSP **TCP 554**.
- Clients → Server: **TCP 8080**. Remote läuft über den Fly-Tunnel (kein Router-Port offen).

## Sicherheit / Auth

Im Profil **`%lan`** (Standard dieses Deployments) ist OIDC **aus** – die App ist im LAN
ohne Login erreichbar (für ein reines Heimnetz okay). Der **Remote**-Zugang erzwingt den
Login dagegen am Fly-Proxy (oauth2-proxy/Google, siehe Phase 4). Alternativ ginge ein
eigener Reverse-Proxy (Caddy + HTTPS/Basic-Auth) oder das `%prod`-Profil mit OIDC.

## Alternativer Weg: lokal im Container bauen (ohne ghcr)

Statt der vorgebauten Images kann der Server alles selbst bauen (erster Build dauert auf
dem N95 mehrere Minuten):
```bash
bash scripts/bootstrap.sh 192.168.113.0/24      # provision + lokaler Build + Start
# später Updates:  git pull && cd deploy && sudo docker compose up -d --build
```
Nutzt `deploy/docker-compose.yml` (mit `build:`) statt `docker-compose.release.yml`.
