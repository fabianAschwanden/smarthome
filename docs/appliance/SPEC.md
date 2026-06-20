# Spec – Use Case 6: Wellness-Anlagen (Whirlpool & Schwimmbecken)

Status: v1.3 (Gecko in.touch2 angebunden) · Datum: 2026-06-20 · Plattform: Java 25 / Quarkus (Hexagonal + DDD)

## 1. Zweck & Scope

Wellness-Anlagen mit **mehreren schaltbaren Funktionen** steuern – konkret
Whirlpool und Schwimmbecken. Jede Anlage hat eine Teilmenge der Funktionen
**Pumpe/Filter, Heizung, Licht, Massage/Jets**, jeweils einzeln EIN/AUS; beheizte
Anlagen zusätzlich mit **Soll-/Ist-Temperatur**.

In Scope: je Funktion EIN/AUS + Zustandsanzeige, Temperatur-Steuerung beheizter
Anlagen (Front- und Backend umgesetzt – siehe §7), Mock-Modus.
Out of Scope (später): Zeitsteuerung der Anlagen, Szenen, Filter-Laufzeitpläne.

## 1a. Anlagen im Überblick

| Anlage            | id           | Raum     | Funktionen                       | Heizung/Temp |
|-------------------|--------------|----------|----------------------------------|--------------|
| **Whirlpool**     | `whirlpool`  | Wellness | Pumpe · Heizung · Licht · Massage | ja (Soll-Temp) |
| **Schwimmbecken** | `pool`       | Garten   | Pumpe · Heizung · Licht          | ja (Soll-Temp) |

- **Whirlpool:** Massage/Jets zusätzlich zur Filterpumpe; Heizung hält die
  Wassertemperatur (typ. Sollbereich 30–40 °C).
- **Schwimmbecken:** keine Massage; Filterpumpe + Heizung (typ. Sollbereich
  18–32 °C); Licht für die Beckenbeleuchtung.

Konkrete Min/Max-Temperaturen je Anlage sind konfigurierbar (§4) und gegen die
reale Steuerung zu verifizieren.

## 2. Anbindung: Gecko in.touch2 (lokal)

Die Anlagen werden über die **Gecko in.touch2**-Steuerung (WLAN) angebunden – lokal
im LAN über die Python-Lib **geckolib**, gekapselt im **Sidecar**
(`tools/tuya-sidecar`, Endpunkte `/spa/read` und `/spa/control`), den der
Java-Adapter per HTTP aufruft (gleiches Muster wie Midea-Klima). Discovery findet
die Spas per Broadcast; der `gecko-ident` aus der Config wählt das richtige Gerät.

- `%dev`/`%test`: **Mock** (in-memory, alle Funktionen schaltbar).
- Echtbetrieb (`smarthome.real-devices=true`): **LocalGeckoApplianceDevice**
  (`adapter/out/appliance/local`), wenn `address` + `gecko-ident` gesetzt sind;
  sonst **PendingApplianceDevice** (offline, 503).

**Funktions-Mapping** (Config, gerätespezifisch): PUMP→`pump-key`,
MASSAGE→`massage-key`, LIGHT→`light-key`, HEATER über die Wasser-Soll-Temperatur.

> Gecko-Verbindungen sind langsam (Discovery + Aufbau ~30–60 s). Der Adapter cacht
> den Zustand (TTL 30 s) und liest das Spa im Hintergrund nach – `readState()`
> blockiert nie die REST-Antwort. Steuerbefehle übernehmen ihre Antwort als Cache.
>
> Voraussetzung am Gerät: die **RF-Strecke** des in.touch2 (Funk zum Spa-Pack) muss
> aktiv sein – sonst meldet geckolib `RF_ERROR` und das Spa bleibt offline.

## 3. API (REST)

