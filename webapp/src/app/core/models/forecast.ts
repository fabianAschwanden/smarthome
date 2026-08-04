/** Spiegelt die REST-DTOs des Backends (publizierte Sprache), nicht das Domänenmodell. */

/** Wie belastbar die Prognose ist: gelernt oder grober Cold-Start-Fallback. */
export type Confidence = 'LEARNED' | 'ROUGH';

/** Eine Stunde der Prognose; {@link hour} markiert den Stundenbeginn (ISO-8601). */
export interface ForecastHour {
  hour: string;
  expectedPvWatt: number;
  gti: number;
}

export interface PvForecast {
  hours: ForecastHour[];
  todayKwh: number;
  tomorrowKwh: number;
  confidence: Confidence;
  /** Wann das zugrunde liegende Anlagenprofil gelernt wurde. */
  learnedAt: string | null;
  /** Wann diese Prognose gerechnet wurde – bei Ausfall der Quelle altert sie sichtbar. */
  computedAt: string;
}

/** Ein Überschussfenster; {@link to} ist exklusiv. */
export interface SurplusWindow {
  from: string;
  to: string;
  expectedKwh: number;
  peakWatt: number;
}

export interface Recommendation {
  from: string;
  to: string;
  expectedKwh: number;
  peakWatt: number;
  confidence: Confidence;
}

export interface Surplus {
  /** Typischer Verbrauch je Stunden-Slot (24 Werte) – Vergleichskurve. */
  baselineWeekdayWatt: number[];
  baselineWeekendWatt: number[];
  windows: SurplusWindow[];
  /** Null, wenn kein Fenster die Schwellen erreicht – an einem trüben Tag normal. */
  recommendation: Recommendation | null;
}
