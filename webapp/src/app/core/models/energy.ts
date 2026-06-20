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
