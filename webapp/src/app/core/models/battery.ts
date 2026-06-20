/** Spiegelt die REST-DTOs des Backends (publizierte Sprache), nicht das Domänenmodell. */
export type ControlMode = 'MANUAL' | 'AUTO';
export type RelayState = 'ON' | 'OFF';

export interface BatteryControl {
  mode: ControlMode;
  desiredState: RelayState;
  changedAt: string;
}
