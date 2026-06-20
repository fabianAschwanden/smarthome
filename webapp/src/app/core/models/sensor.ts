/** Spiegelt die REST-DTOs des Backends (publizierte Sprache). */
export interface Sensor {
  id: string;
  name: string;
  room: string;
  /** °C; sehr kleiner Wert (<-100) = unbekannt. */
  temperature: number;
  /** %; -1 = unbekannt. */
  humidity: number;
  online: boolean;
  observedAt: string;
}
