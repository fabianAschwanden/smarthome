/** Spiegelt das REST-DTO einer nativen Geräte-Weboberfläche (publizierte Sprache). */
export interface NativeView {
  id: string;
  name: string;
  /** Icon-Hinweis (z. B. 'bolt'); kann leer sein. */
  icon: string;
  /** Relativer Proxy-Pfad, über den das iframe lädt (z. B. '/native/smartfox/'). */
  path: string;
}
