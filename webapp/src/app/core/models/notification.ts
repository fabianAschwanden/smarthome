/** Schweregrad einer Meldung in der Nachrichtenzentrale. */
export type NotificationSeverity = 'alarm' | 'warning' | 'info';

/** Eine aggregierte Geräte-Meldung (rein clientseitig aus den Service-Signalen abgeleitet). */
export interface AppNotification {
  /** Stabiler Schlüssel (Quelle+Gerät+Art), damit gleiche Meldungen nicht doppeln. */
  id: string;
  severity: NotificationSeverity;
  title: string;
  detail: string;
  /** Quelle für ein passendes Icon (smoke, switch, cover, sensor, climate, battery). */
  source: 'smoke' | 'switch' | 'cover' | 'sensor' | 'climate' | 'battery';
}
