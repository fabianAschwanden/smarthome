/** Spiegelt das REST-DTO der Alert-Einstellungen (publizierte Sprache). */
export interface AlertSettings {
  /** Ob bei kritischen Alarmen ein Push gesendet wird. */
  enabled: boolean;
  /** ntfy.sh-Topic, den die ntfy-App auf dem Handy abonniert. */
  ntfyTopic: string;
}
