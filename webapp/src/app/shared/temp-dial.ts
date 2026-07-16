import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

const RING_CIRC = 2 * Math.PI * 70;

/**
 * Präsentations-Komponente: Ring-Dial mit Soll-Temperatur in der Mitte.
 * Der Bogen zeigt die Position der Soll-Temperatur zwischen Min und Max.
 * Interaktion (+/-) liegt bewusst beim Eltern-Element.
 *
 * <p>{@code size="sm"} halbiert den Ring (120px statt 240px) und skaliert die
 * Schrift mit – für die Dashboard-Kachel, wo der Dial nur anzeigt.
 */
@Component({
  selector: 'app-temp-dial',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div
      class="relative mx-auto aspect-square w-full"
      [class]="compact() ? 'max-w-[120px]' : 'max-w-[240px]'"
    >
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
          <span
            class="font-medium uppercase tracking-wide text-slate-500"
            [class]="compact() ? 'text-[9px]' : 'text-[11px]'"
          >
            {{ label() }}
          </span>
        }
        @if (emphasis() === 'current') {
          <!-- Klima: Ist gross, Soll klein -->
          <span
            class="font-semibold tabular-nums text-slate-900"
            [class]="compact() ? 'text-xl' : 'text-4xl'"
          >
            {{ current() >= 0 ? current() + '°' : '–' }}
          </span>
          <span class="text-slate-500" [class]="compact() ? 'text-[10px]' : 'mt-0.5 text-xs'">
            Soll {{ target() }}°
          </span>
        } @else {
          <!-- Standard: Soll gross, Ist klein -->
          <span
            class="font-semibold tabular-nums text-slate-900"
            [class]="compact() ? 'text-xl' : 'text-4xl'"
          >
            {{ target() }}°
          </span>
          @if (current() >= 0) {
            <span class="text-slate-500" [class]="compact() ? 'text-[10px]' : 'mt-0.5 text-xs'">
              Ist {{ current() }}°
            </span>
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

  /** 'sm' = halber Ring für die Dashboard-Kachel; 'md' = volle Grösse (Detailseite). */
  readonly size = input<'sm' | 'md'>('md');

  protected readonly compact = computed(() => this.size() === 'sm');

  protected readonly ringCirc = RING_CIRC;

  protected readonly dashOffset = computed(() => {
    const span = this.max() - this.min();
    const fraction = span <= 0 ? 0 : Math.max(0, Math.min(1, (this.target() - this.min()) / span));
    return RING_CIRC * (1 - fraction);
  });
}
