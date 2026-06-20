import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

/**
 * Kompakte Lamellenstoren-Visualisierung fürs Dashboard.
 *
 * Eingabe {@code closed} = „% geschlossen" (0 = offen, 100 = ganz zu):
 * - 100 % → Behang ganz unten, Lamellen flach (dicht geschlossen)
 * - dazwischen (z. B. fast zu) → Behang fast unten, Lamellen leicht angestellt (Spalt)
 * - 0 % → Behang oben, Fenster frei
 *
 *   <app-blind-mini [closed]="80" />
 */
@Component({
  selector: 'app-blind-mini',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="blind-mini" [attr.aria-label]="closed() + '% geschlossen'">
      <!-- Behang: Höhe = wie weit heruntergefahren -->
      <div class="blind-mini-drop" [style.height.%]="closed()">
        @for (slat of slats(); track slat) {
          <div class="blind-mini-slat" [style.transform]="'rotateX(' + tilt() + 'deg)'"></div>
        }
      </div>
    </div>
  `,
})
export class BlindMini {
  /** % geschlossen: 0 = offen, 100 = ganz zu. */
  readonly closed = input(0);

  /** Feste Lamellenzahl im sichtbaren Behang. */
  protected readonly slats = computed(() => Array.from({ length: 8 }, (_, i) => i));

  /**
   * Anstellwinkel der Lamellen: ganz zu (100 %) = 0° (flach/dicht), sonst zunehmend
   * geöffnet (mehr Spalt). Bei „fast zu" also leicht angestellt.
   */
  protected readonly tilt = computed(() => {
    const c = Math.max(0, Math.min(100, this.closed()));
    return Math.round((100 - c) * 0.62); // 100%→0°, fast zu→~6°, offen→62°
  });
}
