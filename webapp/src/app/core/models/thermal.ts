/**
 * Was ein temperaturführendes Gerät gerade tut – spiegelt das Backend-Enum
 * ThermalActivity. Die Oberfläche färbt danach: warm beim Heizen, kühl beim Kühlen,
 * neutral sonst. IDLE heisst nicht «aus»: Ein Whirlpool mit Soll 20 °C bei 31 °C Wasser
 * ist an und tut trotzdem nichts.
 */
export type ThermalActivity = 'HEATING' | 'COOLING' | 'IDLE';

/** Farbton der Oberfläche zu einer Tätigkeit. */
export type ThermalTone = 'warm' | 'cool' | 'neutral';

export function thermalTone(activity: ThermalActivity | null | undefined): ThermalTone {
  switch (activity) {
    case 'HEATING':
      return 'warm';
    case 'COOLING':
      return 'cool';
    default:
      return 'neutral';
  }
}

/** Kurzes Wort für Menschen, die Farben nicht unterscheiden – steht neben dem Punkt. */
export function thermalLabel(activity: ThermalActivity | null | undefined): string | null {
  switch (activity) {
    case 'HEATING':
      return 'heizt';
    case 'COOLING':
      return 'kühlt';
    default:
      return null;
  }
}
