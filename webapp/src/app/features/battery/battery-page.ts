import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { BatteryService } from '../../core/services/battery.service';
import { ForecastService } from '../../core/services/forecast.service';
import { PowerToggle } from '../../shared/power-toggle';
import { ItemImage } from '../../shared/item-image';

/**
 * Use Case 2: Batteriesteuerung über das SMARTFOX-Relais 1 (siehe docs/battery/SPEC.md).
 * Modus umschalten (Manuell/Auto); im Manuell-Modus das Relais direkt schalten.
 */
@Component({
  selector: 'app-battery-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [PowerToggle, ItemImage, RouterLink],
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
        <a
          routerLink="/battery/schedule"
          class="flex items-center gap-1.5 text-sm text-[color:var(--ink-soft)] hover:text-[color:var(--ink)]"
        >
          <svg
            class="size-4"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="1.8"
          >
            <circle cx="12" cy="12" r="9" />
            <path d="M12 7v5l3 2" stroke-linecap="round" stroke-linejoin="round" />
          </svg>
          Zeitsteuerung →
        </a>
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

        <!-- Ladeempfehlung aus der PV-Prognose (Use Case 15) -->
        @if (recommendation(); as rec) {
          <article class="glass-card space-y-4 p-5">
            <div class="flex items-baseline justify-between gap-3">
              <h3 class="font-medium">Ladeempfehlung</h3>
              @if (rec.confidence === 'ROUGH') {
                <!-- Ohne gelerntes Anlagenprofil ist die Empfehlung eine Faustformel. -->
                <span
                  class="rounded-full bg-amber-400/15 px-2 py-0.5 text-[10px] text-amber-300"
                  title="Noch kein gelerntes Anlagenprofil – Schätzung aus der Nennleistung"
                  >grob</span
                >
              }
            </div>
            <p class="text-sm text-[color:var(--ink-soft)]">
              Erwarteter Überschuss
              <span class="font-semibold text-[color:var(--ink)]"
                >{{ zeit(rec.from) }}–{{ zeit(rec.to) }}</span
              >
              , rund
              <span class="font-semibold text-[color:var(--ink)]">{{ rec.expectedKwh }} kWh</span>
              (Spitze {{ rec.peakWatt }} W).
            </p>
            <button
              type="button"
              class="glass rounded-full px-5 py-2 text-sm disabled:opacity-50"
              [disabled]="applying()"
              (click)="uebernehmen()"
            >
              {{ applying() ? 'Wird übernommen …' : 'Als Zeitplan übernehmen' }}
            </button>
            @if (applyResult(); as msg) {
              <p class="text-xs text-[color:var(--ink-soft)]">{{ msg }}</p>
            }
          </article>
        }

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
  private readonly forecast = inject(ForecastService);

  protected readonly control = this.battery.control;
  protected readonly manual = computed(() => this.control()?.mode === 'MANUAL');

  protected readonly recommendation = computed(
    () => this.forecast.surplus()?.recommendation ?? null,
  );
  protected readonly applying = signal(false);
  protected readonly applyResult = signal<string | null>(null);

  /** ISO-Zeitstempel als lokale Uhrzeit "HH:mm". */
  protected zeit(iso: string): string {
    return new Date(iso).toLocaleTimeString('de-CH', { hour: '2-digit', minute: '2-digit' });
  }

  protected uebernehmen(): void {
    this.applying.set(true);
    this.applyResult.set(null);
    this.forecast.applyRecommendation().subscribe({
      next: () => {
        this.applying.set(false);
        this.applyResult.set('Zeitplan angelegt – sichtbar unter Zeitsteuerung.');
      },
      error: (err: { status?: number }) => {
        this.applying.set(false);
        // 409 ist ein Fachfall, kein Fehler: die Empfehlung ist zwischenzeitlich weg.
        this.applyResult.set(
          err?.status === 409
            ? 'Derzeit liegt keine Empfehlung mehr vor.'
            : 'Übernehmen fehlgeschlagen.',
        );
      },
    });
  }

  protected setMode(mode: 'MANUAL' | 'AUTO'): void {
    this.battery.changeMode(mode);
  }

  protected onRelay(on: boolean): void {
    this.battery.switchRelay(on ? 'ON' : 'OFF');
  }
}
