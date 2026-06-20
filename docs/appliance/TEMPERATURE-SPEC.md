# Spec – Wellness/Pool: einstellbare Temperatur

Status: Entwurf v1.0 · Use Case 6 (Wellness-Anlagen)

Whirlpool und Schwimmbecken sind beheizt und sollen eine **einstellbare Soll-Temperatur**
bekommen (Ziel/Ist/Min/Max). Das Frontend ist bereits umgesetzt; hier die noch offenen
**Java-Änderungen** end-to-end, passend zu den bestehenden Patterns.

## 1. Contract (Frontend ↔ Backend)

`GET /api/appliances` und alle POSTs liefern je Anlage zusätzlich ein optionales Feld:

```json
{
  "id": "whirlpool", "name": "Whirlpool", "room": "Wellness",
  "online": true, "observedAt": "…", "functions": { "HEATER": "ON", … },
  "temperature": { "target": 37, "current": 36, "min": 20, "max": 40 }
}
```

`temperature` ist `null`, wenn die Anlage nicht beheizt ist. Neuer Endpunkt:

```
POST /api/appliances/{id}/temperature      Body: { "target": 26 }
```

Antwort: die aktualisierte `ApplianceDto`.

## 2. Domain (framework-frei)

Neues Value Object `domain/model/appliance/ApplianceTemperature.java`:

```java
public record ApplianceTemperature(double target, double current, double min, double max) {
    public ApplianceTemperature {
        if (min >= max) throw new IllegalArgumentException("min muss < max sein");
        if (target < min || target > max) throw new IllegalArgumentException("target ausserhalb [min,max]");
        // current == -1 erlaubt (unbekannt)
        if (current != -1 && (current < min - 20 || current > max + 20)) {
            throw new IllegalArgumentException("current unplausibel");
        }
    }
}
```

`Appliance` um eine optionale Temperatur erweitern (Komponente + Compact-Constructor):

```java
public record Appliance(
        String id, String name, String room, boolean online, Instant observedAt,
        Map<ApplianceFunction, FunctionState> functions,
        Optional<ApplianceTemperature> temperature) {
    public Appliance {
        // … bestehende Invarianten …
        if (temperature == null) temperature = Optional.empty();
        functions = Collections.unmodifiableMap(new EnumMap<>(functions));
    }
}
```

> Alle bestehenden `new Appliance(...)`-Aufrufe (v. a. im Application-Service und in Tests)
> um das zusätzliche Argument ergänzen.

## 3. Ports

`domain/port/out/appliance/ApplianceDevice.java` – Capability per Default-Methoden,
damit nicht beheizte Geräte (und der Pending-Adapter) unverändert bleiben:

```java
default Optional<ApplianceTemperature> readTemperature() { return Optional.empty(); }
default void setTargetTemperature(double celsius) {
    throw new UnsupportedOperationException("keine Temperatursteuerung");
}
```

`domain/port/in/appliance/ControlAppliances.java`:

```java
Appliance setTargetTemperature(String id, double target);
```

Neue Ausnahme `domain/port/in/appliance/TemperatureNotSupported.java` (analog zu
`FunctionNotSupported`).

## 4. Application-Service `ApplianceControlService`

- `setTargetTemperature(id, target)`: Gerät suchen (`ApplianceNotFound`), prüfen ob es
  Temperatur kann (`device.readTemperature().isEmpty()` → `TemperatureNotSupported`),
  Ziel auf `[min,max]` clampen, `device.setTargetTemperature(target)` aufrufen, `observe()`.
- `observe(device)` zusätzlich `device.readTemperature()` einlesen und in das
  `Appliance`-Objekt geben (neues Konstruktor-Argument).

## 5. Out-Adapter (Mock + Config)

`adapter/out/appliance/ApplianceConfig.java` – pro Gerät optionale Temperatur:

```java
Optional<Temp> temperature();
interface Temp { double min(); double max(); @WithDefault("-1") double target(); }
```

`adapter/out/appliance/mock/MockApplianceDevice.java` – optionalen Temperatur-Zustand
halten (mutable `target`; `current` simuliert, z. B. nähert sich `target`):

```java
private Double target, currentSim, min, max;   // null = keine Temperatur
@Override public Optional<ApplianceTemperature> readTemperature() {
    return target == null ? Optional.empty()
        : Optional.of(new ApplianceTemperature(target, currentSim, min, max));
}
@Override public void setTargetTemperature(double c) { this.target = Math.max(min, Math.min(max, c)); }
```

Factory reicht die Temperatur aus der Config an den Mock weiter.

`src/main/resources/application.properties` – Temperatur je Anlage:

```properties
appliance.devices[0].temperature.min=20
appliance.devices[0].temperature.max=40
appliance.devices[0].temperature.target=37
appliance.devices[1].temperature.min=10
appliance.devices[1].temperature.max=35
appliance.devices[1].temperature.target=26
```

## 6. In-Adapter (REST)

`adapter/in/rest/dto/appliance/ApplianceDto.java` – verschachteltes DTO + Mapping:

```java
public record TemperatureDto(double target, double current, double min, double max) {}
// im Record: TemperatureDto temperature   (null wenn leer)
temperature = a.temperature().map(t ->
    new TemperatureDto(t.target(), t.current(), t.min(), t.max())).orElse(null);
```

Neuer Request `adapter/in/rest/dto/appliance/TargetTemperatureRequest.java`:

```java
public record TargetTemperatureRequest(@NotNull Double target) {}
```

`ApplianceResource` – Endpunkt:

```java
@POST @Path("{id}/temperature")
public ApplianceDto setTemperature(@PathParam("id") String id, @Valid TargetTemperatureRequest req) {
    return ApplianceDto.from(appliances.setTargetTemperature(id, req.target()));
}
```

Exception-Mapper `TemperatureNotSupportedMapper` (analog `FunctionNotSupportedMapper`,
HTTP 422 oder 400).

## 7. Tests

- `ApplianceControlServiceTest`: Soll-Temperatur setzen → `observe()` enthält die neue
  Temperatur; Setzen bei nicht beheizter Anlage → `TemperatureNotSupported`; Clamping.
- `ApplianceResourceTest`: `POST {id}/temperature` liefert `temperature` im DTO;
  unbekannte ID → 404; nicht unterstützt → 422/400.
- Neuer `ApplianceTemperatureTest`: Invarianten (min<max, target im Bereich).
- Bestehende `new Appliance(...)`-Aufrufe in Tests um das Temperatur-Argument ergänzen.

## 8. Bereiche (Default)

| Anlage         | min | max | target |
|----------------|-----|-----|--------|
| Whirlpool      | 20  | 40  | 37     |
| Schwimmbecken  | 10  | 35  | 26     |

## 9. ArchUnit

`ApplianceTemperature` lebt in `domain/model` (framework-frei, reiner `record`).
`TemperatureDto`/`TargetTemperatureRequest` in `adapter/in/rest/dto`. Keine neuen
Framework-Importe in der Domäne.
