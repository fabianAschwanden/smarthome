import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { ApplianceService } from '../../core/services/appliance.service';
import { ForecastService } from '../../core/services/forecast.service';
import { Appliance, ApplianceFunction, ApplianceTemperature } from '../../core/models/appliance';
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

      <!-- Aufheizen im Ueberschussfenster (Use Case 15 / F4). Nur sichtbar, wenn die
           Prognose heute ein Fenster erwartet. -->
      @if (surplusWindow(); as fenster) {
        <article class="glass-card space-y-3 p-5">
          <h3 class="font-medium">Mit dem PV-Überschuss aufheizen</h3>
          <p class="text-sm text-[color:var(--ink-soft)]">
            Erwartetes Fenster {{ zeit(fenster.from) }}–{{ zeit(fenster.to) }}, rund
            {{ fenster.expectedKwh }} kWh. Die Heizung geht zu Beginn an und am Ende aus.
          </p>
          <p class="text-xs text-amber-300/90">
            Am Fensterende wird auf die Grundtemperatur zurückgestellt – auch wenn du zwischendurch
            von Hand etwas anderes eingestellt hast. Solange der Whirlpool heizt, lädt die Batterie
            nicht zusätzlich auf Befehl.
          </p>
          <div class="flex items-center gap-3">
            <button
              type="button"
              class="seg px-4 py-1.5 text-sm"
              [disabled]="planning()"
              (click)="ueberschussNutzen()"
            >
              {{ planning() ? 'Wird geplant …' : 'Einplanen' }}
            </button>
            @if (planResult(); as msg) {
              <span class="text-sm text-[color:var(--ink-soft)]">{{ msg }}</span>
            }
          </div>
        </article>
      }

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
                      [target]="soll(t)"
                      [current]="t.current"
                      [min]="t.min"
                      [max]="t.max"
                      label="Wassertemperatur"
                      emphasis="current"
                    />
                    @if (wirdGestellt(t)) {
                      <!-- Die Anlage uebernimmt den Wert verzoegert; bis dahin ist der
                           Unterschied zwischen "gewuenscht" und "eingestellt" sichtbar. -->
                      <p class="mt-2 text-center text-xs text-[color:var(--ink-soft)]">
                        {{ t.pending }}° wird gestellt … (Anlage meldet {{ t.target }}°)
                      </p>
                    }
                    <div class="mt-3 flex items-center justify-center gap-5">
                      <span class="text-sm text-[color:var(--ink-soft)] tabular-nums"
                        >{{ t.max }}°</span
                      >
                      <button
                        type="button"
                        [disabled]="!a.online || soll(t) >= t.max"
                        class="glass flex size-12 items-center justify-center rounded-full text-2xl disabled:opacity-40"
                        (click)="changeTemp(a, 1)"
                        aria-label="Wärmer"
                      >
                        +
                      </button>
                      <button
                        type="button"
                        [disabled]="!a.online || soll(t) <= t.min"
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

                    <!-- Direkteingabe: Ein Sprung von 30 auf 38 Grad soll ein Schritt
                         sein und nicht acht. -->
                    <form
                      class="mt-3 flex items-center justify-center gap-2"
                      (submit)="setTemp(a, $event)"
                    >
                      <input
                        type="number"
                        class="glass w-24 rounded-full px-4 py-2 text-center tabular-nums"
                        [attr.min]="t.min"
                        [attr.max]="t.max"
                        step="1"
                        [value]="soll(t)"
                        [disabled]="!a.online"
                        aria-label="Soll-Temperatur eingeben"
                      />
                      <button type="submit" class="seg px-4 py-2 text-sm" [disabled]="!a.online">
                        Setzen
                      </button>
                    </form>
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
  private readonly forecast = inject(ForecastService);

  protected readonly appliances = this.api.appliances;
  protected readonly planning = signal(false);
  protected readonly planResult = signal<string | null>(null);

  /** Das erste erwartete Überschussfenster; null, wenn heute keines vorliegt. */
  protected readonly surplusWindow = computed(() => this.forecast.surplus()?.windows?.[0] ?? null);

  protected zeit(iso: string): string {
    return new Date(iso).toLocaleTimeString('de-CH', { hour: '2-digit', minute: '2-digit' });
  }

  protected ueberschussNutzen(): void {
    this.planning.set(true);
    this.forecast.applyWellnessSurplus().subscribe({
      next: (schedules) => {
        this.planResult.set(`${schedules.length} Schaltaufträge angelegt`);
        this.planning.set(false);
      },
      error: () => {
        this.planResult.set('Derzeit kein Überschussfenster');
        this.planning.set(false);
      },
    });
  }

  /**
   * Die schaltbaren Funktionen – ohne die Heizung.
   *
   * <p>Die Heizung eines Gecko-Spas ist kein Schalter: Sie ist dauerhaft aktiv und wird
   * ausschliesslich über die Soll-Temperatur geregelt; die App weist ein Schalten mit
   * 503 zurück. Ein Knopf, der nur scheitern kann, gehört nicht auf die Kachel – die
   * Temperatur darüber ist die Heizungsbedienung.
   */
  protected functionsOf(a: Appliance): { key: ApplianceFunction; label: string; on: boolean }[] {
    return Object.keys(a.functions)
      .filter((k) => k !== 'HEATER')
      .map((k) => ({
        key: k as ApplianceFunction,
        label: FUNCTION_LABELS[k as ApplianceFunction] ?? k,
        on: a.functions[k] === 'ON',
      }));
  }

  protected onFunction(id: string, fn: ApplianceFunction, on: boolean): void {
    this.api.switchFunction(id, fn, on ? 'ON' : 'OFF');
  }

  /**
   * Was gerade gewollt ist: der noch offene Wunsch, sonst die gemeldete Soll-Temperatur.
   *
   * <p>Darauf rechnen auch die Schritte auf und ab. Rechnete man auf dem gemeldeten Wert
   * weiter, käme man nie mehr als ein Grad weit – die Anlage übernimmt verzögert, und
   * der zweite Klick startete wieder beim alten Stand.
   */
  protected soll(t: ApplianceTemperature): number {
    return t.pending ?? t.target;
  }

  /** Läuft gerade ein Wunsch, den die Anlage noch nicht übernommen hat? */
  protected wirdGestellt(t: ApplianceTemperature): boolean {
    return t.pending !== null && t.pending !== undefined && t.pending !== t.target;
  }

  protected changeTemp(a: Appliance, delta: number): void {
    const t = a.temperature;
    if (!t) {
      return;
    }
    this.applyTemp(a, this.soll(t) + delta);
  }

  /** Direkteingabe: ein Sprung über mehrere Grad statt vieler Einzelschritte. */
  protected setTemp(a: Appliance, event: Event): void {
    event.preventDefault();
    const input = (event.target as HTMLFormElement).querySelector('input');
    const wanted = Number(input?.value);
    if (Number.isFinite(wanted)) {
      this.applyTemp(a, Math.round(wanted));
    }
  }

  private applyTemp(a: Appliance, wanted: number): void {
    const t = a.temperature;
    if (!t) {
      return;
    }
    // Auf den Bereich der Anlage begrenzen statt die Eingabe zu verwerfen: Wer 45 tippt,
    // will das Maximum - eine wortlos ignorierte Eingabe wäre die schlechtere Antwort.
    const next = Math.min(t.max, Math.max(t.min, wanted));
    if (next !== this.soll(t)) {
      this.api.setTargetTemp(a.id, next);
    }
  }
}
