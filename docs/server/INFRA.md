# Infrastruktur / Kubernetes → eigenes Repo

Der Kubernetes-Teil (k3s-Plattform, Host-Provisioning, Deploy-Manifeste) wurde in ein
eigenes Repo ausgelagert, das **mehrere Anwendungen** betreiben kann:

→ **https://github.com/fabianAschwanden/onprem-infrastructure**

Dort liegt:

- `scripts/provision-host.sh`, `scripts/k3s-install.sh` – Host + k3s aufsetzen.
- `apps/smarthome/` – die Deployment-Manifeste dieser App (vormals `deploy/k8s/`).
- `scripts/deploy-app.sh smarthome` – ausrollen/aktualisieren.
- `docs/MIGRATION.md` – Umstieg von Docker-Compose nach k3s (mit Datenübernahme).
- `docs/ADD-APP.md`, `docs/LEARN.md` – neue App hinzufügen, Kubernetes-Grundlagen.

In **diesem** App-Repo bleibt nur die App selbst plus der einfache Docker-Compose-Weg
(`deploy/docker-compose*.yml`, `docs/server/SETUP.md`). Die Geräte-Config
(`config/application.properties`) liest das Infra-Deployment per `pre-deploy.sh` aus
diesem Repo aus (Pfad via `SMARTHOME_REPO` überschreibbar).
