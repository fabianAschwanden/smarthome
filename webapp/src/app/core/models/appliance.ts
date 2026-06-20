/** Spiegelt die REST-DTOs des Backends (publizierte Sprache). */
export type ApplianceFunction = 'PUMP' | 'HEATER' | 'LIGHT' | 'MASSAGE';
export type FunctionState = 'ON' | 'OFF';

/** Temperatur-Steuerung einer beheizten Anlage (Whirlpool/Schwimmbecken). */
export interface ApplianceTemperature {
  /** Soll-Temperatur in °C. */
  target: number;
  /** Ist-Temperatur in °C; -1 = unbekannt. */
  current: number;
  min: number;
  max: number;
}

export interface Appliance {
  id: string;
  name: string;
  room: string;
  online: boolean;
  observedAt: string;
  /** Nur vorhandene Funktionen, je mit Zustand "ON"/"OFF". */
  functions: Record<string, FunctionState>;
  /** Vorhanden nur bei beheizten Anlagen; sonst null/undefined. */
  temperature?: ApplianceTemperature | null;
}
