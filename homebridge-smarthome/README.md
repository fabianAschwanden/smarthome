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

## Geräteklassen

| Klasse | HomeKit | Besonderheit |
|---|---|---|
| Schalter | `Switch` | Kritische Schalter siehe `allowCriticalOff` |
| Sensor | `TemperatureSensor` (+ `HumiditySensor`) | Feuchte nur, wenn der Sensor eine meldet |
| Rauchmelder | `SmokeSensor` (+ `StatusLowBattery`) | Batteriestatus nur bei bekanntem Wert |
| Store | `WindowCovering` | **Keine Invertierung** – REST und HomeKit zählen beide 0 = zu |
| Klima | `HeaterCooler` | `FAN` wird als `AUTO` gezeigt und nie gesetzt; Boost bleibt im Dashboard |
| Wellness | `Thermostat` + `Lightbulb`/`Switch` je Funktion | Ein Accessory pro Anlage; welche Funktionen es gibt, sagt das Gerät |

Der Rauchmelder ist die dokumentierte **Ausnahme** von der Regel «offline → nicht
erreichbar»: Batteriemelder funken sporadisch, ein dauerhaftes «keine Antwort» würde die
Kachel entwerten. Er meldet deshalb den zuletzt bekannten Alarmzustand weiter.

## Grenzen

- Der Wellness-**Thermostat** kennt nur AUS und HEIZEN. Kühlen kann keine der Anlagen,
  und ein Modus, den das Gerät nicht hat, wäre ein Versprechen, das beim Antippen bricht.
- **Boost** (Turbo der Klimaanlage) und der Modus **FAN** haben in HomeKit kein
  Gegenstück und bleiben dem Dashboard vorbehalten. FAN wird als `AUTO` angezeigt –
  «aus» wäre falscher –, aber aus HomeKit nie gesetzt.
- Die **Aussentemperatur** der Klimaanlage wird nicht exponiert; dafür gibt es den
  eigenen Aussensensor.
- Ausgeliefert wird das Plugin noch nicht: Das Container-Image mit dem Plugin entsteht in
  Etappe 6. Bis dahin läuft in der Bridge das generische HTTP-Plugin aus Etappe 1.
- Zustände kommen per Poll, nicht per Push – eine Änderung am Gerät erscheint mit bis zu
  einem Poll-Intervall Verzögerung in der Home-App (Folgestufe F1).
