import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TuyaService } from '../../core/services/tuya.service';
import { TuyaSwitch } from '../../core/models/tuya';
import { PowerToggle } from '../../shared/power-toggle';
import { ItemImage } from '../../shared/item-image';

/**
 * Use Case 3: Tuya/Smart-Life-Schalter (siehe docs/tuya/SPEC.md).
 * Liste aller Geräte; jedes EIN/AUS schalten und Zustand anzeigen.
 */
@Component({
  selector: 'app-switch-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, PowerToggle, ItemImage],
  template: `
    <section class="space-y-5">
      <h2 class="text-2xl font-semibold">Schalter</h2>

      @if (switches(); as list) {
        @if (list.length === 0) {
          <p class="text-[color:var(--ink-soft)]">Keine Geräte konfiguriert.</p>
        }
        <div class="grid gap-4 sm:grid-cols-2">
          @for (s of list; track s.id) {
            <article class="glass-card flex gap-4 p-5">
              <div class="w-24 shrink-0 sm:w-28">
                <app-item-image [itemId]="s.id" [label]="s.name" />
              </div>
              <div class="flex min-w-0 flex-1 flex-col gap-3">
                <header class="flex items-start justify-between gap-3">
                  <div>
                    <h3 class="font-medium">{{ s.name }}</h3>
                    <p class="mt-0.5 text-xs text-[color:var(--ink-soft)]">
                      {{ !s.online ? 'Offline' : s.state === 'ON' ? 'Ein' : 'Aus' }}
                      @if (s.room) {
                        · {{ s.room }}
                      }
                    </p>
                  </div>
                  <app-power-toggle
                    [on]="s.state === 'ON'"
                    [disabled]="!s.online"
                    [label]="s.name"
                    (onChange)="onSwitch(s, $event)"
                  />
                </header>

                @if (s.critical) {
                  <p class="flex items-center gap-1.5 text-xs text-amber-300/90">
                    ⚠ Kritisch – AUS versorgt auch das WLAN; vor dem Ausschalten kommt eine
                    Rückfrage.
                  </p>
                }
                @if (!s.online) {
                  <p class="text-xs text-amber-300/90">
                    ⚠ Nicht erreichbar – local-key/IP prüfen (docs/tuya/SPEC.md).
                  </p>
                }
                @if (s.hint) {
                  <p class="flex items-center gap-1.5 text-xs text-[color:var(--ink-soft)]">
                    <svg
                      class="size-3.5 shrink-0"
                      viewBox="0 0 24 24"
                      fill="none"
                      stroke="currentColor"
                      stroke-width="1.8"
                    >
                      <circle cx="12" cy="12" r="9" />
                      <path d="M12 11v5M12 8h.01" stroke-linecap="round" />
                    </svg>
                    {{ s.hint }}
                  </p>
                }
                <a
                  [routerLink]="['/switch', s.id, 'schedule']"
                  class="inline-flex items-center gap-1.5 text-sm text-[color:var(--ink-soft)] hover:text-[color:var(--ink)]"
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
                  Zeitsteuerung
                </a>
              </div>
            </article>
          }
        </div>
      } @else {
        <p class="text-[color:var(--ink-soft)]">Lade Schalter …</p>
      }
    </section>
  `,
})
export class SwitchPage {
  private readonly tuya = inject(TuyaService);

  protected readonly switches = this.tuya.switches;

  protected onSwitch(sw: TuyaSwitch, on: boolean): void {
    const state: 'ON' | 'OFF' = on ? 'ON' : 'OFF';
    // Kritische Schalter (z. B. Homecinema = WLAN) vor dem AUS bestätigen lassen.
    if (sw.critical && state === 'OFF') {
      const ok = confirm(
        `„${sw.name}" ist kritisch und versorgt auch das WLAN.\n` +
          'Nach dem Ausschalten ist KEINE Steuerung mehr möglich.\n\nWirklich ausschalten?',
      );
      if (!ok) {
        return;
      }
      this.tuya.switchTo(sw.id, state, true);
      return;
    }
    this.tuya.switchTo(sw.id, state);
  }
}
