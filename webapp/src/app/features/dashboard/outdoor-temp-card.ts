import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { DecimalPipe } from '@angular/common';

/**
 * Außentemperatur-Kachel fürs Dashboard. Der anzuzeigende Wert wird auf der Seite aus
 * mehreren Quellen kombiniert (bevorzugt der dedizierte Außensensor, sonst der
 * AC-Außenfühler) und hier nur dargestellt: {@code temp} = {@code null} → „–".
 * {@code source} benennt die aktive Quelle (z. B. „Wetterstation" / „Klimaanlage").
 */
@Component({
  selector: 'app-outdoor-temp-card',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DecimalPipe],
  template: `
    <article class="glass-card space-y-3 p-5" [class.opacity-60]="temp() === null">
      <header class="flex items-center justify-between">
        <h3 class="font-medium">Außentemperatur</h3>
        <span class="text-xs text-[color:var(--ink-faint)]">{{ source() ?? 'Sensor' }}</span>
      </header>
      <div class="flex items-end justify-between">
        <p class="text-3xl font-semibold tabular-nums">
          {{ temp() !== null ? (temp()! | number: '1.0-1') + '°' : '–' }}
        </p>
        <span class="text-3xl">🌡️</span>
      </div>
      @if (temp() === null) {
        <p class="text-xs text-[color:var(--ink-faint)]">
          Kein Außenwert – Sensor offline und Klimaanlage aus.
        </p>
      }
    </article>
  `,
})
export class OutdoorTempCard {
  /** Anzuzeigende Außentemperatur in °C; {@code null} = kein Wert verfügbar → „–". */
  readonly temp = input.required<number | null>();
  /** Quelle des Werts für den Untertitel (z. B. Sensorname oder „Klimaanlage"). */
  readonly source = input<string | null>(null);
}
