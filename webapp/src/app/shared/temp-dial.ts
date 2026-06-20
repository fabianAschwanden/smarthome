import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

const RING_CIRC = 2 * Math.PI * 70;

/**
 * Präsentations-Komponente: Ring-Dial mit Soll-Temperatur in der Mitte.
 * Der Bogen zeigt die Position der Soll-Temperatur zwischen Min und Max.
 * Interaktion (+/-) liegt bewusst beim Eltern-Element.
 */
@Component({
  selector: 'app-temp-dial',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="relative mx-auto aspect-square w-full max-w-[240px]">
      <svg viewBox="0 0 180 180" class="h-full w-full">
        <circle
          cx="90"
          cy="90"
          r="70"
          fill="none"
          stroke="rgba(255,255,255,0.15)"
          stroke-width="14"
        />
        <circle
          cx="90"
          cy="90"
          r="70"
          fill="none"
          stroke="var(--accent)"
          stroke-width="14"
          stroke-linecap="round"
          transform="rotate(-90 90 90)"
          [attr.stroke-dasharray]="ringCirc"
          [attr.stroke-dashoffset]="dashOffset()"
        />
        <circle cx="90" cy="90" r="55" fill="rgba(255,255,255,0.92)" />
      </svg>
      <div class="absolute inset-0 flex flex-col items-center justify-center text-center">
        @if (label()) {
          <span class="text-[11px] font-medium uppercase tracking-wide text-slate-500">
            {{ label() }}
          </span>
        }
        @if (emphasis() === 'current') {
          <!-- Klima: Ist gross, Soll klein -->
          <span class="text-4xl font-semibold tabular-nums text-slate-900">
            {{ current() >= 0 ? current() + '°' : '–' }}
          </span>
          <span class="mt-0.5 text-xs text-slate-500">Soll {{ target() }}°</span>
        } @else {
          <!-- Standard: Soll gross, Ist klein -->
          <span class="text-4xl font-semibold tabular-nums text-slate-900">{{ target() }}°</span>
          @if (current() >= 0) {
            <span class="mt-0.5 text-xs text-slate-500">Ist {{ current() }}°</span>
          }
        }
      </div>
    </div>
  `,
})
export class TempDial {
  readonly target = input.required<number>();
  readonly current = input(-1);
  readonly min = input(16);
  readonly max = input(30);
  readonly label = input('');

  /** Was steht gross in der Mitte: 'target' (Soll, Standard) oder 'current' (Ist). */
  readonly emphasis = input<'target' | 'current'>('target');

  protected readonly ringCirc = RING_CIRC;

  protected readonly dashOffset = computed(() => {
    const span = this.max() - this.min();
    const fraction = span <= 0 ? 0 : Math.max(0, Math.min(1, (this.target() - this.min()) / span));
    return RING_CIRC * (1 - fraction);
  });
}
