import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  OnDestroy,
  effect,
  input,
  viewChild,
} from '@angular/core';
import { Chart, ChartConfiguration, registerables } from 'chart.js';
import { EnergyHistory } from '../../core/models/energy';

Chart.register(...registerables);

/** Farbwelt nach Fronius-Solar.web-Vorbild. */
export const ENERGY_COLORS = {
  production: '#f2a33c', // Erzeugung (orange)
  selfUse: '#f5d040', // Eigennutzung (gelb)
  consumption: '#8fb8d8', // Verbrauch (hellblau)
} as const;

const PRODUCTION_FILL = 'rgba(242, 163, 60, 0.75)';
const SELF_USE_FILL = 'rgba(245, 208, 64, 0.85)';
const GRID_COLOR = 'rgba(255, 255, 255, 0.10)';
const TICK_COLOR = 'rgba(159, 176, 195, 0.9)';

/**
 * Energie-Verlauf nach Fronius-Solar.web-Vorbild: die Tagesansicht als gefüllte
 * Leistungskurve (kW) aus den Roh-Messpunkten – Erzeugung orange, Eigennutzung gelb
 * (min(pv, verbrauch)), Verbrauch als hellblaue Linie. Woche/Monat als kWh-Balken je
 * Tag in derselben Farbwelt. {@code compact} rendert die Sparkline der
 * Dashboard-Kachel (ohne Achsen). Ohne 2D-Context (jsdom/Tests) wird nichts gezeichnet.
 */
@Component({
  selector: 'app-energy-history-chart',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div [style.height.px]="compact() ? 64 : 260">
      <canvas #canvas></canvas>
    </div>
  `,
})
export class EnergyHistoryChart implements OnDestroy {
  readonly history = input<EnergyHistory | null>(null);
  readonly compact = input<boolean>(false);

  private readonly canvasRef = viewChild<ElementRef<HTMLCanvasElement>>('canvas');
  private chart?: Chart;

  constructor() {
    effect(() => {
      const history = this.history();
      const ref = this.canvasRef();
      if (ref) {
        this.render(history, ref.nativeElement);
      }
    });
  }

  ngOnDestroy(): void {
    this.chart?.destroy();
  }

  private render(history: EnergyHistory | null, canvas: HTMLCanvasElement): void {
    const ctx = canvas.getContext('2d');
    if (!ctx) {
      return; // jsdom / Testumgebung ohne Canvas
    }
    this.chart?.destroy();
    if (!history) {
      return;
    }
    this.chart =
      history.range === 'day' && history.samples.length > 0
        ? this.dayCurve(ctx, history)
        : this.kwhBars(ctx, history);
  }

  /** Tagesansicht: Leistungskurve in kW aus den Roh-Messpunkten. */
  private dayCurve(ctx: CanvasRenderingContext2D, history: EnergyHistory): Chart {
    const compact = this.compact();
    const samples = history.samples;
    const labels = samples.map((s) => this.time(s.timestamp));
    const pvKw = samples.map((s) => s.pvWatt / 1000);
    const consKw = samples.map((s) => s.consumptionWatt / 1000);
    const selfUseKw = samples.map((s) => Math.min(s.pvWatt, s.consumptionWatt) / 1000);

    const config: ChartConfiguration<'line'> = {
      type: 'line',
      data: {
        labels,
        datasets: [
          {
            label: 'Erzeugung',
            data: pvKw,
            fill: 'origin',
            backgroundColor: PRODUCTION_FILL,
            borderColor: ENERGY_COLORS.production,
            borderWidth: 1,
            pointRadius: 0,
            tension: 0.2,
            order: 3,
          },
          {
            label: 'Eigennutzung',
            data: selfUseKw,
            fill: 'origin',
            backgroundColor: SELF_USE_FILL,
            borderColor: 'transparent',
            pointRadius: 0,
            tension: 0.2,
            order: 2,
          },
          {
            label: 'Verbrauch',
            data: consKw,
            fill: false,
            borderColor: ENERGY_COLORS.consumption,
            borderWidth: 2,
            pointRadius: 0,
            tension: 0.2,
            order: 1,
          },
        ],
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        animation: false,
        plugins: {
          legend: { display: false }, // eigene Legende mit kWh-Summen unter dem Chart
          tooltip: {
            enabled: !compact,
            callbacks: {
              label: (item) => `${item.dataset.label}: ${(item.raw as number).toFixed(2)} kW`,
            },
          },
        },
        scales: {
          x: {
            display: !compact,
            grid: { display: false },
            ticks: { color: TICK_COLOR, maxRotation: 0, autoSkip: true, maxTicksLimit: 5 },
          },
          y: {
            display: !compact,
            beginAtZero: true,
            grid: { color: GRID_COLOR },
            ticks: { color: TICK_COLOR },
            title: { display: !compact, text: 'kW', color: TICK_COLOR },
          },
        },
      },
    };
    return new Chart(ctx, config);
  }

  /** Woche/Monat: kWh-Balken je Tag (Erzeugung + Verbrauch). */
  private kwhBars(ctx: CanvasRenderingContext2D, history: EnergyHistory): Chart {
    const compact = this.compact();
    const labels = history.buckets.map((b) => this.dayLabel(history.range, b.start));

    const config: ChartConfiguration<'bar'> = {
      type: 'bar',
      data: {
        labels,
        datasets: [
          {
            label: 'Erzeugung',
            data: history.buckets.map((b) => b.pvKwh),
            backgroundColor: PRODUCTION_FILL,
            borderRadius: 3,
          },
          {
            label: 'Verbrauch',
            data: history.buckets.map((b) => b.consumptionKwh),
            backgroundColor: ENERGY_COLORS.consumption,
            borderRadius: 3,
          },
        ],
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        animation: false,
        plugins: {
          legend: { display: false },
          tooltip: {
            enabled: !compact,
            callbacks: {
              label: (item) => `${item.dataset.label}: ${(item.raw as number).toFixed(2)} kWh`,
            },
          },
        },
        scales: {
          x: {
            display: !compact,
            grid: { display: false },
            ticks: { color: TICK_COLOR, maxRotation: 0, autoSkip: true },
          },
          y: {
            display: !compact,
            beginAtZero: true,
            grid: { color: GRID_COLOR },
            ticks: { color: TICK_COLOR },
            title: { display: !compact, text: 'kWh', color: TICK_COLOR },
          },
        },
      },
    };
    return new Chart(ctx, config);
  }

  private time(iso: string): string {
    const d = new Date(iso);
    return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
  }

  private dayLabel(range: string, iso: string): string {
    const d = new Date(iso);
    if (range === 'week') {
      return d.toLocaleDateString('de-CH', { weekday: 'short' });
    }
    return `${d.getDate()}.${d.getMonth() + 1}.`;
  }
}
