# Heim-Server aufsetzen – Minix NEO Z95 (Intel N95)

Ziel: den Mini-PC mit **möglichst wenig Aufwand** als Smart-Home-Server betreiben.
Empfohlene Distribution: **Ubuntu Server 26.04 LTS** (beste Hardware-Unterstützung für
den N95, 5 Jahre Support). Alternative: **Debian 13 „trixie"** (schlanker, gleicher Stack).

Auf dem Server wird **nur Docker** gebraucht – Backend (Quarkus) und Frontend (Angular)
werden im Container gebaut. Du brauchst also kein Java/Node/Maven zu installieren.

> **Schritt-für-Schritt-Erstinstallation** (Ubuntu 26.04 + vorgebaute ghcr-Images +
> Remote-Zugang): siehe **[`INSTALL.md`](INSTALL.md)** – der empfohlene, aktuelle Weg
> (kein langer Build auf dem Mini-PC). Dieses Dokument hier ist die Referenz/Hintergrund.

## Kurzfassung (zwei Wege)

**A) Vorgebaute Releases ziehen (empfohlen, schnell):**
```bash
git clone https://github.com/fabianAschwanden/smarthome.git
cd smarthome
sudo bash scripts/server-provision.sh 192.168.113.0/24   # Docker/Firewall/wireguard
# config/application.properties + deploy/.env ausfüllen (siehe INSTALL.md)
bash scripts/server-update.sh                          # zieht ghcr-Images, startet
```

**B) Lokal im Container bauen (kein ghcr nötig, erster Build dauert auf dem N95):**
```bash
git clone https://github.com/fabianAschwanden/smarthome.git
cd smarthome
bash scripts/bootstrap.sh 192.168.113.0/24
```

Dashboard öffnen: `http://<server-ip>:8080`. Der Rest dieses Dokuments erklärt Details und Betrieb.

---

## 1. BIOS-Einstellungen (wichtig für einen Server)

Beim Start `Entf`/`F7` drücken und einstellen:

- **Restore on AC Power Loss → Power On** (nach Stromausfall automatisch starten).
- **Suspend/Sleep deaktivieren** (Server soll nie schlafen).
- Boot-Reihenfolge: USB-Stick zuerst (für die Installation).
- Secure Boot kann an bleiben (Ubuntu unterstützt es).

## 2. Ubuntu Server 26.04 installieren

1. ISO laden: <https://ubuntu.com/download/server> (26.04 LTS).
2. USB-Stick schreiben mit **balenaEtcher** oder **Rufus**.
3. Vom Stick booten, Installer folgen:
   - Sprache/Tastatur, Netzwerk (DHCP reicht erst mal).
   - **„Install OpenSSH server" ankreuzen** (für Fernzugriff).
   - Benutzer anlegen (z. B. `fabian`).
   - Keine zusätzlichen Snaps nötig.
4. Nach der Installation Stick entfernen, neu starten.

Von deinem Rechner einloggen:

```bash
ssh fabian@<server-ip>
```

## 3. Feste IP vergeben

Am einfachsten im Router eine **DHCP-Reservierung** für die MAC des Servers setzen.
Alternativ direkt am Server (Netplan), Beispiel `/etc/netplan/01-static.yaml`:

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

## 4. Bootstrap ausführen

```bash
git clone https://github.com/fabianAschwanden/smarthome.git
cd smarthome
bash scripts/bootstrap.sh 192.168.113.0/24
```

Das Skript (`scripts/bootstrap.sh` → `scripts/server-provision.sh`):

- installiert Docker Engine + Compose-Plugin,
- richtet die Firewall ein (SSH + Port 8080 nur aus dem LAN),
- aktiviert automatische Sicherheitsupdates und die Zeitzone,
- legt `deploy/.env` an (mit generiertem DB-Passwort),
- baut die Container und startet sie.

Der **erste Build** dauert einige Minuten (Maven- und npm-Abhängigkeiten). Danach läuft
alles als Container mit `restart: unless-stopped`, also auch nach Neustart/Stromausfall.

## 5. Geräte konfigurieren

Alle Geräte-Daten (device-ids, local-keys/Token, IPs, Wetter-Standort) stehen
**ausschliesslich** in der gitignored `config/application.properties` – sie wird in
den App-Container gemountet und überschreibt die Standardwerte. **Niemals** Secrets
ins Repo committen.

```bash
cp config/application.properties.example config/application.properties
nano config/application.properties   # %lan.*-Einträge ausfüllen (siehe Vorlage)
```

In `deploy/.env` steht nur das DB-Passwort (siehe `deploy/.env.example`).

Nach Änderungen:

```bash
cd deploy && sudo docker compose up -d --build
```

> **Feste Geräte-IPs sind Pflicht** – die lokale Geräte-Discovery (UDP-Broadcast)
> funktioniert nicht über Subnetz-Grenzen. Ports/Netz siehe Abschnitt 7.

## 6. Betrieb

```bash
cd ~/smarthome/deploy

sudo docker compose logs -f app     # Logs ansehen
sudo docker compose ps              # Status
sudo docker compose restart app     # neu starten
sudo docker compose down            # stoppen

# Update auf neue Version:
cd ~/smarthome && git pull && cd deploy && sudo docker compose up -d --build
```

**Datenbank-Backup** (Cronjob empfohlen):

```bash
sudo docker compose exec -T db pg_dump -U smarthome smarthome > backup-$(date +%F).sql
```

## 7. Netzwerk & Firewall

Die App läuft auf **TCP 8080**. Die Firewall erlaubt 8080 nur aus deinem LAN-CIDR
(siehe Bootstrap-Parameter). Wenn Server und Geräte in **verschiedenen Subnetzen**
liegen, brauchst du Routing (keine Internet-Port-Forwards) und die Geräte-Ports –
Details und die vollständige Port-Liste stehen in der Antwort zur Subnetz-Frage bzw.
können als eigenes Doc ergänzt werden:

- Server → Geräte: SMARTFOX/Fronius **TCP 80**, Tuya **TCP 6668** + **UDP 6666/6667**
  (Discovery-Broadcasts), Midea **TCP 6444**.
- Clients → Server: **TCP 8080**.

## 8. Sicherheit / Auth

Im Profil **`%lan`** (Standard dieses Deployments) ist OIDC **aus** – die App ist im
LAN ohne Login erreichbar. Das ist für ein reines Heimnetz okay. Wenn du sie später von
aussen oder mit Login willst:

- einen Reverse-Proxy (z. B. **Caddy**) mit HTTPS + Basic-Auth davorsetzen, oder
- das `%prod`-Profil mit Keycloak/OIDC nutzen (Blueprint §8).

## 9. Fernzugriff / Support

OpenSSH ist installiert (Schritt 2). Für Hilfe beim Einrichten kannst du eine
SSH-Sitzung öffnen und die Ausgaben hier einfügen – ich leite dich Befehl für Befehl
durch und liefere fertige Snippets. Einen direkten SSH-Zugriff von ausserhalb deines
Netzes solltest du nur bewusst und abgesichert (Key-Auth, ggf. VPN/Tailscale) öffnen.
