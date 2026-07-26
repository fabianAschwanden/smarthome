import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { DecimalPipe } from '@angular/common';

/**
 * Außentemperatur-Kachel fürs Dashboard: zeigt die von der Klimaanlage (Außengerät)
 * gemeldete Temperatur. Der Außenfühler sitzt an der Außeneinheit und liefert nur bei
 * LAUFENDEM Gerät verlässliche Werte – ist der AC aus, steht dort ein alter/unbrauchbarer
 * Wert. Darum: nur bei {@code powered} anzeigen, sonst „–". Ebenso „–" bei {@code !online}.
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
      } @else if (!powered()) {
        <p class="text-xs text-[color:var(--ink-faint)]">
          Klimaanlage aus – Außenfühler misst nur im Betrieb.
        </p>
      }
    </article>
  `,
})
export class OutdoorTempCard {
  /** Außentemperatur in °C; -1 = unbekannt. */
  readonly temp = input.required<number>();
  readonly online = input<boolean>(true);
  /** Klimaanlage in Betrieb? Nur dann ist der Außenfühler verlässlich. */
  readonly powered = input<boolean>(true);

  protected readonly hasValue = computed(
    () => this.online() && this.powered() && this.temp() > -100,
  );
}
