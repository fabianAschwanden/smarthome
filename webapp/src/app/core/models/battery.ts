/** Spiegelt die REST-DTOs des Backends (publizierte Sprache), nicht das Domänenmodell. */
export type ControlMode = 'MANUAL' | 'AUTO';
export type RelayState = 'ON' | 'OFF';

export interface BatteryControl {
  mode: ControlMode;
  desiredState: RelayState;
  changedAt: string;
}

/** Ein Ladevorgang der Batterie; die Energie ist geschätzt, nicht gemessen. */
export interface ChargingSession {
  startedAt: string;
  endedAt: string;
  minutes: number;
  /** Ladeleistung aus dem Verbrauchssprung beim Einschalten. */
  watt: number;
  /**
   * Gegenmessung aus der Mitte des Ladevorgangs (kurz abgeschaltet); null, wenn keine
   * stattgefunden hat. Weicht sie stark von {@link watt} ab, hat beim Einschalten
   * vermutlich eine andere Last mitgeschaltet.
   */
  verifiedWatt: number | null;
  energyKwh: number;
  /** Immer true – die Anlage misst das Lade-Relais nicht separat. */
  estimated: boolean;
}
