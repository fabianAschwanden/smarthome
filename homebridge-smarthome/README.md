# homebridge-smarthome

Homebridge-Plugin, das die Geräte dieser App in HomeKit bringt (Use Case 16, siehe
[`../docs/homekit/`](../docs/homekit/)).

Das Plugin ist ein **eigenes Deployable**: Es läuft in Homebridge, nicht in der App, und
spricht ausschliesslich deren REST-API. Die App weiss nichts von HomeKit – fällt die
Bridge aus, merkt sie es nicht.

## Aufbau

| Datei | Rolle |
|---|---|
| `src/types.ts` | Spiegelt die REST-DTOs (publizierte Sprache), nicht das Domänenmodell |
| `src/api-client.ts` | Der einzige Ort, der HTTP kennt |
| `src/platform.ts` | Poll-Zyklus, Anlegen/Entfernen von Accessories |
| `src/accessories/` | Je Geräteklasse ein Handler (DTO ↔ HomeKit-Characteristics) |

## Konfiguration

```json
{
  "platform": "Smarthome",
  "baseUrl": "http://127.0.0.1:8080",
  "pollIntervalSeconds": 10
}
```

`allowCriticalOff` (Standard `false`) erlaubt HomeKit, als **kritisch** markierte Schalter
auszuschalten. Standardmässig scheitert das sichtbar: HomeKit kann keine Rückfrage
anzeigen, und eine Fehlerkennung von Siri oder eine Automation schaltet ganz ohne
Menschen – genau der Unfall, gegen den die Markierung gesetzt wurde. Einschalten geht
immer.

## Entwickeln

```bash
npm install
npm run lint    # tsc --noEmit
npm test        # Vitest
npm run build   # nach dist/
```

Die Tests decken die Abbildungslogik ab und arbeiten gegen eine HAP-Attrappe
(`test/harness.ts`) – Homebridge wird dafür nicht gestartet.

## Geräteklassen (Stand Etappe 3)

| Klasse | HomeKit | Besonderheit |
|---|---|---|
| Schalter | `Switch` | Kritische Schalter siehe `allowCriticalOff` |
| Sensor | `TemperatureSensor` (+ `HumiditySensor`) | Feuchte nur, wenn der Sensor eine meldet |
| Rauchmelder | `SmokeSensor` (+ `StatusLowBattery`) | Batteriestatus nur bei bekanntem Wert |

Der Rauchmelder ist die dokumentierte **Ausnahme** von der Regel «offline → nicht
erreichbar»: Batteriemelder funken sporadisch, ein dauerhaftes «keine Antwort» würde die
Kachel entwerten. Er meldet deshalb den zuletzt bekannten Alarmzustand weiter.

## Grenzen

- **Storen und Klima** liest der Poll-Zyklus bereits mit, sie bekommen ihre Handler in
  den Etappen 4 und 5.
- Ausgeliefert wird das Plugin noch nicht: Das Container-Image mit dem Plugin entsteht in
  Etappe 6. Bis dahin läuft in der Bridge das generische HTTP-Plugin aus Etappe 1.
- Zustände kommen per Poll, nicht per Push – eine Änderung am Gerät erscheint mit bis zu
  einem Poll-Intervall Verzögerung in der Home-App (Folgestufe F1).
