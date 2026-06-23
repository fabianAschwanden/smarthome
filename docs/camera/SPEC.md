# Spec – Use Case 11: Kameras (Live-Stream)

Status: v1.0 (Tuya/Hankvision-Cam angebunden) · Datum: 2026-06-21 · Plattform: Java 25 / Quarkus (app-template: Hexagonal + DDD)

## 1. Zweck & Scope

Live-Bild einer (oder mehrerer) IP-Kamera(s) im Dashboard anzeigen.

In Scope: Kamera-Metadaten (Name/Raum/Stream-Name) liefern, Live-Stream im Browser
darstellen, Mehrkamera-fähig.
Out of Scope (später): Aufnahme/Archiv, PTZ-Steuerung, Bewegungs-Events,
Zwei-Wege-Audio.

## 2. Anbindung: RTSP → WebRTC über go2rtc

Die reale Kamera ist eine **Tuya/Hankvision-OEM-Cam**. Sie liefert lokal im LAN einen
**RTSP-Stream** (H.265/HEVC, 1920×1080, ~15 fps, kein Auth) unter
`rtsp://<kamera-ip>:554/mpeg4`. Browser können H.265-RTSP nicht direkt abspielen,
darum läuft ein **Gateway**:

- **go2rtc** (`alexxit/go2rtc`) zieht den RTSP-Stream und stellt ihn als
  **WebRTC** (Fallback MSE/MJPEG) bereit. API/Player auf Port `1984`, WebRTC auf `8555`.
- **Wichtig – H.265-Transkodierung:** Browser-WebRTC kann **kein H.265** dekodieren.
  Darum bietet go2rtc den Stream zusätzlich via `ffmpeg:<stream>#video=h264`
  (ffmpeg ist im Image enthalten) als H.264 an. Ohne diese Zeile bleibt der Player im
  Browser auf „Loading" stehen, obwohl der Snapshot (`/api/frame.jpeg`) funktioniert.
- Config (mit Kamera-IP/RTSP-URL) liegt **gitignored** in
  `deploy/go2rtc/go2rtc.yaml` (Vorlage: `deploy/go2rtc/go2rtc.example.yaml`).
- Im Deployment (`deploy/docker-compose.yml`) ist go2rtc ein eigener Service im
  Host-Netz, auf demselben Host wie die App.

Verifiziert: go2rtc lieferte ein Live-Frame (JPEG 1920×1080) von der realen Kamera.

## 3. Architektur (Hexagonal)

- `domain/model/camera/Camera` – record `(id, name, room, stream)`; Invarianten
  (id/name/stream nicht leer) im Compact-Constructor. `stream` = go2rtc-Stream-Name.
- `domain/port/in/camera/ViewCameras` – Use-Case-Interface (`List<Camera> list()`).
- `application/service/camera/CameraViewService` – mappt Config → Domäne.
- `adapter/out/camera/CameraConfig` – `@ConfigMapping(prefix="camera")`,
  Liste `camera.devices[i]` mit `id/name/room/stream`.
- `adapter/in/rest/camera/CameraResource` – `GET /api/cameras` → `List<CameraDto>`.

## 4. Sicherheit / Datenschutz

- **Die API liefert NIE die RTSP-URL oder die Kamera-IP** – nur Name, Raum und den
  go2rtc-Stream-Namen. Die IP bleibt ausschliesslich in der gitignored go2rtc-Config.
- Das Repo ist öffentlich: keine IPs/URLs/Tokens einchecken.
- **Zugriff über dieselbe Origin (remote-tauglich):** Das Backend proxyt `/go2rtc/*`
  an den go2rtc-Dienst (`localhost:1984`, siehe `adapter/in/gateway/Go2rtcProxy`) –
  **HTTP UND WebSocket** (`/go2rtc/api/ws`). Das Frontend bettet go2rtcs **MSE-Player**
  (`/go2rtc/stream.html?src=<stream>&mode=mse`) per iframe ein. MSE ist adaptiv und
  reconnectet, daher über instabile Remote-Verbindungen (Fly-Tunnel/Mobilfunk) stabil –
  ein roher progressiver `stream.mp4`-Download bricht dort ab (im LAN lief er). So läuft
  der Stream über Port 8080 und ist auch **remote** erreichbar (Fly-Login-Proxy +
  WireGuard, wo nur 8080 durchgeht); ein direkter Zugriff auf `:1984` wäre von aussen
  schwarz. go2rtc transkodiert H.265→H.264 bei Bedarf via ffmpeg.

## 5. Frontend

- `core/models/camera.ts`, `core/services/camera.service.ts` (einmaliger Abruf,
  Metadaten sind statisch).
- `features/camera/camera-page.ts` – je Kamera eine Kachel mit eingebettetem
  go2rtc-MSE-Player (`<iframe src="/go2rtc/stream.html?src=<stream>&mode=mse">`, relativ
  über den Backend-Proxy).
- Route `/cameras` + Nav-Eintrag (Kamera-Icon).

## 6. Konfiguration

`application.properties` (Stream-Name aus Env überschreibbar):

```
camera.devices[0].id=garten
camera.devices[0].name=Garten
camera.devices[0].room=Garten
camera.devices[0].stream=${CAMERA_STREAM_GARTEN:garten}
```

`deploy/go2rtc/go2rtc.yaml` (gitignored): mappt den Stream-Namen `garten` auf die
echte `rtsp://...`-URL.
