/** Spiegelt das REST-DTO /api/info. */
export interface AppInfo {
  /** Release-Version (Git-Tag ohne „v", z. B. „1.5.4"); „dev" bei lokalen Builds. */
  version: string;
  /** Build-Zeitpunkt (ISO-8601), leer wenn unbekannt. */
  builtAt: string;
}
