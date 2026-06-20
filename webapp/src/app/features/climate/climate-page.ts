import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { ClimateService } from '../../core/services/climate.service';
import { Climate, ClimateMode } from '../../core/models/climate';
import { PowerToggle } from '../../shared/power-toggle';
import { TempDial } from '../../shared/temp-dial';
import { ItemImage } from '../../shared/item-image';

const MODES: { key: ClimateMode; label: string }[] = [
  { key: 'COOL', label: 'Kühlen' },
  { key: 'HEAT', label: 'Heizen' },
  { key: 'AUTO', label: 'Auto' },
  { key: 'FAN', label: 'Lüften' },
];

const MODE_ACTION: Record<ClimateMode, string> = {
  COOL: 'Kühlen auf',
  HEAT: 'Heizen auf',
  AUTO: 'Auto · Ziel',
  FAN: 'Lüften · Ziel',
};

const MIN_TEMP = 16;
const MAX_TEMP = 30;

/**
 * Use Case 7: Klimaanlage (siehe docs/climate/SPEC.md). Je Gerät: Ein/Aus, Ring-Dial
 * mit Soll-Temperatur, Modus (Kühlen/Heizen/Auto/Lüften) und Ist-Temperatur.
 */
@Component({
  selector: 'app-climate-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [PowerToggle, TempDial, ItemImage],
  template: `
    <section class="space-y-5">
      <h2 class="text-2xl font-semibold">Klima</h2>

      @if (climate(); as list) {
        @if (list.length === 0) {
          <p class="text-[color:var(--ink-soft)]">Keine Klimaanlagen konfiguriert.</p>
        }
        <div class="grid gap-4 lg:grid-cols-2">
          @for (c of list; track c.id) {
            <article class="glass-card flex gap-5 p-6" [class.opacity-60]="!c.online">
              <div class="w-28 shrink-0 sm:w-36">
                <app-item-image [itemId]="c.id" [label]="c.name" />
              </div>
              <div class="min-w-0 flex-1">
              <!-- Kopf: Name/Raum + Ein/Aus -->
              <header class="flex items-start justify-between gap-3">
                <div>
                  <h3 class="text-lg font-semibold">{{ c.name }}</h3>
                  <p class="text-sm text-[color:var(--ink-soft)]">{{ c.room || 'Klimaanlage' }}</p>
                </div>
                <app-power-toggle
                  [on]="c.power"
                  [disabled]="!c.online"
                  [label]="c.name"
                  (onChange)="onPower(c, $event)"
                />
              </header>

              <!-- Ring-Dial -->
              <div class="my-4">
                <app-temp-dial
                  [target]="c.targetTemp"
                  [current]="c.currentTemp"
                  [min]="minTemp"
                  [max]="maxTemp"
                  [label]="modeAction(c.mode)"
                  emphasis="current"
                />
              </div>

              <!-- Soll-Temperatur einstellen -->
              <div class="flex items-center justify-center gap-5">
                <span class="text-sm text-[color:var(--ink-soft)] tabular-nums">{{ maxTemp }}°</span>
                <button
                  type="button"
                  [disabled]="!c.online || c.targetTemp >= maxTemp"
                  class="glass flex size-12 items-center justify-center rounded-full text-2xl disabled:opacity-40"
                  (click)="changeTarget(c, 1)"
                  aria-label="Wärmer"
                >
                  +
                </button>
                <button
                  type="button"
                  [disabled]="!c.online || c.targetTemp <= minTemp"
                  class="glass flex size-12 items-center justify-center rounded-full text-2xl disabled:opacity-40"
                  (click)="changeTarget(c, -1)"
                  aria-label="Kälter"
                >
                  −
                </button>
                <span class="text-sm text-[color:var(--ink-soft)] tabular-nums">{{ minTemp }}°</span>
              </div>

              <!-- Aktionen: Modi -->
              <p class="mt-6 mb-3 text-sm font-medium text-[color:var(--ink-soft)]">Aktionen</p>
              <div class="grid grid-cols-2 gap-3 sm:grid-cols-4">
                @for (m of modes; track m.key) {
                  <button
                    type="button"
                    [disabled]="!c.online"
                    class="tile-toggle"
                    [class.tile-toggle-active]="c.mode === m.key"
                    (click)="setMode(c, m.key)"
                  >
                    <svg
                      class="size-6"
                      viewBox="0 0 24 24"
                      fill="none"
                      stroke="currentColor"
                      stroke-width="1.7"
                    >
                      @switch (m.key) {
                        @case ('COOL') {
                          <path
                            d="M12 2v20M4.5 6.5 12 11l7.5-4.5M4.5 17.5 12 13l7.5 4.5M3 12h18M12 2l-2.5 2.5M12 2l2.5 2.5M12 22l-2.5-2.5M12 22l2.5-2.5"
                            stroke-linecap="round"
                            stroke-linejoin="round"
                          />
                        }
                        @case ('HEAT') {
                          <path
                            d="M10 13.5V5a2 2 0 1 1 4 0v8.5a4 4 0 1 1-4 0z"
                            stroke-linejoin="round"
                          />
                        }
                        @case ('AUTO') {
                          <path
                            d="M9 16V9l3 5 3-5v7"
                            stroke-linecap="round"
                            stroke-linejoin="round"
                          />
                        }
                        @case ('FAN') {
                          <path
                            d="M12 12c0-3 .5-6 2.5-6S18 8 16 10c2 0 4 .5 4 2.5S16 15 14 13c0 2-.5 5-2.5 5S9 16 11 14c-2 0-5-.5-5-2.5S10 9 12 12z"
                            stroke-linejoin="round"
                          />
                        }
                      }
                    </svg>
                    <span class="text-xs">{{ m.label }}</span>
                  </button>
                }
              </div>

              @if (!c.online) {
                <p class="mt-4 text-xs text-amber-300/90">
                  ⚠ Gerade nicht erreichbar – Sidecar läuft? IP geprüft? (docs/climate/SPEC.md)
                </p>
              }
              </div>
            </article>
          }
        </div>
      } @else {
        <p class="text-[color:var(--ink-soft)]">Lade Klimaanlagen …</p>
      }
    </section>
  `,
})
export class ClimatePage {
  private readonly api = inject(ClimateService);

  protected readonly climate = this.api.climate;
  protected readonly modes = MODES;
  protected readonly minTemp = MIN_TEMP;
  protected readonly maxTemp = MAX_TEMP;

  protected onPower(c: Climate, on: boolean): void {
    this.api.setPower(c.id, on);
  }

  protected setMode(c: Climate, mode: ClimateMode): void {
    this.api.setMode(c.id, mode);
  }

  protected changeTarget(c: Climate, delta: number): void {
    const next = c.targetTemp + delta;
    if (next >= MIN_TEMP && next <= MAX_TEMP) {
      this.api.setTargetTemp(c.id, next);
    }
  }

  protected modeAction(mode: ClimateMode): string {
    return MODE_ACTION[mode] ?? mode;
  }
}
