/** Spiegelt die REST-DTOs der App (publizierte Sprache), nicht deren Domaenenmodell. */

/** Gemeinsame Felder aller Geraete-DTOs. */
export interface DeviceBase {
  id: string;
  name: string;
  room: string;
  online: boolean;
  observedAt: string;
}

export interface SwitchDto extends DeviceBase {
  state: 'ON' | 'OFF';
  /** Kritische Schalter verlangen im Dashboard eine Bestaetigung beim Ausschalten. */
  critical: boolean;
  hint: string;
}

export interface CoverDto extends DeviceBase {
  /** Domaenen-Semantik: 100 = ZU. HomeKit rechnet umgekehrt (siehe cover.ts). */
  position: number;
}

export interface ClimateDto extends DeviceBase {
  power: boolean;
  mode: string;
  currentTemp: number;
  targetTemp: number;
  outdoorTemp: number | null;
  boost: boolean;
}

export interface SensorDto extends DeviceBase {
  /** Werte unter {@link TEMPERATURE_UNKNOWN_BELOW} bedeuten "unbekannt" (Domaene: -1000). */
  temperature: number;
  /** {@link HUMIDITY_UNKNOWN} bedeutet "unbekannt", nicht "0 %". */
  humidity: number;
}

/** Die Domaene meldet -1000 fuer eine fehlende Temperatur - kein Messwert, ein Platzhalter. */
export const TEMPERATURE_UNKNOWN_BELOW = -100;

/** Die Domaene meldet -1 fuer eine fehlende Feuchte. */
export const HUMIDITY_UNKNOWN = -1;

export function hasTemperature(sensor: SensorDto): boolean {
  return sensor.temperature > TEMPERATURE_UNKNOWN_BELOW;
}

export function hasHumidity(sensor: SensorDto): boolean {
  return sensor.humidity >= 0;
}

export interface SmokeDto extends DeviceBase {
  /** "OK" oder "ALARM" (Domaenen-Enum AlarmState). */
  alarm: string;
  /** {@link BATTERY_UNKNOWN} bedeutet "unbekannt", nicht "leer". */
  battery: number;
}

export const BATTERY_UNKNOWN = -1;

/** Ab hier meldet HomeKit "Batterie schwach" - die uebliche Schwelle fuer Melder. */
export const LOW_BATTERY_PERCENT = 20;

export function hasBattery(smoke: SmokeDto): boolean {
  return smoke.battery >= 0;
}

/** Ein Abzug aller Geraete eines Poll-Zyklus. */
export interface Snapshot {
  switches: SwitchDto[];
  covers: CoverDto[];
  climate: ClimateDto[];
  sensors: SensorDto[];
  smoke: SmokeDto[];
}

export const EMPTY_SNAPSHOT: Snapshot = {
  switches: [],
  covers: [],
  climate: [],
  sensors: [],
  smoke: [],
};
