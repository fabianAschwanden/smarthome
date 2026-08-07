import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { EnergyService } from '../../core/services/energy.service';
import { ForecastService } from '../../core/services/forecast.service';
import { EnergyHistory, HistoryRange, PowerReading } from '../../core/models/energy';
import { ItemImage } from '../../shared/item-image';
import { ENERGY_COLORS, EnergyHistoryChart } from './energy-history-chart';

const RANGES: { key: HistoryRange; label: string }[] = [
  { key: 'day', label: 'Tag' },
  { key: 'week', label: 'Woche' },
  { key: 'month', label: 'Monat' },
];

/**
 * Use Case 1: aktuellen Energieverbrauch visualisieren – Fronius und SMARTFOX
 * nebeneinander mit Differenz (docs/energy/SPEC.md §5) plus Verlauf von Verbrauch und
 * PV-Produktion (Tag/Woche/Monat) als kWh-Balkendiagramm.
 */
@Component({
  selector: 'app-energy-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ItemImage, EnergyHistoryChart],
  template: `
    <section class="space-y-5">
      @if (snapshot(); as snap) {
        <div class="flex items-end justify-between">
          <div>
            <h2 class="text-2xl font-semibold">Power Consumption</h2>
            <p class="text-sm text-[color:var(--ink-faint)]">Stand: {{ snap.timestamp }}</p>
          </div>
        </div>

        <!-- Hero: PV-Bild links, aktueller Verbrauch rechts -->
        @if (primary(); as p) {
          <article class="glass-card flex gap-5 p-6">
            <div class="w-32 shrink-0 sm:w-44">
              <app-item-image itemId="energy" label="Energieversorgung (PV-Anlage)" />
            </div>
            <div class="flex flex-1 flex-wrap items-center justify-between gap-6">
              <div>
                <p class="text-sm text-[color:var(--ink-soft)]">
                  Aktueller Verbrauch · {{ p.source }}
                </p>
                <p class="mt-1 text-4xl font-semibold tracking-tight sm:text-5xl">
                  {{ watt(p.consumptionWatt) }}
                </p>
              </div>
              <div class="flex gap-8 text-sm">
                <div>
                  <p class="text-[color:var(--ink-faint)]">PV-Produktion</p>
                  <p class="mt-1 text-lg font-medium">{{ watt(p.pvWatt) }}</p>
                </div>
                <div>
                  <p class="text-[color:var(--ink-faint)]">
                    Netz ({{ p.gridWatt >= 0 ? 'Bezug' : 'Einspeisung' }})
                  </p>
                  <p class="mt-1 text-lg font-medium">{{ watt(p.gridWatt) }}</p>
                </div>
              </div>
            </div>
          </article>
        }

        <!-- Quellen nebeneinander -->
        <div class="grid gap-4 sm:grid-cols-2">
          @for (reading of snap.readings; track reading.source) {
            <article class="glass-card p-5">
              <header class="flex items-center justify-between">
                <h3 class="font-medium">{{ reading.source }}</h3>
                <span
                  class="rounded-full px-3 py-0.5 text-xs font-semibold"
                  [class]="
                    reading.status === 'OK'
                      ? 'bg-emerald-400/20 text-emerald-200'
                      : 'bg-red-400/20 text-red-200'
                  "
                >
                  {{ reading.status }}
                </span>
              </header>
              <dl class="mt-4 space-y-2 text-sm">
                <div class="flex justify-between">
                  <dt class="text-[color:var(--ink-soft)]">Verbrauch</dt>
                  <dd class="font-semibold">{{ watt(reading.consumptionWatt) }}</dd>
                </div>
                <div class="flex justify-between">
                  <dt class="text-[color:var(--ink-soft)]">PV-Produktion</dt>
                  <dd>{{ watt(reading.pvWatt) }}</dd>
                </div>
                <div class="flex justify-between">
                  <dt class="text-[color:var(--ink-soft)]">
                    Netz ({{ reading.gridWatt >= 0 ? 'Bezug' : 'Einspeisung' }})
                  </dt>
                  <dd>{{ watt(reading.gridWatt) }}</dd>
                </div>
                @if (reading.batteryWatt !== null) {
                  <div class="flex justify-between">
                    <dt class="text-[color:var(--ink-soft)]">Batterie</dt>
                    <dd>{{ watt(reading.batteryWatt) }}</dd>
                  </div>
                }
              </dl>
            </article>
          }
        </div>

        <!-- Differenz der Quellen -->
        @if (snap.comparison; as cmp) {
          <article class="glass-card border-l-4 border-l-[color:var(--accent)] p-5">
            <h3 class="font-medium">Differenz {{ cmp.first }} ↔ {{ cmp.second }}</h3>
            <div class="mt-3 grid gap-3 text-sm sm:grid-cols-3">
              <div>
                <span class="text-[color:var(--ink-soft)]">Verbrauch</span>
                <p class="mt-0.5 font-semibold">
                  {{ watt(cmp.consumptionDeltaWatt) }}
                  <span class="text-[color:var(--ink-faint)]"
                    >({{ cmp.consumptionDeltaPercent.toFixed(1) }} %)</span
                  >
                </p>
              </div>
              <div>
                <span class="text-[color:var(--ink-soft)]">PV</span>
                <p class="mt-0.5 font-semibold">{{ watt(cmp.pvDeltaWatt) }}</p>
              </div>
              <div>
                <span class="text-[color:var(--ink-soft)]">Netz</span>
                <p class="mt-0.5 font-semibold">{{ watt(cmp.gridDeltaWatt) }}</p>
              </div>
            </div>
          </article>
        }
      } @else {
        <p class="text-[color:var(--ink-soft)]">Lade aktuelle Werte …</p>
      }

      <!-- Energie-Verlauf (Fronius-Solar.web-Optik): Leistungskurve am Tag,
           kWh-Balken für Woche/Monat, Legende mit Summen darunter -->
      <article class="glass-card space-y-4 p-5">
        <header class="flex flex-wrap items-center justify-between gap-3">
          <h3 class="text-lg font-semibold">Energie</h3>
          <div class="glass inline-flex items-center gap-1 rounded-full p-1">
            @for (r of ranges; track r.key) {
              <button
                type="button"
                class="seg px-4 py-1.5 text-sm"
                [attr.data-active]="range() === r.key"
                (click)="setRange(r.key)"
              >
                {{ r.label }}
              </button>
            }
          </div>
        </header>

        @if (history(); as h) {
          <app-energy-history-chart [history]="h" />
          <div class="space-y-2 border-t border-white/10 pt-3">
            <div class="flex items-center justify-between">
              <span class="flex items-center gap-2.5 text-sm text-[color:var(--ink-soft)]">
                <span class="size-2.5 rounded-full" [style.background]="colors.production"></span>
                Erzeugung
              </span>
              <span class="text-lg font-semibold tabular-nums"
                >{{ totalPv() }} <span class="text-sm font-normal">kWh</span></span
              >
            </div>
            <div class="flex items-center justify-between">
              <span class="flex items-center gap-2.5 text-sm text-[color:var(--ink-soft)]">
                <span class="size-2.5 rounded-full" [style.background]="colors.selfUse"></span>
                Eigennutzung
              </span>
              <span class="text-lg font-semibold tabular-nums"
                >{{ totalSelfUse() }} <span class="text-sm font-normal">kWh</span></span
              >
            </div>
            <div class="flex items-center justify-between">
              <span class="flex items-center gap-2.5 text-sm text-[color:var(--ink-soft)]">
                <span class="size-2.5 rounded-full" [style.background]="colors.consumption"></span>
                Verbrauch
              </span>
              <span class="text-lg font-semibold tabular-nums"
                >{{ totalConsumption() }} <span class="text-sm font-normal">kWh</span></span
              >
            </div>
          </div>
        } @else {
          <p class="text-sm text-[color:var(--ink-soft)]">Lade Verlauf …</p>
        }
      </article>

      <!-- Prognosegüte (Use Case 15 / F1): Wie gut lag die Vorhersage? Steht hier und
           nicht auf dem Dashboard – die Zahl ist zum Nachschauen, nicht zum Danebenstehen. -->
      @if (accuracy(); as acc) {
        <article class="glass-card space-y-4 p-5">
          <header class="flex flex-wrap items-baseline justify-between gap-3">
            <h3 class="text-lg font-semibold">Prognosegüte</h3>
            @if (acc.mapePercent !== null) {
              <p class="text-sm text-[color:var(--ink-soft)]">
                <span class="text-2xl font-semibold tabular-nums text-[color:var(--ink)]"
                  >{{ acc.mapePercent.toFixed(0) }} %</span
                >
                mittlere Abweichung über {{ acc.ratedDays }}
                {{ acc.ratedDays === 1 ? 'Tag' : 'Tage' }}
              </p>
            } @else {
              <!-- Leer statt 0: "noch nicht messbar" ist etwas anderes als "kein Fehler". -->
              <p class="text-sm text-[color:var(--ink-soft)]">Noch kein Tag auswertbar</p>
            }
          </header>

          @if (acc.days.length > 0) {
            <ul class="space-y-1.5">
              @for (day of acc.days; track day.date) {
                <li class="flex items-center justify-between gap-3 text-sm">
                  <span class="text-[color:var(--ink-soft)]">{{ dayLabel(day.date) }}</span>
                  <span class="flex items-center gap-3 tabular-nums">
                    <span class="text-[color:var(--ink-faint)]"
                      >{{ day.forecastKwh.toFixed(1) }} kWh erwartet</span
                    >
                    @if (day.actualKwh !== null) {
                      <span class="font-semibold">{{ day.actualKwh.toFixed(1) }} kWh</span>
                    } @else {
                      <span class="text-[color:var(--ink-faint)]">läuft noch</span>
                    }
                    @if (day.deviationPercent !== null) {
                      <span
                        class="w-14 text-right"
                        [class.text-[color:var(--accent)]]="day.deviationPercent > 25"
                        >{{ day.deviationPercent.toFixed(0) }} %</span
                      >
                    } @else {
                      <span class="w-14 text-right text-[color:var(--ink-faint)]">–</span>
                    }
                  </span>
                </li>
              }
            </ul>
          } @else {
            <p class="text-sm text-[color:var(--ink-soft)]">
              Noch keine Prognose festgehalten – der erste Eintrag entsteht morgen früh.
            </p>
          }
        </article>
      }
    </section>
  `,
})
export class EnergyPage {
  private readonly energy = inject(EnergyService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly snapshot = this.energy.snapshot;
  private readonly forecast = inject(ForecastService);
  protected readonly accuracy = this.forecast.accuracy;
  protected readonly ranges = RANGES;
  protected readonly colors = ENERGY_COLORS;
  protected readonly range = signal<HistoryRange>('day');
  protected readonly history = signal<EnergyHistory | null>(null);

  protected readonly totalPv = computed(() => this.sum((b) => b.pvKwh));
  protected readonly totalConsumption = computed(() => this.sum((b) => b.consumptionKwh));
  protected readonly totalSelfUse = computed(() => this.sum((b) => b.selfUseKwh));

  /** Referenzquelle für die Hero-Kennzahl: bevorzugt Fronius (PV-Wechselrichter). */
  protected readonly primary = computed<PowerReading | undefined>(() => {
    const readings = this.snapshot()?.readings ?? [];
    return (
      readings.find((r) => r.source === 'FRONIUS' && r.status === 'OK') ??
      readings.find((r) => r.source === 'FRONIUS') ??
      readings[0]
    );
  });

  constructor() {
    this.loadHistory('day');
    // Einmal beim Öffnen: Die Güte ändert sich einmal je Tag, Pollen wäre Beschäftigung.
    this.forecast.loadAccuracy();
  }

  protected setRange(range: HistoryRange): void {
    if (range !== this.range()) {
      this.range.set(range);
      this.history.set(null);
      this.loadHistory(range);
    }
  }

  /** «Heute», «Gestern» oder das kurze Datum – ein ISO-String liest sich in einer Liste schlecht. */
  protected dayLabel(isoDate: string): string {
    const today = new Date();
    const date = new Date(`${isoDate}T00:00:00`);
    const diffDays = Math.round(
      (new Date(today.getFullYear(), today.getMonth(), today.getDate()).getTime() -
        date.getTime()) /
        86_400_000,
    );
    if (diffDays === 0) {
      return 'Heute';
    }
    if (diffDays === 1) {
      return 'Gestern';
    }
    return date.toLocaleDateString('de-CH', { weekday: 'short', day: '2-digit', month: '2-digit' });
  }

  private loadHistory(range: HistoryRange): void {
    this.energy
      .history(range)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((h) => {
        if (this.range() === range) {
          this.history.set(h);
        }
      });
  }

  private sum(
    pick: (b: { pvKwh: number; consumptionKwh: number; selfUseKwh: number }) => number,
  ): string {
    const buckets = this.history()?.buckets ?? [];
    return buckets.reduce((acc, b) => acc + pick(b), 0).toFixed(2);
  }

  protected watt(value: number): string {
    return `${Math.round(value)} W`;
  }
}
