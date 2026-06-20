# Spec – Use Case 10: Wettervorhersage

Status: v1.0 (umgesetzt) · Datum: 2026-06-20 · Plattform: Java 25 / Quarkus (Hexagonal + DDD)

## 1. Zweck & Scope

Aktuelle Wetterlage + Tages-Hoch/Tief + Stundenverlauf für den konfigurierten
Standort auf dem Dashboard anzeigen (Kachel `app-weather-card`).

## 2. Anbindung

Quelle **Open-Meteo** (`api.open-meteo.com/v1/forecast`, **kein API-Key**). Gelesen
werden `current` (Temp, weather_code), `daily` (max/min) und `hourly`. Der
WMO-`weather_code` wird auf `WeatherCondition` normalisiert; der Stundenverlauf zeigt
die nächsten ~6 h ab der laufenden Stunde.

- `%dev`/`%test`: `MockWeatherGateway` (feste Werte, kein externer Call).
- Echtbetrieb (`smarthome.real-devices=true`): `OpenMeteoWeatherGateway`.

Der `WeatherService` cacht die Antwort 10 min (das UI pollt häufiger); bei einem
Ausfall wird der letzte bekannte Stand weitergegeben.

## 3. API (REST)

| Methode | Pfad           | Antwort                                                       |
|---------|----------------|--------------------------------------------------------------|
| GET     | `/api/weather` | Vorhersage (location, currentTemp, condition, dayMax/Min, hours[]) bzw. **204** wenn keine Daten |

## 4. Konfiguration (Standort, ortsbezogen → nur in config/)

```properties
weather.location=<Ortsname>
weather.latitude=<lat>
weather.longitude=<lon>
```

Der eingecheckte Default ist neutral (kein echter Wohnort); der konkrete Standort
steht nur in der gitignored `config/application.properties`.

## 5. Architektur-Einordnung (Hexagonal)

Slice `weather`: Domäne `WeatherForecast` (+ `HourEntry`), `WeatherCondition`;
Port `CurrentWeather` (in), `WeatherGateway` (out); `WeatherService` (application);
Adapter `adapter/in/rest/weather` + `adapter/out/weather/{mock,openmeteo}`.

## 6. Offene Punkte / TODO

- [ ] Optional: Wetterwarnungen / 10-Tage-Vorhersage (Open-Meteo liefert mehr Felder).
