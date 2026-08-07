# Folgestufen – Use Case 16 (HomeKit-Bridge): Konzept & Umsetzungsgrundlage

Status: v0.1 (Entwurf) · Datum: 2026-08-06 · Basis: UC 16 live (SPEC.md/PLAN.md)

Drei Ausbaustufen mit sehr unterschiedlichem Charakter: **F1 ist ein normaler
Ausbau** (gleiche Muster wie v1), **F2 ist ein Make-or-Buy-Entscheid mit Spike**,
**F3 ist bewusstes Abwarten**. Empfohlene Reihenfolge: F1 sofort machbar,
F2 als Spike bei Gelegenheit, F3 nur beobachten.

---

## F1 · Wellness in HomeKit — nur Licht & Wassertemperatur

**Ziel:** «Hey Siri, heiz den Whirlpool auf 38 Grad» und «Whirlpool-Licht an» —
bewusst **nur diese zwei Funktionen** (Entscheid Aug 2026). Pumpe, Massage und
Filterung bleiben Dashboard-exklusiv: Sie sind betriebsnah, und jede weitere
Funktion multipliziert das Latenzproblem (unten) ohne echten Siri-Mehrwert.

**Randbedingung — die Anlage steuert praktisch asynchron:** Die Gecko
in.touch2-Strecke (Adapter → Sidecar → Anlage) hat **grosse Latenz**; ein
Befehl ist erst nach mehreren Sekunden bis Minuten im rückgelesenen Zustand
sichtbar. HomeKit erwartet dagegen Antworten im Sub-Sekunden-Bereich — ein
blockierender Durchgriff würde die Home-App einfrieren und die ganze Bridge
(HAP bedient Characteristics seriell) mitreissen. Deshalb:

**Asynchrones Schreibmuster («optimistisch bestätigen, abgleichen, zurückfallen»):**

1. **Sofort bestätigen:** Der Set-Callback übernimmt den Zielwert lokal und
   antwortet HomeKit unmittelbar — kein Port-Call im HAP-Thread.
2. **Asynchron ausführen:** Der Befehl geht über eine kleine Befehlsablage
   (`PendingCommand` je Accessory, letzter gewinnt) auf einem Worker an
   `ControlAppliances`. **Debounce 2 s** — beim Ziehen am Temperatur-Rad
   erreicht nur der letzte Wert die Anlage.
3. **Abgleichen:** Der RefreshLoop (für Wellness eigener, gemächlicher Takt
   `homekit.wellness-refresh-interval`, Default 30 s) liest den Ist-Zustand.
   Stimmt er mit dem Ziel überein → `PendingCommand` abhaken.
4. **Zurückfallen:** Bestätigt die Anlage den Zielwert nicht innerhalb
   `homekit.wellness-confirm-timeout` (Default 3 min), wird die Characteristic
   auf den tatsächlichen Gerätezustand zurückgesetzt (Event an HomeKit) und
   eine Meldung in die Nachrichtenzentrale gestellt — die Home-App zeigt nie
   dauerhaft einen Zustand, den die Anlage nicht hat.

Das Muster passt natürlich zu den zwei gewählten Profilen:

| Funktion | HomeKit-Profil | Latenz-Verhalten |
|---|---|---|
| Wassertemperatur (Ist/Soll) | **Thermostat** (nur Heizen) | Von Natur aus asynchron: `TargetTemperature` = sofort bestätigtes Ziel, `CurrentTemperature` = träges Ist — genau die Semantik der Anlage. |
| Licht | **Switch** (bzw. Lightbulb ohne Dimmen) | Optimistisch EIN/AUS, Reconcile über Schritt 3; Rückfall nach Timeout sichtbar. |

- **Leit-Anwendungsfall (Abnahmekriterium):** «Hey Siri, heize den Whirlpool
  auf 33 Grad» ⇒ Soll = 33.0 °C, Modus = Heizen, Befehl läuft asynchron an die
  Anlage. Dafür nötig: Accessory heisst exakt **«Whirlpool»** (bzw. «Becken»),
  und der TargetHeatingCoolingState akzeptiert HEAT.
- Temperaturbereich: HomeKit deckelt die Thermostat-Soll-Temperatur per Spec
  standardmässig bei **38 °C** (Min 10 °C). Untergrenze auf den Anlagenbereich
  anheben (z. B. 20 °C), 0.5-°C-Schritte; ob HAP-Java ein Anheben des Max über
  38 °C erlaubt, im Spike prüfen — sonst gilt 38 °C als HomeKit-Grenze
  (Dashboard kann weiterhin mehr). `pending`-Anlage ⇒ «nicht erreichbar»
  (Muster v1).
- Je Anlage (Whirlpool, Becken) ein Accessory-Paar, gemeinsam benannt.
- `PendingCommand`/Debounce/Timeout als **pure, einzeln getestete Hilfsklasse**
  im Adapter (injizierbare `Clock`) — sie ist das Herzstück dieser Stufe und
  wiederverwendbar, falls später doch weitere träge Funktionen dazukommen.
- **Wechselwirkung mit UC-15-F4** (Überschuss-Heizung): Beide setzen dasselbe
  Soll über denselben Port — die Override-Regel aus den Forecast-Folgestufen
  («manuell gewinnt, Automatik pausiert») greift auch bei HomeKit-Eingriffen.
  Wichtig: Als «manueller Eingriff» zählt das **bestätigte** Soll (Schritt 3),
  nicht der optimistische Zwischenstand.

