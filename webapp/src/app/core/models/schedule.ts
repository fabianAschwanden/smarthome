/** Spiegelt die REST-DTOs des Backends (publizierte Sprache). */
export type ScheduleType = 'SCHEDULE' | 'COUNTDOWN' | 'RANDOM' | 'INCHING';
export type SwitchState = 'ON' | 'OFF';
export type Weekday =
  | 'MONDAY'
  | 'TUESDAY'
  | 'WEDNESDAY'
  | 'THURSDAY'
  | 'FRIDAY'
  | 'SATURDAY'
  | 'SUNDAY';

export interface Schedule {
  id: string;
  switchId: string;
  type: ScheduleType;
  action: SwitchState;
  enabled: boolean;
  time: string | null;
  weekdays: Weekday[];
  fireAt: string | null;
  windowStart: string | null;
  windowEnd: string | null;
  pulseSeconds: number | null;
}

/** Anlage-Anforderung; je Typ sind unterschiedliche Felder relevant. */
export interface CreateScheduleRequest {
  switchId: string;
  type: ScheduleType;
  action?: SwitchState;
  enabled?: boolean;
  time?: string;
  weekdays?: Weekday[];
  countdownSeconds?: number;
  windowStart?: string;
  windowEnd?: string;
  pulseSeconds?: number;
}
