# Spec – Use Case 13: Native Geräte-Weboberflächen

Status: v1.0 (SMARTFOX) · Datum: 2026-06-23 · Plattform: Java 25 / Quarkus (Hexagonal + DDD)

## 1. Zweck & Scope

Fremde Geräte-Weboberflächen (mit eigenem Web-UI) direkt im Dashboard einbetten –
als Kacheln unter dem Nav-Punkt **„Native"**. Erste Oberfläche: die **SMARTFOX**-Web-UI
(`http://192.168.1.124/index.shtml`).

In Scope: konfigurierbare Liste von Ziel-Oberflächen, Einbettung im iframe, Zugriff
auch **remote** (über den Fly-Tunnel). Out of Scope: Single-Sign-on in die Fremd-UIs,
Umschreiben fremder JS-Logik.

## 2. Problem & Lösung: Reverse-Proxy

Ein iframe direkt auf `http://192.168.1.124` funktioniert **nicht** remote: die LAN-IP
ist über den Fly-Tunnel nicht erreichbar, und eine HTTPS-Seite darf kein HTTP-iframe
laden (Mixed Content). Gleiche Situation wie bei der Kamera.

Lösung: ein **Backend-Reverse-Proxy** (`adapter/in/gateway/NativeProxy`, Vert.x) reicht
`/native/<id>/*` an die konfigurierte Geräte-URL weiter. Damit läuft alles über dieselbe
Origin/Port 8080 – LAN wie remote. Zwei Eingriffe machen die iframe-Einbettung robust:

- **Frame-Blocker entfernen:** `X-Frame-Options` und `Content-Security-Policy` werden aus
  der Antwort gestrippt (sonst verweigert der Browser das iframe).
- **`<base href="/native/<id>/">` injizieren** in HTML-Antworten, damit relative Pfade der
  Fremd-UI (CSS/JS/AJAX) korrekt durch den Proxy aufgelöst werden. Assets (CSS/JS/Bilder)
  werden durchgestreamt, HTML wird gepuffert (wegen der Injektion).

### Vertrauensmodell (die Fremd-UI ist Geräte-Firmware)

Die eingebettete UI läuft auf der App-Origin – eine kompromittierte Firmware hätte sonst
Zugriff auf `/api/*` mit der Session. Vier Schranken begrenzen das:

- **Request-Header-Allowlist** (`FORWARDED_REQUEST_HEADERS`): `Cookie`, `Authorization`
  und `X-Forwarded-*` erreichen das Gerät nie – sonst erntet ein loggendes Gerät Sessions.
  Umgekehrt wird `Set-Cookie` aus der Geräteantwort entfernt (keine Cookie-Injektion in
  die App-Domain).
- **Referer-Fallback eng gefasst:** nur `GET`/`HEAD`, nur Endungen aus
  `FALLBACK_EXTENSIONS`, Referer wird als URI geparst (Pfad-Präfix, nicht `indexOf`),
  `..` wird abgelehnt. Sonst wäre er ein offener Proxy: ein gefälschter `Referer` genügte
  für `GET /setswrel.cgi?rel=1&state=1`. **Wenn eine Fremd-UI einen absoluten Pfad mit
  anderer Endung braucht, gehört die Endung hierher** – am direkten Pfad
  `/native/<id>/...` ist weiterhin alles erlaubt.
- **Cross-Site-Sperre:** Requests mit `Sec-Fetch-Site: cross-site` werden mit 403
  abgewiesen (CSRF per präpariertem Link). Nicht via SameSite=strict gelöst – das bricht
  den OIDC-Code-Flow.
- **Eigene CSP + `sandbox` am iframe:** Die CSP begrenzt die *Ziele* der Fremd-UI auf die
  eigene Origin (keine Exfiltration an fremde Hosts); `sandbox` entzieht ihr u. a.
  Top-Level-Navigation und Downloads. `smarthome.nativeview-style-src-extra` lässt gezielt
  ein Hersteller-Stylesheet zu (der SMARTFOX lädt sein einziges CSS von `my.smartfox.at`).

