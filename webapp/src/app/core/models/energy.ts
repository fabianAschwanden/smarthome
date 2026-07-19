/** Spiegelt die REST-DTOs des Backends (publizierte Sprache), nicht das Domänenmodell. */
export interface DailyEnergy {
  productionWhToday: number | null;
  totalWh: number | null;
  autonomyPercent: number | null;
  selfConsumptionPercent: number | null;
}

export interface PowerReading {
  source: string;
  timestamp: string;
  gridWatt: number;
  pvWatt: number;
  batteryWatt: number | null;
  consumptionWatt: number;
  status: 'OK' | 'ERROR';
  daily: DailyEnergy | null;
}

export interface SourceComparison {
  first: string;
  second: string;
  consumptionDeltaWatt: number;
  pvDeltaWatt: number;
  gridDeltaWatt: number;
  consumptionDeltaPercent: number;
}

export interface EnergySnapshot {
  timestamp: string;
  readings: PowerReading[];
  comparison: SourceComparison | null;
}

/** Bereich des Energie-Verlaufs. */
export type HistoryRange = 'day' | 'week' | 'month';

/** Ein Verlaufs-Abschnitt: Beginn (ISO-8601), Energie und Eigennutzung in kWh. */
export interface EnergyBucket {
  start: string;
  pvKwh: number;
  consumptionKwh: number;
  selfUseKwh: number;
}

/** Roh-Messpunkt (Leistungskurve der Tagesansicht). */
export interface EnergySamplePoint {
  timestamp: string;
  pvWatt: number;
  consumptionWatt: number;
}

/** Verlauf von Verbrauch und PV-Produktion; samples nur bei range=day gefüllt. */
export interface EnergyHistory {
  range: HistoryRange;
  buckets: EnergyBucket[];
  samples: EnergySamplePoint[];
}
