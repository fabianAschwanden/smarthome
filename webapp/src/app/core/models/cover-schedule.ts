/** Spiegelt die REST-DTOs der Storen-Zeitsteuerung (publizierte Sprache). */
export type CoverScheduleType = 'SCHEDULE' | 'COUNTDOWN';
export type Weekday =
  | 'MONDAY'
  | 'TUESDAY'
  | 'WEDNESDAY'
  | 'THURSDAY'
  | 'FRIDAY'
  | 'SATURDAY'
  | 'SUNDAY';

export interface CoverSchedule {
  id: string;
  coverId: string;
  type: CoverScheduleType;
  /** Zielposition 0 = zu, 100 = offen (Geräte-Skala). */
  position: number;
  enabled: boolean;
  time: string | null;
  weekdays: Weekday[];
  fireAt: string | null;
}

/** Anlage-Anforderung; je Typ sind unterschiedliche Felder relevant. */
export interface CreateCoverScheduleRequest {
  coverId: string;
  type: CoverScheduleType;
  position: number;
  enabled?: boolean;
  time?: string;
  weekdays?: Weekday[];
  countdownSeconds?: number;
}
