import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { DecimalPipe } from '@angular/common';

/**
 * Außentemperatur-Kachel fürs Dashboard: zeigt die von der Klimaanlage (Außengerät)
 * gemeldete Temperatur. {@code temp} = -1 oder {@code !online} → „–".
 */
@Component({
  selector: 'app-outdoor-temp-card',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DecimalPipe],
  template: `
    <article class="glass-card space-y-3 p-5" [class.opacity-60]="!online()">
      <header class="flex items-center justify-between">
        <h3 class="font-medium">Außentemperatur</h3>
        <span class="text-xs text-[color:var(--ink-faint)]">Klimaanlage</span>
      </header>
      <div class="flex items-end justify-between">
        <p class="text-3xl font-semibold tabular-nums">
          {{ hasValue() ? (temp() | number: '1.0-1') + '°' : '–' }}
        </p>
        <span class="text-3xl">🌡️</span>
      </div>
      <p class="text-xs text-[color:var(--ink-soft)]">Sensor auf der Ostseite, Morgensonne</p>
      @if (!online()) {
        <p class="text-xs text-amber-300/90">⚠ Klimaanlage nicht erreichbar.</p>
      }
    </article>
  `,
})
export class OutdoorTempCard {
  /** Außentemperatur in °C; -1 = unbekannt. */
  readonly temp = input.required<number>();
  readonly online = input<boolean>(true);

  protected readonly hasValue = computed(() => this.online() && this.temp() > -100);
}
