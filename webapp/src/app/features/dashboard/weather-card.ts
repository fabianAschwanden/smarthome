import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { Weather, WeatherCondition } from '../../core/models/weather';

const CONDITION_LABEL: Record<WeatherCondition, string> = {
  CLEAR: 'Klar',
  MAINLY_CLEAR: 'Meist sonnig',
  PARTLY_CLOUDY: 'Teils bewölkt',
  CLOUDY: 'Bewölkt',
  FOG: 'Nebel',
  DRIZZLE: 'Nieselregen',
  RAIN: 'Regen',
  SNOW: 'Schnee',
  SHOWERS: 'Schauer',
  THUNDERSTORM: 'Gewitter',
  UNKNOWN: '–',
};

/**
 * Wetterkachel fürs Dashboard (Open-Meteo über das Backend): Ort, aktuelle
 * Temperatur, Zustand, Tages-Hoch/Tief und ein Stundenverlauf mit Icons.
 */
@Component({
  selector: 'app-weather-card',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (weather(); as w) {
      <div class="glass-card weather-card p-5">
        <header class="flex items-start justify-between gap-3">
          <div>
            <h3 class="text-lg font-semibold">{{ w.location }}</h3>
            <p class="text-sm text-[color:var(--ink-soft)]">{{ label(w.condition) }}</p>
            <p class="mt-1 text-xs text-[color:var(--ink-soft)]">
              H: {{ round(w.dayMax) }}° · T: {{ round(w.dayMin) }}°
            </p>
          </div>
          <div class="flex items-center gap-2">
            <span class="weather-icon weather-icon-lg">{{ icon(w.condition) }}</span>
            <span class="text-4xl font-semibold tabular-nums">{{ round(w.currentTemp) }}°</span>
          </div>
        </header>

        @if (w.hours.length) {
          <div
            class="mt-4 flex justify-between gap-2 overflow-x-auto border-t border-white/10 pt-3"
          >
            @for (h of w.hours; track h.label) {
              <div class="flex shrink-0 flex-col items-center gap-1 text-center">
                <span class="text-xs text-[color:var(--ink-soft)]">{{ h.label }}</span>
                <span class="weather-icon">{{ icon(h.condition) }}</span>
                <span class="text-sm font-medium tabular-nums">{{ round(h.temp) }}°</span>
              </div>
            }
          </div>
        }
      </div>
    } @else {
      <div class="glass-card p-5">
        <p class="text-sm text-[color:var(--ink-soft)]">Lade Wetter …</p>
      </div>
    }
  `,
})
export class WeatherCard {
  readonly weather = input<Weather | null>(null);

  protected round(n: number): number {
    return Math.round(n);
  }

  protected label(c: WeatherCondition): string {
    return CONDITION_LABEL[c] ?? '–';
  }

  /** Einfaches Emoji-Icon je Zustand (kein zusätzliches Asset nötig). */
  protected icon(c: WeatherCondition): string {
    switch (c) {
      case 'CLEAR':
      case 'MAINLY_CLEAR':
        return '☀️';
      case 'PARTLY_CLOUDY':
        return '⛅';
      case 'CLOUDY':
        return '☁️';
      case 'FOG':
        return '🌫️';
      case 'DRIZZLE':
      case 'RAIN':
        return '🌧️';
      case 'SHOWERS':
        return '🌦️';
      case 'SNOW':
        return '❄️';
      case 'THUNDERSTORM':
        return '⛈️';
      default:
        return '🌡️';
    }
  }
}
