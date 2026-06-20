import { ChangeDetectionStrategy, Component, input, model } from '@angular/core';

/**
 * Einheitlicher Ein/Aus-Button (Power-Orb) für das ganze UI.
 *
 * Verwendung (zwei-Wege):
 *   <app-power-toggle [(on)]="lampAn" label="Hauptlampe" />
 * oder gesteuert mit Output:
 *   <app-power-toggle [on]="state()" (onChange)="toggle($event)" />
 */
@Component({
  selector: 'app-power-toggle',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <button
      type="button"
      class="power-orb"
      [class.is-on]="on()"
      [class.power-orb-sm]="size() === 'sm'"
      [class.power-orb-lg]="size() === 'lg'"
      [attr.aria-pressed]="on()"
      [attr.aria-label]="label()"
      [disabled]="disabled()"
      (click)="toggle()"
    >
      <svg
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        stroke-width="1.9"
        aria-hidden="true"
      >
        <path d="M12 4v8" stroke-linecap="round" />
        <path d="M7.6 7.6a6.5 6.5 0 1 0 8.8 0" stroke-linecap="round" />
      </svg>
    </button>
  `,
})
export class PowerToggle {
  /** Zustand (zwei-Wege via [(on)]). */
  readonly on = model(false);

  /** Grösse: 'sm' (dicht), 'md' (Standard) oder 'lg' (Touch/Dashboard). */
  readonly size = input<'sm' | 'md' | 'lg'>('md');

  /** Barrierefreie Beschriftung des Buttons. */
  readonly label = input('Ein/Aus');

  /** Deaktiviert den Button (z. B. im Automatik-Modus). */
  readonly disabled = input(false);

  protected toggle(): void {
    if (this.disabled()) {
      return;
    }
    this.on.set(!this.on());
  }
}