| Methode | Pfad                                        | Body / Antwort                         |
|---------|---------------------------------------------|----------------------------------------|
| GET     | `/api/appliances`                           | Liste (id, name, room, online, functions, temperature) |
| POST    | `/api/appliances/{id}/functions/{function}` | `{ "state": "ON"\|"OFF" }`             |
| POST    | `/api/appliances/{id}/temperature`          | `{ "target": <°C> }` (nur beheizte Anlagen) |

`{function}` ∈ `PUMP`/`HEATER`/`LIGHT`/`MASSAGE`. 404 bei unbekannter Anlage,
400 wenn die Anlage die Funktion nicht hat (bzw. keine Heizung für `/temperature`
oder Soll-Temp ausserhalb min/max), 503 wenn nicht erreichbar.

`temperature` im DTO ist `null` bei Anlagen ohne Heizung, sonst:

```json
{ "target": 36, "current": 35, "min": 30, "max": 40 }
```

(`current = -1` = Ist-Temperatur unbekannt.)

## 4. Konfiguration

```properties
appliance.devices[0].id=whirlpool
appliance.devices[0].name=Whirlpool
appliance.devices[0].room=Wellness
appliance.devices[0].functions=PUMP,HEATER,LIGHT,MASSAGE
appliance.devices[0].temp-min=30
appliance.devices[0].temp-max=40
appliance.devices[1].id=pool
appliance.devices[1].name=Schwimmbecken
appliance.devices[1].room=Garten
appliance.devices[1].functions=PUMP,HEATER,LIGHT
appliance.devices[1].temp-min=18
appliance.devices[1].temp-max=32
# temp-min/temp-max nur bei beheizten Anlagen; fehlt HEATER, gibt es keine Temperatur-Steuerung.
# address/secret: vom späteren echten Adapter genutzt (secret nur per config/Env).
```

Mock vs. echt über `smarthome.real-devices` (wie die anderen Slices).

## 5. Architektur-Einordnung (Hexagonal)

Eigener Slice `appliance`: Treiber-Port `ControlAppliances`
(`domain/port/in/appliance`), getriebener Port `ApplianceDevice` +
`ApplianceDeviceFactory` (`domain/port/out/appliance`), Application-Service
`ApplianceControlService` (`application/service/appliance`), Adapter
`adapter/in/rest/appliance` und `adapter/out/appliance/{mock,local,pending}`
(`local` = Gecko über den geteilten `support.tuya.TuyaSidecarClient`).

## 7. Temperatur-Steuerung (beheizte Anlagen) – umgesetzt

Whirlpool und Schwimmbecken haben eine Heizung mit Soll-Temperatur. Front- und
Backend sind umgesetzt:

- Domäne: Value Object `Temperature` (target/current/min/max, Invarianten im
  Compact-Constructor) als Feld im `Appliance`-Aggregat (`null` ohne Heizung);
  Use-Case `ControlAppliances.setTargetTemperature(id, °C)`.
- REST: `POST /api/appliances/{id}/temperature` mit `{ "target": <°C> }`.
- Regeln: nur Anlagen mit `HEATER` (sonst `TemperatureNotSupported` → 400);
  Soll-Temp muss in `[temp-min, temp-max]` liegen (sonst 400).
- Frontend: `ApplianceTemperature` + `setTargetTemp(id, °C)`, Ring-Dial in der
  Wellness-Kachel.
- Mock hält die Soll-Temp und simuliert eine Ist-Temperatur; der echte Adapter
  schreibt später den passenden Geräte-Datenpunkt.

## 8. Offene Punkte / TODO

- [x] Temperatur-Steuerung im Backend (Domäne + Port + REST) – umgesetzt (§7).
- [x] Echter Adapter (Gecko in.touch2 via Sidecar/geckolib) – `LocalGeckoApplianceDevice`.
- [x] Funktions-Mapping (P1/P2/LI) am echten Spa verifiziert (Live-Test 2026-06-20).
- [ ] Min/Max-Temperaturen je Anlage final festlegen (Gerät meldet Soll teils ausserhalb).
