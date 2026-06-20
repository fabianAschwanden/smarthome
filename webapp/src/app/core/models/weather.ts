/** Spiegelt die REST-DTOs des Backends (publizierte Sprache), nicht das Domänenmodell. */
export type WeatherCondition =
  | 'CLEAR'
  | 'MAINLY_CLEAR'
  | 'PARTLY_CLOUDY'
  | 'CLOUDY'
  | 'FOG'
  | 'DRIZZLE'
  | 'RAIN'
  | 'SNOW'
  | 'SHOWERS'
  | 'THUNDERSTORM'
  | 'UNKNOWN';

export interface WeatherHour {
  label: string;
  temp: number;
  condition: WeatherCondition;
}

export interface Weather {
  location: string;
  currentTemp: number;
  condition: WeatherCondition;
  dayMax: number;
  dayMin: number;
  hours: WeatherHour[];
  observedAt: string;
}
