/** Spiegelt die REST-DTOs des Backends (publizierte Sprache). */
export type ClimateMode = 'COOL' | 'HEAT' | 'AUTO' | 'FAN';

export interface Climate {
  id: string;
  name: string;
  room: string;
  power: boolean;
  mode: ClimateMode;
  targetTemp: number;
  /** -1 = unbekannt. */
  currentTemp: number;
  online: boolean;
  observedAt: string;
}
