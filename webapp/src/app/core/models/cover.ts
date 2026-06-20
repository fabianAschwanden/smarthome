/** Spiegelt die REST-DTOs des Backends (publizierte Sprache). */
export type CoverCommand = 'OPEN' | 'CLOSE' | 'STOP';

export interface Cover {
  id: string;
  name: string;
  room: string;
  /** 0 = geschlossen, 100 = offen, -1 = unbekannt. */
  position: number;
  online: boolean;
  observedAt: string;
}
