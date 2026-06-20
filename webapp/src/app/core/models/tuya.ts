/** Spiegelt die REST-DTOs des Backends (publizierte Sprache), nicht das Domänenmodell. */
export type SwitchState = 'ON' | 'OFF';

export interface TuyaSwitch {
  id: string;
  name: string;
  room: string;
  state: SwitchState;
  online: boolean;
  /** Kritischer Schalter (z. B. Homecinema = WLAN): AUS erfordert Bestätigung. */
  critical: boolean;
  observedAt: string;
}