**Etappen:** (1) `PendingCommand`-Mechanik pur + Tests (Debounce, letzter
gewinnt, Timeout-Rückfall, Clock-gesteuert) · (2) Thermostat-Accessory +
Wellness-RefreshLoop · (3) Licht-Switch + Verifikation an der realen Anlage
(gemessene Bestätigungszeiten → Timeouts kalibrieren).
**Aufwand:** ~2 Abende. **DoD:** «Hey Siri, heize den Whirlpool auf 33 Grad»
setzt das Soll und die Anlage folgt; Licht per Siri schaltbar; die Home-App
friert nie ein; ein nicht bestätigter Befehl fällt sichtbar zurück und meldet
sich in der Nachrichtenzentrale.

## F2 · Kameras in HomeKit (HomeKit-Video)

**Ziel:** Kamera-Livebild in der Home-App (und auf dem Apple TV).

**Ehrliche Einordnung — Make or Buy:** HomeKit-Video verlangt SRTP-Streaming,
Snapshot-API und Codec-Aushandlung. Das in HAP-Java selbst zu bauen ist ein
Grossprojekt mit dünner Beispiel-Lage — und die Streams liegen dank **go2rtc**
bereits konsumierbar vor. Darum fällt hier bewusst die pragmatische Karte:

| Option | Bewertung |
|---|---|
| **A · Eigenbau in HAP-Java** | Maximale Kontrolle, aber hoher Aufwand + Wartungsrisiko am dünnsten Ende der Bibliothek. Nur wenn B im Spike scheitert. |
| **B · Zweite, spezialisierte Bridge (empfohlen)** | **Scrypted** (oder Homebridge + Kamera-Plugin) als eigene k3s-App (`apps/`-Template!), konsumiert die bestehenden go2rtc-/RTSP-Streams und publiziert **nur die Kameras** als eigene HomeKit-Bridge. HomeKit erlaubt beliebig viele Bridges — die eigene UC-16-Bridge bleibt unberührt. |
| **C · Nicht machen** | Kamera bleibt im Dashboard (WebRTC) — auch legitim; Home-App-Video ist Komfort, kein Sicherheitsgewinn. |

**Konzept für B:** Neue App nach `docs/ADD-APP.md` (Deployment + PVC für Config
+ Service; hostNetwork nur falls mDNS es erfordert — im Spike klären), Streams
aus go2rtc (`rtsp://127.0.0.1:8554/<stream>` bzw. RTSP-Quelle direkt),
**kein HomeKit Secure Video** (bräuchte iCloud+ und Cloud-Upload — Widerspruch
zum Lokal-Prinzip; Livebild + Aufzeichnung lokal via Frigate wäre das spätere
P9-Thema).

**Etappen:** (1) **Spike** (1 Abend, Wegwerf-Deployment): Scrypted als k3s-App,
eine Kamera anbinden, Latenz/Stabilität in der Home-App bewerten ·
(2) bei Erfolg: saubere App (`apps/scrypted/`), Config versioniert, Doku ·
(3) Apple-TV-Test, Entscheid A/B/C dokumentieren.
**Aufwand:** Spike 1 Abend; Ausbau ~2 Abende. **DoD:** Livebild < 3 s Latenz in
der Home-App oder dokumentierter Entscheid dagegen.

## F3 · Matter — beobachten, nicht bauen

**Ziel (später):** Geräte auch für Nicht-Apple-Ökosysteme (Google, Alexa, …)
sichtbar machen; Matter ist zudem Apples strategische Richtung.

**Stand:** Es gibt kein reifes Java-SDK für einen Matter-Bridge-Server —
Eigenbau hiesse C++-SDK wrappen (unverhältnismässig). Da HomeKit (UC 16) den
Apple-Bedarf vollständig deckt, ist der Nutzen heute null.

**Konzept = Beobachtungskriterien** (jährlich oder bei Anlass prüfen):

- Ein brauchbares Matter-**Java**-SDK oder eine stabile Bridge-Lösung erscheint
  (z. B. matter.js-Server als Sidecar — dann wäre das Sidecar-Muster aus
  `tools/tuya-sidecar` der natürliche Weg: Java-Adapter → matter.js-Prozess).
- Konkreter Bedarf: ein Nicht-Apple-Gerät/Ökosystem zieht ein.
- Apple deprecatet HAP für Bridges (derzeit nicht absehbar).

**Aufwand jetzt:** 0. Kein Code, keine Abhängigkeit — nur dieser Abschnitt als
festgehaltener Entscheid.

---

## Empfohlene Gesamt-Reihenfolge (UC 15 + UC 16 Folgestufen)

| # | Stufe | Warum jetzt |
|---|---|---|
| 1 | UC 15 · F1 Genauigkeit | Misst die Prognose — Grundlage für alles Automatische; klein |
| 2 | UC 15 · F3 Storen-Hitzeschutz | Grösster Alltagsnutzen, im Sommer sofort erlebbar |
| 3 | UC 16 · F1 Wellness in HomeKit | Klein, rundet die Home-App ab; legt die Override-Basis mit |
| 4 | UC 15 · F2 Auto-Apply | Automatik mit F1 als Schutzschalter |
| 5 | UC 15 · F4 Wellness-Überschuss | Nutzt Override + Fenster-Mechanik der Vorstufen |
| 6 | UC 16 · F2 Kamera-Spike | Ein Abend, danach fundierter Make-or-Buy-Entscheid |
| — | UC 16 · F3 Matter | Nur beobachten |

Die Stufen 1–5 sind reine Java-/App-Arbeit im bestehenden Repo; Stufe 6 ist ein
Infra-Spike. Zusammen ergeben sie den Schritt vom «steuerbaren» zum wirklich
**selbststeuernden** Haus — mit dem Nutzer immer als letzter Instanz.
