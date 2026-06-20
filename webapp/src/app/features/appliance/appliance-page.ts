import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { ApplianceService } from '../../core/services/appliance.service';
import { Appliance, ApplianceFunction } from '../../core/models/appliance';
import { TempDial } from '../../shared/temp-dial';
import { ItemImage } from '../../shared/item-image';

const FUNCTION_LABELS: Record<ApplianceFunction, string> = {
  PUMP: 'Pumpe',
  HEATER: 'Heizung',
  LIGHT: 'Licht',
  MASSAGE: 'Massage',
  FILTER: 'Filterung',
};

/**
 * Use Case 6: Wellness-Anlagen (Whirlpool/Schwimmbecken; siehe docs/appliance/SPEC.md).
 * Je Anlage eine Kachel mit Toggle-Kacheln pro Funktion (Pumpe/Heizung/Licht/Massage).
 */
@Component({
  selector: 'app-appliance-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TempDial, ItemImage],
  template: `
    <section class="space-y-5">
      <h2 class="text-2xl font-semibold">Wellness</h2>

      @if (appliances(); as list) {
        @if (list.length === 0) {
          <p class="text-[color:var(--ink-soft)]">Keine Anlagen konfiguriert.</p>
        }
        <div class="grid gap-4 sm:grid-cols-2">
          @for (a of list; track a.id) {
            <article class="glass-card flex gap-4 p-5" [class.opacity-60]="!a.online">
              <div class="w-24 shrink-0 sm:w-28">
                <app-item-image [itemId]="a.id" [label]="a.name" />
              </div>
              <div class="flex min-w-0 flex-1 flex-col gap-4">
                <header class="flex items-start justify-between gap-3">
                  <div>
                    <h3 class="text-lg font-semibold">{{ a.name }}</h3>
                    <p class="text-sm text-[color:var(--ink-soft)]">
                      {{ a.online ? 'Online' : 'Offline' }}
                      @if (a.room) {
                        · {{ a.room }}
                      }
                    </p>
                  </div>
                  <span
                    class="size-2.5 shrink-0 rounded-full"
                    [class]="a.online ? 'bg-emerald-400' : 'bg-red-400'"
                  ></span>
                </header>

                <!-- Temperatur (nur bei beheizten Anlagen) -->
                @if (a.temperature; as t) {
                  <div>
                    <app-temp-dial
                      [target]="t.target"
                      [current]="t.current"
                      [min]="t.min"
                      [max]="t.max"
                      label="Wassertemperatur"
                      emphasis="current"
                    />
                    <div class="mt-3 flex items-center justify-center gap-5">
                      <span class="text-sm text-[color:var(--ink-soft)] tabular-nums"
                        >{{ t.max }}°</span
                      >
                      <button
                        type="button"
                        [disabled]="!a.online || t.target >= t.max"
                        class="glass flex size-12 items-center justify-center rounded-full text-2xl disabled:opacity-40"
                        (click)="changeTemp(a, 1)"
                        aria-label="Wärmer"
                      >
                        +
                      </button>
                      <button
                        type="button"
                        [disabled]="!a.online || t.target <= t.min"
                        class="glass flex size-12 items-center justify-center rounded-full text-2xl disabled:opacity-40"
                        (click)="changeTemp(a, -1)"
                        aria-label="Kälter"
                      >
                        −
                      </button>
                      <span class="text-sm text-[color:var(--ink-soft)] tabular-nums"
                        >{{ t.min }}°</span
                      >
                    </div>
                  </div>
                }

                <div class="grid grid-cols-2 gap-3 sm:grid-cols-4">
                  @for (fn of functionsOf(a); track fn.key) {
                    <button
                      type="button"
                      [disabled]="!a.online"
                      class="tile-toggle"
                      [class.tile-toggle-active]="fn.on"
                      [attr.aria-pressed]="fn.on"
                      [attr.aria-label]="fn.label"
                      (click)="onFunction(a.id, fn.key, !fn.on)"
                    >
                      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7">
                        @switch (fn.key) {
                          @case ('PUMP') {
                            <path
                              d="M12 3c3 4 5 6.5 5 9a5 5 0 0 1-10 0c0-2.5 2-5 5-9z"
                              stroke-linejoin="round"
                            />
                          }
                          @case ('HEATER') {
                            <path
                              d="M12 3c1.6 2.6 4 4.2 4 7.2a4 4 0 0 1-8 0c0-1.3.6-2.2 1.4-3.2.3 1.1.9 1.6 1.4 1.9C10.4 8 11 6 12 3z"
                              stroke-linejoin="round"
                            />
                          }
                          @case ('LIGHT') {
                            <path
                              d="M9.5 18h5M10.5 21h3M12 3a6 6 0 0 0-3.3 11c.5.4.8 1 .8 1.6h5c0-.6.3-1.2.8-1.6A6 6 0 0 0 12 3z"
                              stroke-linecap="round"
                              stroke-linejoin="round"
                            />
                          }
                          @case ('MASSAGE') {
                            <path
                              d="M4 8c2-2.5 4-2.5 6 0s4 2.5 6 0M4 12c2-2.5 4-2.5 6 0s4 2.5 6 0M4 16c2-2.5 4-2.5 6 0s4 2.5 6 0"
                              stroke-linecap="round"
                              stroke-linejoin="round"
                            />
                          }
                          @case ('FILTER') {
                            <path
                              d="M4 5h16l-6 7v6l-4 2v-8z"
                              stroke-linecap="round"
                              stroke-linejoin="round"
                            />
                          }
                        }
                      </svg>
                      <span class="text-xs">{{ fn.label }}</span>
                    </button>
                  }
                </div>

                @if (!a.online) {
                  <p class="text-xs text-amber-300/90">
                    ⚠ Nicht erreichbar – Steuerschnittstelle noch nicht angebunden.
                  </p>
                }
              </div>
            </article>
          }
        </div>
      } @else {
        <p class="text-[color:var(--ink-soft)]">Lade Anlagen …</p>
      }
    </section>
  `,
})
export class AppliancePage {
  private readonly api = inject(ApplianceService);

  protected readonly appliances = this.api.appliances;

  protected functionsOf(a: Appliance): { key: ApplianceFunction; label: string; on: boolean }[] {
    return Object.keys(a.functions).map((k) => ({
      key: k as ApplianceFunction,
      label: FUNCTION_LABELS[k as ApplianceFunction] ?? k,
      on: a.functions[k] === 'ON',
    }));
  }

  protected onFunction(id: string, fn: ApplianceFunction, on: boolean): void {
    this.api.switchFunction(id, fn, on ? 'ON' : 'OFF');
  }

  protected changeTemp(a: Appliance, delta: number): void {
    const t = a.temperature;
    if (!t) {
      return;
    }
    const next = t.target + delta;
    if (next >= t.min && next <= t.max) {
      this.api.setTargetTemp(a.id, next);
    }
  }
}
