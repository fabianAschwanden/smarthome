import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { EnergyService } from '../../core/services/energy.service';
import { PowerReading } from '../../core/models/energy';
import { ItemImage } from '../../shared/item-image';

/**
 * Use Case 1: aktuellen Energieverbrauch visualisieren – stellt Fronius und
 * SMARTFOX nebeneinander dar und hebt die Differenz hervor (siehe docs/energy/SPEC.md §5).
 */
@Component({
  selector: 'app-energy-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ItemImage],
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
    </section>
  `,
})
export class EnergyPage {
  private readonly energy = inject(EnergyService);

  protected readonly snapshot = this.energy.snapshot;

  /** Referenzquelle für die Hero-Kennzahl: bevorzugt SMARTFOX (Netzreferenz). */
  protected readonly primary = computed<PowerReading | undefined>(() => {
    const readings = this.snapshot()?.readings ?? [];
    return readings.find((r) => r.source === 'SMARTFOX') ?? readings[0];
  });

  protected watt(value: number): string {
    return `${Math.round(value)} W`;
  }
}
