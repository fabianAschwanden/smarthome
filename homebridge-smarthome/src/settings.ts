/** Muss mit dem "platform"-Eintrag in der Homebridge-config.json uebereinstimmen. */
export const PLATFORM_NAME = 'Smarthome';

/** Muss dem Paketnamen entsprechen, sonst findet Homebridge das Plugin nicht. */
export const PLUGIN_NAME = 'homebridge-smarthome';

/** Poll-Intervall in Sekunden, wenn die Konfiguration nichts vorgibt. */
export const DEFAULT_POLL_SECONDS = 10;

/** Untergrenze: haeufigeres Fragen belastet die App, ohne frischere Daten zu liefern. */
export const MIN_POLL_SECONDS = 2;
