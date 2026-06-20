import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { NotificationCenterService } from '../core/services/notification-center.service';
import { AppNotification } from '../core/models/notification';

/**
 * Glocke oben rechts = Einstieg in die Nachrichtenzentrale. Zeigt ein Zähler-Badge;
 * bei einem aktiven Alarm leuchtet/pulsiert sie rot. Klick öffnet ein Panel mit der
 * Meldungsliste. Daten kommen reaktiv aus {@link NotificationCenterService}.
 */
@Component({
  selector: 'app-notification-bell',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="relative">
      <button
        type="button"
        class="glass relative flex size-11 items-center justify-center rounded-full"
        [class.bell-alarm]="hasAlarm()"
        [attr.aria-label]="'Nachrichten (' + count() + ')'"
        (click)="toggle()"
      >
        <svg
          class="size-5 opacity-80"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="1.8"
        >
          <path d="M18 8a6 6 0 1 0-12 0c0 7-3 9-3 9h18s-3-2-3-9" stroke-linejoin="round" />
          <path d="M10.3 21a1.94 1.94 0 0 0 3.4 0" stroke-linecap="round" />
        </svg>
        @if (count() > 0) {
          <span class="bell-badge" [class.bell-badge-alarm]="hasAlarm()">{{ count() }}</span>
        }
      </button>

      @if (open()) {
        <!-- Klick ausserhalb schliesst das Panel -->
        <div class="fixed inset-0 z-30" (click)="open.set(false)"></div>
        <div class="notif-panel glass-panel z-40">
          <header class="flex items-center justify-between px-4 py-3">
            <h3 class="font-semibold">Nachrichten</h3>
            <span class="text-xs text-[color:var(--ink-soft)]">{{ count() }}</span>
          </header>
          <div class="max-h-[60vh] overflow-y-auto">
            @for (n of notifications(); track n.id) {
              <div class="notif-item" [class.notif-item-alarm]="n.severity === 'alarm'">
                <span class="notif-icon">{{ icon(n) }}</span>
                <div class="min-w-0">
                  <p class="truncate text-sm font-medium">{{ n.title }}</p>
                  <p class="text-xs text-[color:var(--ink-soft)]">{{ n.detail }}</p>
                </div>
              </div>
            } @empty {
              <p class="px-4 py-6 text-center text-sm text-[color:var(--ink-soft)]">
                Keine Meldungen – alles in Ordnung.
              </p>
            }
          </div>
        </div>
      }
    </div>
  `,
})
export class NotificationBell {
  private readonly center = inject(NotificationCenterService);

  protected readonly notifications = this.center.notifications;
  protected readonly count = this.center.count;
  protected readonly hasAlarm = this.center.hasAlarm;
  protected readonly open = signal(false);

  protected toggle(): void {
    this.open.update((v) => !v);
  }

  protected icon(n: AppNotification): string {
    if (n.severity === 'alarm') {
      return '🔥';
    }
    switch (n.source) {
      case 'battery':
        return '🔋';
      case 'smoke':
        return '🛡';
      case 'cover':
        return '🪟';
      case 'sensor':
        return '🌡';
      case 'climate':
        return '❄️';
      default:
        return '⚠';
    }
  }
}
