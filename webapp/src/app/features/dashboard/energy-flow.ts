import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { EnergyHistory, PowerReading } from '../../core/models/energy';
import { PowerToggle } from '../../shared/power-toggle';
import { ENERGY_COLORS, EnergyHistoryChart } from '../energy/energy-history-chart';

/**
 * Energiefluss-Darstellung nach dem Muster der Fronius-App: PV oben (Ring mit
 * Auslastung), Wechselrichter-Logo in der Mitte, Netz links und Haus rechts –
 * darunter der Tagesverlauf in Fronius-Solar.web-Optik (Leistungskurve wie auf
 * der Detailseite) mit kWh-Summen für Erzeugung, Eigennutzung und Verbrauch.
 */
@Component({
  selector: 'app-energy-flow',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [PowerToggle, EnergyHistoryChart],
  template: `
    @if (reading(); as r) {
      <div class="glass-card h-full space-y-3 p-4">
        <!-- Energiefluss-Diagramm -->
        <div class="flow">
          <!-- PV oben -->
          <div class="flow-pv">
            <p class="flow-value">{{ kw(r.pvWatt) }}</p>
            <div class="flow-node flow-node-pv">
              <svg class="flow-ring" viewBox="0 0 100 100" aria-hidden="true">
                <circle class="flow-ring-bg" cx="50" cy="50" r="44" />
                <circle
                  class="flow-ring-fill"
                  cx="50"
                  cy="50"
                  r="44"
                  pathLength="100"
                  [attr.stroke-dasharray]="pvFill() + ' 100'"
                />
              </svg>
              <svg
                class="flow-icon flow-icon-sun"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="1.8"
                aria-hidden="true"
              >
                <circle cx="12" cy="12" r="4" />
                <path
                  d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4"
                  stroke-linecap="round"
                />
              </svg>
            </div>
          </div>

          <!-- Verbindungslinien -->
          <svg
            class="flow-lines"
            viewBox="0 0 300 90"
            preserveAspectRatio="none"
            aria-hidden="true"
          >
            <path d="M150 0 V40" />
            <path d="M150 45 L55 85" />
            <path d="M150 45 L245 85" />
          </svg>

          <!-- Wechselrichter Mitte -->
          <div class="flow-inverter">
            <img src="/fronius-logo.svg" alt="Fronius" width="44" height="44" />
          </div>

          <!-- Netz + Haus -->
          <div class="flow-bottom">
            <div class="flow-leg">
              <div class="flow-node flow-node-grid">
                <svg
                  class="flow-icon"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="1.6"
                  aria-hidden="true"
                >
                  <path
                    d="M7 22l2-9M17 22l-2-9M9 13h6M6 22h12M12 2v4M9 6l-3 7M15 6l3 7M9 6h6"
                    stroke-linecap="round"
                    stroke-linejoin="round"
                  />
                </svg>
              </div>
              <p class="flow-value flow-value-sm">{{ wattOrKw(absGrid(r)) }}</p>
              <p class="flow-leg-label">{{ r.gridWatt >= 0 ? 'Bezug' : 'Einspeisung' }}</p>
            </div>
            <div class="flow-leg">
              <div class="flow-node flow-node-home">
                <svg
                  class="flow-icon"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="1.6"
                  aria-hidden="true"
                >
                  <path
                    d="M4 11l8-7 8 7M6 10v9h12v-9M10 19v-5h4v5"
                    stroke-linecap="round"
                    stroke-linejoin="round"
                  />
                </svg>
              </div>
              <p class="flow-value flow-value-sm">{{ kw(r.consumptionWatt) }}</p>
              <p class="flow-leg-label">Haus</p>
            </div>
          </div>
        </div>

        <!-- Batterie: ein+lädt / ein, lädt nicht / aus -->
        @if (batteryStatus() !== 'unknown') {
          <div class="flex items-center justify-between border-t border-white/10 pt-3">
            <span class="flex items-center gap-2 text-sm text-[color:var(--ink-soft)]">
              <!-- Batterie-Icon; lädt = animierter Blitz/Puls + grün, aus = ausgegraut -->
              <span
                class="battery"
                [class.battery-charging]="batteryStatus() === 'charging'"
                [class.battery-off]="batteryStatus() === 'off'"
              >
                <svg
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="1.7"
                  aria-hidden="true"
                >
                  <rect x="3" y="8" width="16" height="9" rx="2" />
                  <path d="M21 11v3" stroke-linecap="round" />
                  @if (batteryStatus() === 'charging') {
                    <path
                      class="battery-bolt"
                      d="M12 9.5l-2 3h2.4l-1.4 3 3.6-4h-2.2z"
                      fill="currentColor"
                      stroke="none"
                    />
                  }
                </svg>
              </span>
              Batterie
            </span>
            <span class="flex items-center gap-3">
              <span class="text-sm font-medium" [class]="batteryClass()">{{ batteryLabel() }}</span>
              <app-power-toggle
                [on]="batteryStatus() === 'charging'"
                size="sm"
                label="Batterieladung ein/aus"
                (onChange)="batteryToggle.emit($event)"
                (click)="$event.stopPropagation()"
              />
            </span>
          </div>
        }

        <!-- Energie-Verlauf heute (Fronius-Optik, wie die Detailseite): Leistungskurve
             mit Achsen + kWh-Zeilen. Nicht-interaktiv, damit Tap die Detailseite öffnet. -->
        @if (dayHistory(); as dh) {
          <div class="space-y-2 border-t border-white/10 pt-3">
            <div class="flex items-baseline justify-between">
              <h4 class="font-medium">Energie</h4>
              <span class="text-xs text-[color:var(--ink-faint)]">Heute</span>
            </div>
            <div class="pointer-events-none">
              <app-energy-history-chart [history]="dh" [height]="170" />
            </div>
            <div class="space-y-1.5 pt-1">
              <div class="flex items-center justify-between">
                <span class="flex items-center gap-2 text-sm text-[color:var(--ink-soft)]">
                  <span class="size-2 rounded-full" [style.background]="colors.production"></span>
                  Erzeugung
                </span>
                <span class="font-semibold tabular-nums"
                  >{{ totalPv() }} <span class="text-xs font-normal">kWh</span></span
                >
              </div>
              <div class="flex items-center justify-between">
                <span class="flex items-center gap-2 text-sm text-[color:var(--ink-soft)]">
                  <span class="size-2 rounded-full" [style.background]="colors.selfUse"></span>
                  Eigennutzung
                </span>
                <span class="font-semibold tabular-nums"
                  >{{ totalSelfUse() }} <span class="text-xs font-normal">kWh</span></span
                >
              </div>
              <div class="flex items-center justify-between">
                <span class="flex items-center gap-2 text-sm text-[color:var(--ink-soft)]">
                  <span class="size-2 rounded-full" [style.background]="colors.consumption"></span>
                  Verbrauch
                </span>
                <span class="font-semibold tabular-nums"
                  >{{ totalConsumption() }} <span class="text-xs font-normal">kWh</span></span
                >
              </div>
            </div>
          </div>
        }
      </div>
    } @else {
      <div class="glass-card p-5">
        <p class="text-sm text-[color:var(--ink-soft)]">Lade Energiedaten …</p>
      </div>
    }
  `,
})
export class EnergyFlow {
  /** Aktuelles Fronius-Reading (oder undefined, solange nichts geladen). */
  readonly reading = input<PowerReading | undefined>();

