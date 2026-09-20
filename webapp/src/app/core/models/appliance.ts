/** Spiegelt die REST-DTOs des Backends (publizierte Sprache). */
export type ApplianceFunction = 'PUMP' | 'HEATER' | 'LIGHT' | 'MASSAGE' | 'FILTER';
export type FunctionState = 'ON' | 'OFF';

/** Temperatur-Steuerung einer beheizten Anlage (Whirlpool/Schwimmbecken). */
import { ThermalActivity } from './thermal';

export interface ApplianceTemperature {
  /** Soll-Temperatur in °C. */
  target: number;
  /** Ist-Temperatur in °C; -1 = unbekannt. */
  current: number;
  min: number;
  max: number;
  /**
   * Gewünschte Soll-Temperatur, solange die Anlage sie noch nicht übernommen hat –
   * sonst null. Der Gecko-Befehl wirkt verzögert; ohne diesen Wert zeigte die
   * Oberfläche den alten an, und der nächste Schritt rechnete wieder von dort.
   */
  pending?: number | null;
  /** Was die Heizung gerade tut – kommt vom Gerät, nicht aus einem Temperaturvergleich. */
  activity: ThermalActivity;
}

export interface Appliance {
  id: string;
  name: string;
  room: string;
  online: boolean;
  /**
   * false = bewusst stillgelegt (z. B. über den Winter vom Strom). Kein Fehler,
   * ein gewählter Zustand – die Anlage wird nicht mehr angesprochen.
   */
  active: boolean;
  observedAt: string;
  /** Nur vorhandene Funktionen, je mit Zustand "ON"/"OFF". */
  functions: Record<string, FunctionState>;
  /** Vorhanden nur bei beheizten Anlagen; sonst null/undefined. */
  temperature?: ApplianceTemperature | null;
}
