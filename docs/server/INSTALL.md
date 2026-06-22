# Mini-PC Installation – Schritt für Schritt (Ubuntu 26.04 · ghcr-Releases · Remote)

Konkreter Ablauf für die Erstinstallation des Smart-Home-Servers auf dem Mini-PC
(Minix NEO Z95 / Intel N95), mit **vorgebauten ghcr-Images** (kein Build auf dem N95)
und **Remote-Zugang über den Fly-Login-Proxy + WireGuard**.

Legende: 🖥️ = am Mini-PC (per SSH) · 💻 = an deinem Mac · ☁️ = Fly/Browser.

---

## Phase 0 – BIOS & OS (vor allem anderen)

🖥️ **BIOS** (beim Start `Entf`/`F7`):
- *Restore on AC Power Loss* → **Power On**
- Sleep/Suspend **aus**
- Boot: USB-Stick zuerst

🖥️ **Ubuntu Server 26.04 LTS** installieren:
1. ISO: <https://ubuntu.com/download/server> → USB-Stick (balenaEtcher/Rufus)
2. Installer: Sprache/Tastatur, Netzwerk (DHCP), **„Install OpenSSH server" ankreuzen**,
   Benutzer `fabian` anlegen.
3. Stick raus, neu starten.

💻 **Feste IP** am einfachsten per **DHCP-Reservierung im Router** (MAC des Servers).
Notiere die IP, z. B. `192.168.1.10`.

💻 Einloggen:
```bash
ssh fabian@192.168.1.10
```

---

## Phase 1 – Repo & Provisioning

🖥️ Repo klonen und das System vorbereiten (Docker, Firewall, Zeitzone, Auto-Updates,
wireguard-tools):
```bash
git clone https://github.com/fabianAschwanden/smarthome.git
cd smarthome
sudo bash scripts/server-provision.sh 192.168.1.0/24    # dein LAN-CIDR
```

🖥️ **Einmal ab- und wieder anmelden** (damit die docker-Gruppe greift):
```bash
exit
ssh fabian@192.168.1.10
cd smarthome
docker ps        # muss ohne sudo funktionieren
```

---

## Phase 2 – Konfiguration (Geräte + DB)

🖥️ Gerätedaten anlegen (gitignored, nie ins Repo):
```bash
cp config/application.properties.example config/application.properties
nano config/application.properties
```
Fülle die **`%lan.*`**-Einträge: device-ids, local-keys/Token, **feste Geräte-IPs**,
Wetter-Standort. (Vorlage zeigt jede Zeile.)

🖥️ DB-Passwort + Image-Tag prüfen:
```bash
cp deploy/.env.example deploy/.env
nano deploy/.env
```
- `DB_PASSWORD=` → ein sicheres Passwort setzen (z. B. `openssl rand -hex 16`)
- `IMAGE_OWNER=fabianaschwanden` (passt)
- `IMAGE_TAG=latest` (oder `1.0.0` für die fixe Version)

🖥️ Kamera-Gateway (optional, falls Tuya-Cam genutzt wird):
```bash
cp deploy/go2rtc/go2rtc.example.yaml deploy/go2rtc/go2rtc.yaml
nano deploy/go2rtc/go2rtc.yaml    # echte rtsp://<kamera-ip>:554/... eintragen
```

---

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
**LAN-Test:** im Browser `http://192.168.1.10:8080` → Dashboard. ✅

---

## Phase 4 – Remote-Zugang (Fly-Tunnel + Proxy)

Der Mini-PC braucht einen **eigenen** WireGuard-Peer (der `smarthome-laptop` von gestern
bleibt unberührt). Danach zeigt der Fly-Proxy auf den Mini-PC statt auf den Laptop.

💻 **(am Mac)** Peer für den Server erzeugen und die 6PN-IP merken:
```bash
flyctl wireguard create personal fra smarthome-home > home.conf
grep -E "Address|Endpoint" home.conf      # die fdaa:…-Address ist die UPSTREAM-IP
```

💻 `home.conf` auf den Server kopieren:
```bash
scp home.conf fabian@192.168.1.10:/home/fabian/
```

🖥️ **(am Mini-PC)** Tunnel als Dienst installieren (dauerhaft, ausgehend):
```bash
sudo cp /home/fabian/home.conf /etc/wireguard/fly.conf
rm /home/fabian/home.conf
sudo systemctl enable --now wg-quick@fly
sudo wg show          # 'latest handshake' muss erscheinen
```

💻 **(am Mac)** Den Fly-Proxy auf die Mini-PC-6PN-IP umstellen (UPSTREAM-Secret) und
ALLOWED_EMAILS prüfen:
```bash
flyctl secrets set -a smarthome-remote \
  UPSTREAM='http://[<MINI-PC-6PN-IP>]:8080'
```
(Die anderen Secrets – OIDC_CLIENT_ID/SECRET, COOKIE_SECRET, ALLOWED_EMAILS – sind von
gestern gesetzt und bleiben. Setzen des Secrets triggert einen Redeploy.)

☁️ **Test:** `https://smarthome-remote.fly.dev` im Browser (am besten Handy über Mobilfunk)
→ Google-Login → Dashboard vom Mini-PC. ✅

> Falls die Kamera remote schwarz bleibt: go2rtc läuft, der Backend-Proxy reicht
> `/go2rtc/*` durch – im LAN zuerst testen (`http://192.168.1.10:8080` → Kameras).

---

## Phase 5 – Betrieb & Updates

🖥️ Updates (neues Release): einfach erneut
```bash
cd ~/smarthome && bash scripts/server-update.sh
```

🖥️ Alltag:
```bash
cd ~/smarthome/deploy
docker compose -f docker-compose.release.yml ps          # Status
docker compose -f docker-compose.release.yml logs -f app # Logs
docker compose -f docker-compose.release.yml restart app # neu starten
```

🖥️ DB-Backup (Cron empfohlen):
```bash
docker compose -f docker-compose.release.yml exec -T db \
  pg_dump -U smarthome smarthome > ~/backup-$(date +%F).sql
```

---

## Wenn etwas klemmt

Öffne eine SSH-Sitzung und füge die Ausgabe hier ein – ich gehe Befehl für Befehl mit.
Typische Stolpersteine: docker-Gruppe (neu einloggen), Geräte-IPs nicht fest (Discovery
scheitert über Subnetze), Fly-Tunnel-Handshake fehlt (`sudo wg show`), UPSTREAM zeigt noch
auf den Laptop statt den Mini-PC.
