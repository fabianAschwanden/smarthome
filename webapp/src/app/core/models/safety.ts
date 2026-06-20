/** Spiegelt die REST-DTOs des Backends (publizierte Sprache). */
export type AlarmState = 'OK' | 'ALARM';

export interface SmokeDetector {
  id: string;
  name: string;
  room: string;
  alarm: AlarmState;
  /** %, -1 = unbekannt. */
  battery: number;
  online: boolean;
  observedAt: string;
}
