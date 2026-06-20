import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { BatteryService } from '../../core/services/battery.service';
import { PowerToggle } from '../../shared/power-toggle';
import { ItemImage } from '../../shared/item-image';

/**
 * Use Case 2: Batteriesteuerung über das SMARTFOX-Relais 1 (siehe docs/battery/SPEC.md).
 * Modus umschalten (Manuell/Auto); im Manuell-Modus das Relais direkt schalten.
 */
@Component({
  selector: 'app-battery-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [PowerToggle, ItemImage],
  template: `
    <section class="space-y-5">
      <div class="flex items-end justify-between">
        <div>
          <div class="flex items-center gap-3">
            <h2 class="text-2xl font-semibold">Batteriesteuerung</h2>
            <span class="chip">📍 Keller</span>
          </div>
          @if (control(); as ctrl) {
            <p class="text-sm text-[color:var(--ink-faint)]">Stand: {{ ctrl.changedAt }}</p>
          }
        </div>
      </div>

      @if (control(); as ctrl) {
        <!-- Hero: Bild links, Relais-Zustand + Ein/Aus rechts -->
        <article class="glass-card flex gap-5 p-6">
          <div class="w-28 shrink-0 sm:w-40">
            <app-item-image itemId="battery" label="Batterie / Speicher" />
          </div>
          <div class="flex flex-1 flex-wrap items-center justify-between gap-6">
            <div>
              <p class="text-sm text-[color:var(--ink-soft)]">Relais 1 · Batterieladung</p>
              <p class="mt-1 text-4xl font-semibold tracking-tight sm:text-5xl">
                {{ ctrl.desiredState === 'ON' ? 'EIN' : 'AUS' }}
              </p>
            </div>
            <app-power-toggle
              [on]="ctrl.desiredState === 'ON'"
              [disabled]="!manual()"
              label="Batterieladung schalten"
              (onChange)="onRelay($event)"
            />
          </div>
        </article>

        <!-- Steuerung -->
        <article class="glass-card space-y-5 p-5">
          <div>
            <p class="mb-2 text-sm text-[color:var(--ink-soft)]">Modus</p>
            <div class="glass inline-flex items-center gap-1 rounded-full p-1.5">
              <button
                type="button"
                class="seg px-5 py-2 text-sm"
                [attr.data-active]="ctrl.mode === 'AUTO'"
                (click)="setMode('AUTO')"
              >
                Automatik
              </button>
              <button
                type="button"
                class="seg px-5 py-2 text-sm"
                [attr.data-active]="ctrl.mode === 'MANUAL'"
                (click)="setMode('MANUAL')"
              >
                Manuell
              </button>
            </div>
          </div>

          @if (!manual()) {
            <p class="flex items-center gap-1.5 text-xs text-amber-300/90">
              🔒 Gesperrt – erst auf „Manuell" wechseln, um das Relais selbst zu schalten. Im
              Automatik-Modus steuert der PV-Überschuss.
            </p>
          }
        </article>
      } @else {
        <p class="text-[color:var(--ink-soft)]">Lade Steuerstand …</p>
      }
    </section>
  `,
})
export class BatteryPage {
  private readonly battery = inject(BatteryService);

  protected readonly control = this.battery.control;
  protected readonly manual = computed(() => this.control()?.mode === 'MANUAL');

  protected setMode(mode: 'MANUAL' | 'AUTO'): void {
    this.battery.changeMode(mode);
  }

  protected onRelay(on: boolean): void {
    this.battery.switchRelay(on ? 'ON' : 'OFF');
  }
}
