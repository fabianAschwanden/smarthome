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
  /**
   * Geraeteskala: 0 = ZU, 100 = OFFEN - dieselbe Richtung wie HomeKit, es wird NICHT
   * invertiert. Das Dashboard zeigt "% zu" und spiegelt dafuer selbst; diese Spiegelung
   * gehoert der Oberflaeche, nicht der API. {@link POSITION_UNKNOWN} = unbekannt.
   */
  position: number;
}

/** Die Domaene meldet -1, wenn die Store keine Position liefert. */
export const POSITION_UNKNOWN = -1;

export function hasPosition(cover: CoverDto): boolean {
  return cover.position >= 0;
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
