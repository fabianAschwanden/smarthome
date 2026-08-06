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
  temperature: number;
  humidity: number;
}

export interface SmokeDto extends DeviceBase {
  alarm: string;
  /** -1 bedeutet "unbekannt", nicht "leer". */
  battery: number;
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
