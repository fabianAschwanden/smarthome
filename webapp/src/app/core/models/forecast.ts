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

/** Ein Tag im Vergleich Prognose gegen Ist. */
export interface AccuracyDay {
  date: string;
  forecastKwh: number;
  /** Null, solange der Tag nicht abgeschlossen ist – nicht 0. */
  actualKwh: number | null;
  /** Null bei offenem Tag und bei einem Tag ganz ohne Ertrag (Fehler nicht definiert). */
  deviationPercent: number | null;
}

export interface Accuracy {
  /** Mittlerer relativer Fehler; null, solange kein Tag bewertbar ist. */
  mapePercent: number | null;
  /** Auf wie vielen Tagen der MAPE beruht – ohne das ist er nicht einzuordnen. */
  ratedDays: number;
  days: AccuracyDay[];
}

/** Fenster, in dem Beschatten gegen die Hitze lohnt (UC 15 / F3). */
export interface HeatProtection {
  from: string;
  /** Exklusiv – dann wird wieder geöffnet. */
  to: string;
  peakGti: number;
  indoorTemp: number;
  /** Geräteskala (0 = zu, 100 = offen). */
  shadedPosition: number;
  /** Dieselbe Angabe in der Sprache der Oberfläche – das Backend rechnet um. */
  closedPercent: number;
}

/** Wie der letzte Lauf der Lade-Automatik ausging. */
export type AutoApplyOutcome =
  'APPLIED' | 'NO_RECOMMENDATION' | 'FORECAST_UNRELIABLE' | 'NOT_ENOUGH_DATA';

export interface AutoApply {
  enabled: boolean;
  /** Null, solange die Automatik noch nie gelaufen ist. */
  lastRunDay: string | null;
  lastOutcome: AutoApplyOutcome | null;
  /** Klartext dazu – sagt auch, warum NICHT geschaltet wurde. */
  lastDetail: string;
}
