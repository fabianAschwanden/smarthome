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
  /** Konfigurierte Ladeleistung, aus der die Energie gerechnet wird (nicht gemessen). */
  watt: number;
  /**
   * Aus dem Hausverbrauch abgeleiteter Wert – nur zum Vergleich, damit sich die
   * konfigurierte Leistung nachjustieren lässt. Weicht er stark ab, lohnt ein Blick.
   */
  measuredWatt: number | null;
  energyKwh: number;
  /** Immer true – die Anlage misst das Lade-Relais nicht separat. */
  estimated: boolean;
}
