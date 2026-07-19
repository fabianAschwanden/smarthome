/** Spiegelt die REST-DTOs des Backends (publizierte Sprache). */

/** Ergebnis eines Restores: übernommene Einträge je Kategorie. */
export interface RestoreSummary {
  alertSettings: boolean;
  switchSchedules: number;
  batterySchedules: number;
  coverSchedules: number;
  itemImages: number;
}
