import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CoverService } from '../../core/services/cover.service';
import { ForecastService } from '../../core/services/forecast.service';
import { Cover } from '../../core/models/cover';
import { ItemImage } from '../../shared/item-image';

/**
 * Use Case 5: Storensteuerung (Tuya-Cover, siehe docs/cover/SPEC.md).
 * Liste aller Storen mit Jalousie-Visualisierung, Auf/Stopp/Ab und Positions-Slider.
 */
@Component({
  selector: 'app-cover-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ItemImage, RouterLink],
  template: `
    <section class="space-y-5">
      <div class="flex items-center justify-between gap-3">
        <h2 class="text-2xl font-semibold">Storen</h2>
        <a routerLink="/covers/schedule" class="seg px-3 py-1.5 text-sm">Zeitsteuerung</a>
      </div>

      <!-- Hitzeschutz (Use Case 15 / F3): nur sichtbar, wenn es heute etwas zu
           beschatten gibt - starke Einstrahlung UND warme Raeume. -->
      @if (heatProtection(); as hp) {
        <article class="glass-card border-l-4 border-l-[color:var(--accent)] p-5">
          <h3 class="font-medium">Hitzeschutz empfohlen</h3>
          <p class="mt-1 text-sm text-[color:var(--ink-soft)]">
            {{ time(hp.from) }} bis {{ time(hp.to) }} · bis {{ hp.peakGti.toFixed(0) }} W/m² · innen
            {{ hp.indoorTemp.toFixed(1) }} °C
          </p>
          <p class="mt-2 text-sm">
            Die Storen fahren auf <strong>{{ hp.closedPercent }} % zu</strong> und öffnen danach
            wieder. Ein Spalt bleibt offen – ganz unten sitzt der Behang auf dem Anschlag auf.
          </p>
          <div class="mt-3 flex items-center gap-3">
            <button
              type="button"
              class="seg px-4 py-1.5 text-sm"
              [disabled]="applying()"
              (click)="applyHeatProtection()"
            >
              {{ applying() ? 'Wird übernommen …' : 'Übernehmen' }}
            </button>
            @if (applied(); as count) {
              <span class="text-sm text-[color:var(--ink-soft)]"
                >{{ count }} Zeitsteuerungen angelegt</span
              >
            }
          </div>
        </article>
      }

      @if (covers(); as list) {
        @if (list.length === 0) {
          <p class="text-[color:var(--ink-soft)]">Keine Storen konfiguriert.</p>
        }
        <div class="grid gap-4 sm:grid-cols-2">
          @for (c of list; track c.id) {
            <article class="glass-card flex gap-4 p-5" [class.opacity-60]="!c.online">
              <div class="w-24 shrink-0 sm:w-28">
                <app-item-image [itemId]="c.id" [label]="c.name" />
              </div>
              <div class="flex min-w-0 flex-1 flex-col gap-4">
                <div class="flex gap-5">
                  <!-- Jalousie-Visualisierung: voller Schatten = zu (100 %) -->
                  <div class="blind-window h-32 w-24 shrink-0" aria-hidden="true">
                    <div class="blind-shade" [style.height.%]="closedPercent(c)"></div>
                  </div>

                  <!-- Kopf + Position (100 % = zu) -->
                  <div class="flex min-w-0 flex-1 flex-col">
                    <h3 class="truncate font-medium">{{ c.name }}</h3>
                    <p class="text-sm text-[color:var(--ink-soft)]">{{ c.room || 'Store' }}</p>
                    <p class="mt-auto text-3xl font-semibold tabular-nums">
                      {{ c.position < 0 ? '–' : closedPercent(c) + '%' }}
                    </p>
                    <p class="text-xs text-[color:var(--ink-faint)]">
                      {{
                        !c.online
                          ? 'offline'
                          : closedPercent(c) >= 100
                            ? 'zu'
                            : closedPercent(c) <= 0
                              ? 'offen'
                              : 'teilweise'
                      }}
                    </p>
                  </div>
                </div>

                <!-- Auf / Stopp / Ab -->
                <div class="mt-4 flex gap-2">
                  <button
                    type="button"
                    [disabled]="!c.online"
                    class="seg flex flex-1 items-center justify-center gap-1.5 py-2 text-sm disabled:opacity-40"
                    (click)="command(c.id, 'OPEN')"
                    aria-label="Auf"
                  >
                    <svg
                      class="size-4"
                      viewBox="0 0 24 24"
                      fill="none"
                      stroke="currentColor"
                      stroke-width="2"
                    >
                      <path d="M6 15l6-6 6 6" stroke-linecap="round" stroke-linejoin="round" />
                    </svg>
                    Auf
                  </button>
                  <button
                    type="button"
                    [disabled]="!c.online"
                    class="seg flex flex-1 items-center justify-center gap-1.5 py-2 text-sm disabled:opacity-40"
                    (click)="command(c.id, 'STOP')"
                    aria-label="Stopp"
                  >
                    <svg class="size-4" viewBox="0 0 24 24" fill="currentColor" stroke="none">
                      <rect x="7" y="7" width="10" height="10" rx="2" />
                    </svg>
                    Stopp
                  </button>
                  <button
                    type="button"
                    [disabled]="!c.online"
                    class="seg flex flex-1 items-center justify-center gap-1.5 py-2 text-sm disabled:opacity-40"
                    (click)="command(c.id, 'CLOSE')"
                    aria-label="Ab"
                  >
                    <svg
                      class="size-4"
                      viewBox="0 0 24 24"
                      fill="none"
                      stroke="currentColor"
                      stroke-width="2"
                    >
                      <path d="M6 9l6 6 6-6" stroke-linecap="round" stroke-linejoin="round" />
                    </svg>
                    Ab
                  </button>
                </div>

                <!-- Positions-Slider (0 = offen, 100 = zu) -->
                <div class="mt-4">
                  <p class="mb-1 text-xs text-[color:var(--ink-soft)]">
                    Position (0 = offen, 100 = zu)
                  </p>
                  <input
                    type="range"
                    min="0"
                    max="100"
                    step="5"
                    [value]="c.position < 0 ? 0 : closedPercent(c)"
                    [disabled]="!c.online"
                    class="w-full accent-[color:var(--accent)] disabled:opacity-40"
                    (change)="setPosition(c.id, $event)"
                  />
                </div>
              </div>
            </article>
          }
        </div>
      } @else {
        <p class="text-[color:var(--ink-soft)]">Lade Storen …</p>
      }
    </section>
  `,
})
export class CoverPage {
  private readonly api = inject(CoverService);
  private readonly forecast = inject(ForecastService);

