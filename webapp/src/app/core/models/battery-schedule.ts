/** Spiegelt die REST-DTOs der Batterie-Zeitsteuerung (publizierte Sprache). */
export type BatteryScheduleType = 'SCHEDULE' | 'COUNTDOWN';
export type RelayState = 'ON' | 'OFF';
export type Weekday =
  'MONDAY' | 'TUESDAY' | 'WEDNESDAY' | 'THURSDAY' | 'FRIDAY' | 'SATURDAY' | 'SUNDAY';

export interface BatterySchedule {
  id: string;
  type: BatteryScheduleType;
  action: RelayState;
  enabled: boolean;
  time: string | null;
  weekdays: Weekday[];
  fireAt: string | null;
}

/** Anlage-Anforderung; je Typ sind unterschiedliche Felder relevant. */
export interface CreateBatteryScheduleRequest {
  type: BatteryScheduleType;
  action?: RelayState;
  enabled?: boolean;
  time?: string;
  weekdays?: Weekday[];
  countdownSeconds?: number;
}
