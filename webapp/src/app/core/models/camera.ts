/** Spiegelt das REST-DTO des Backends (publizierte Sprache). */
export interface Camera {
  id: string;
  name: string;
  room: string;
  /** Name des Streams im go2rtc-Gateway. */
  stream: string;
}