  protected readonly covers = this.api.covers;
  protected readonly heatProtection = this.forecast.heatProtection;
  protected readonly applying = signal(false);
  protected readonly applied = signal<number | null>(null);

  constructor() {
    // Einmal beim Öffnen: Das Fenster stammt aus der stündlich gerechneten Prognose.
    this.forecast.loadHeatProtection();
  }

  /** Uhrzeit ohne Datum – das Fenster liegt immer heute. */
  protected time(iso: string): string {
    return new Date(iso).toLocaleTimeString('de-CH', { hour: '2-digit', minute: '2-digit' });
  }

  protected applyHeatProtection(): void {
    this.applying.set(true);
    this.forecast.applyHeatProtection().subscribe({
      next: (schedules) => {
        this.applied.set(schedules.length);
        this.applying.set(false);
      },
      error: () => this.applying.set(false),
    });
  }

  /**
   * „% geschlossen" für die UI (100 = zu). Das Gerät meldet die Position invertiert
   * (dp 2: klein = zu), daher 100 − Gerätewert. Treibt Text, Jalousie-Schatten und Slider.
   */
  protected closedPercent(c: Cover): number {
    if (c.position < 0) {
      return 0;
    }
    return 100 - c.position;
  }

  protected command(id: string, command: 'OPEN' | 'CLOSE' | 'STOP'): void {
    this.api.command(id, command);
  }

  protected setPosition(id: string, event: Event): void {
    // Slider ist „% zu"; das Gerät erwartet die invertierte Skala -> zurückrechnen.
    const closed = Number((event.target as HTMLInputElement).value);
    this.api.setPosition(id, 100 - closed);
  }
}