  /**
   * Batterie-Zustand: 'charging' (eingeschaltet -> lädt), 'off' (ausgeschaltet),
   * 'unknown' (kein Status -> Zeile aus). Die Batterie wird nur per Taster
   * geschaltet, daher kein Lade-Watt-Schwellwert.
   */
  readonly batteryStatus = input<'charging' | 'off' | 'unknown'>('unknown');

  /** Batterie-Schalter betätigt (true = ein). Das Schalten selbst macht die Seite. */
  readonly batteryToggle = output<boolean>();

  /** Tagesverlauf für Leistungskurve + kWh-Summen; null solange nicht geladen. */
  readonly dayHistory = input<EnergyHistory | null>(null);

  protected readonly colors = ENERGY_COLORS;

  protected readonly totalPv = computed(() => this.sumKwh((b) => b.pvKwh));
  protected readonly totalSelfUse = computed(() => this.sumKwh((b) => b.selfUseKwh));
  protected readonly totalConsumption = computed(() => this.sumKwh((b) => b.consumptionKwh));

  /** PV-Ring-Füllung 0–100 %: skaliert auf eine angenommene Spitzenleistung. */
  protected readonly pvFill = computed(() => {
    const pv = this.reading()?.pvWatt ?? 0;
    const peak = 8000; // ~Anlagen-Peak in W; nur für die Ring-Optik
    return Math.max(0, Math.min(100, (pv / peak) * 100));
  });

  protected absGrid(r: PowerReading): number {
    return Math.abs(r.gridWatt);
  }

  protected batteryLabel(): string {
    switch (this.batteryStatus()) {
      case 'charging':
        return 'lädt';
      case 'off':
        return 'aus';
      default:
        return '';
    }
  }

  protected batteryClass(): string {
    switch (this.batteryStatus()) {
      case 'charging':
        return 'text-emerald-300';
      case 'off':
        return 'text-[color:var(--ink-faint)]';
      default:
        return 'text-[color:var(--ink-soft)]';
    }
  }

  protected kw(watt: number): string {
    return watt >= 1000 ? `${(watt / 1000).toFixed(2)} kW` : `${Math.round(watt)} W`;
  }

  /** Wie kw(), aber unter 1 kW in W (für Netz, wie im Vorbild „548 W"). */
  protected wattOrKw(watt: number): string {
    return this.kw(watt);
  }

  private sumKwh(
    pick: (b: { pvKwh: number; consumptionKwh: number; selfUseKwh: number }) => number,
  ): string {
    const buckets = this.dayHistory()?.buckets ?? [];
    return buckets.reduce((acc, b) => acc + pick(b), 0).toFixed(2);
  }
}