> **Harte Invariante: auf Proxy-Antworten darf KEIN Frame-Blocker liegen.** Weder
> `X-Frame-Options` noch `frame-ancestors` in der CSP — die Fremd-UI wird als iframe
> eingebettet, jeder Frame-Blocker lässt den Browser mit „Dieser Inhalt ist blockiert"
> abbrechen. Der Proxy strippt deshalb den Header des Geräts **und** den, den die App
> global setzt (`stripFrameBlockers`). Das ist schon zweimal schiefgegangen: einmal durch
> den globalen `X-Frame-Options`-Header (M9), einmal durch `frame-ancestors 'self'` in der
> eigenen CSP (H7). `NativeProxyTest` sichert beides ab — wenn ein Test hier rot wird,
> ist die Antwort anzupassen, nicht der Test.

> **Kein `sandbox` am iframe.** Sandboxed Kontexte gelten für Cookies als cross-site: Der
> iframe-Request erreichte den Login-Proxy (Fly/oauth2-proxy) ohne Session-Cookie, wurde
> auf die Google-Loginseite umgeleitet, und die verbietet das Einbetten — wieder „Dieser
> Inhalt ist blockiert", diesmal nur **remote**, im LAN unauffällig. Der Schutz war ohnehin
> gering: `allow-same-origin` ist für die XHR der UI nötig, damit bleibt die Seite
> same-origin und käme weiterhin an `parent.document` und `/api/*`. Wirksam ist die CSP
> auf den Proxy-Antworten.

> **Restrisiko:** `allow-same-origin` bleibt nötig (die UI lädt ihre Werte per XHR), damit
> bleibt die Fremd-UI same-origin und könnte per `parent.document` aufs Dashboard
> zugreifen. Die harte Trennung wäre eine **eigene Origin** (Subdomain/Port) für
> `/native/*` – bewusst offen, siehe `security-review.md` (H7).

## 3. Architektur (Hexagonal)

- `domain/model/nativeview/NativeView` – record `(id, name, icon)`; Invarianten im Compact-Constructor.
- `domain/port/in/nativeview/ViewNativeViews` – Use-Case (`list()`).
- `domain/port/out/nativeview/NativeViewProvider` – liefert die Views (vom Adapter implementiert).
- `application/service/nativeview/NativeViewService` – Use-Case-Service.
- `adapter/out/nativeview/{NativeViewConfig,ConfiguredNativeViewProvider}` – Config-Mapping
  (`nativeview.targets[i]`) → Domäne.
- `adapter/in/rest/nativeview/NativeViewResource` – `GET /api/native` → DTO (id/name/icon +
  Proxy-Pfad, **nie** die Geräte-URL).
- `adapter/in/gateway/NativeProxy` – der Reverse-Proxy. Liest `nativeview.targets[i].{id,url}`
  direkt über die Config (nicht über die @ConfigMapping-Bean des out-Adapters – Adapter
  referenzieren einander nicht, Blueprint §3.4).

> Prefix bewusst `nativeview` statt `native`: SmallRye validiert `@ConfigMapping`-Keys strikt,
> und `native.encoding` (JVM-/Surefire-Property) würde sonst fälschlich als unbekannter Key
> abgelehnt.

## 4. Konfiguration

Eingecheckt (`application.properties`, Platzhalter):
```
nativeview.targets[0].id=smartfox
nativeview.targets[0].name=SMARTFOX
nativeview.targets[0].icon=bolt
nativeview.targets[0].url=${NATIVE_SMARTFOX_URL:http://smartfox.invalid}
nativeview.targets[0].path=/index.shtml
```
Echte Haus-IP nur in der gitignored `config/application.properties` (bzw. Env):
`nativeview.targets[0].url=http://192.168.1.124`. Die URL bleibt serverseitig.

Neue Kachel = ein weiterer `nativeview.targets[i]`-Block.

## 5. Frontend

`features/nativeview/native-page.ts` (Route `/native`, Grid-Icon in der Nav): Kacheln je
View; die gewählte wird im iframe über den Proxy-Pfad geladen. „Im neuen Tab öffnen"-Link.

## 6. Sicherheit

- Geräte-URL/IP verlässt das Backend nie Richtung Frontend (nur der Proxy-Pfad).
- Remote ist der Zugriff durch den Fly-Login-Proxy abgesichert (Google-Login).
- Hinweis: Die Fremd-UI selbst hat keinen eigenen Login – wer am Dashboard ist, sieht sie.
