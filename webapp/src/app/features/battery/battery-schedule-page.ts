import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { BatteryScheduleService } from '../../core/services/battery-schedule.service';
import {
  BatterySchedule,
  BatteryScheduleType,
  CreateBatteryScheduleRequest,
  RelayState,
  Weekday,
} from '../../core/models/battery-schedule';

const WEEKDAYS: { key: Weekday; label: string }[] = [
  { key: 'MONDAY', label: 'Mo' },
  { key: 'TUESDAY', label: 'Di' },
  { key: 'WEDNESDAY', label: 'Mi' },
  { key: 'THURSDAY', label: 'Do' },
  { key: 'FRIDAY', label: 'Fr' },
  { key: 'SATURDAY', label: 'Sa' },
  { key: 'SUNDAY', label: 'So' },
];

/**
 * Zeitsteuerung der Batterie (Use Case 14): Liste der Regeln + Anlegen je Typ
 * (Zeitplan/Countdown). Aktion schaltet das Lade-Relais EIN/AUS; die Ausführung
 * versetzt die Batterie dabei in den Manuell-Modus.
 */
@Component({
  selector: 'app-battery-schedule-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, RouterLink],
  template: `
    <section class="space-y-5">
      <div class="flex items-center gap-3">
        <a routerLink="/battery" class="rail-btn" aria-label="Zurück">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M15 18l-6-6 6-6" stroke-linecap="round" stroke-linejoin="round" />
          </svg>
        </a>
        <h2 class="text-2xl font-semibold">Zeitsteuerung Batterie</h2>
      </div>

      <p class="text-xs text-[color:var(--ink-faint)]">
        Eine Regel schaltet das Lade-Relais zur gewählten Zeit – die Batterie wechselt dabei in den
        Manuell-Modus.
      </p>

      <!-- Bestehende Regeln -->
      <div class="grid gap-3 sm:grid-cols-2">
        @for (s of schedules(); track s.id) {
          <article class="glass-card flex items-center justify-between gap-3 p-4">
            <div class="min-w-0">
              <p class="font-medium">{{ describe(s) }}</p>
              <p class="text-xs text-[color:var(--ink-faint)]">
                {{ s.type }} · Laden {{ s.action === 'ON' ? 'EIN' : 'AUS' }}
              </p>
            </div>
            <div class="flex shrink-0 items-center gap-2">
              <button
                type="button"
                class="seg px-3 py-1 text-xs"
                [attr.data-active]="s.enabled"
                (click)="toggle(s)"
              >
                {{ s.enabled ? 'aktiv' : 'aus' }}
              </button>
              <button
                type="button"
                class="rail-btn size-9"
                aria-label="Löschen"
                (click)="remove(s)"
              >
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
                  <path d="M4 7h16M9 7V5h6v2M6 7l1 13h10l1-13" stroke-linecap="round" />
                </svg>
              </button>
            </div>
          </article>
        } @empty {
          <p class="text-sm text-[color:var(--ink-soft)]">Noch keine Zeitsteuerung angelegt.</p>
        }
      </div>

      <!-- Neue Regel -->
      <article class="glass-card space-y-4 p-5">
        <h3 class="font-medium">Hinzufügen</h3>

        <div class="flex flex-wrap gap-2">
          @for (t of types; track t) {
            <button
              type="button"
              class="seg px-4 py-2 text-sm"
              [attr.data-active]="newType() === t"
              (click)="newType.set(t)"
            >
              {{ typeLabel(t) }}
            </button>
          }
        </div>

        <!-- Aktion: Relais EIN/AUS -->
        <div class="flex items-center gap-2 text-sm">
          <span class="text-[color:var(--ink-soft)]">Laden</span>
          <button
            type="button"
            class="seg px-3 py-1"
            [attr.data-active]="action() === 'ON'"
            (click)="action.set('ON')"
          >
            EIN
          </button>
          <button
            type="button"
            class="seg px-3 py-1"
            [attr.data-active]="action() === 'OFF'"
            (click)="action.set('OFF')"
          >
            AUS
          </button>
        </div>

        @switch (newType()) {
          @case ('SCHEDULE') {
            <div class="space-y-3">
              <label class="block text-sm">
                <span class="text-[color:var(--ink-soft)]">Uhrzeit</span>
                <input
                  type="time"
                  [(ngModel)]="time"
                  class="glass mt-1 block rounded-lg px-3 py-2"
                />
              </label>
              <div class="flex flex-wrap gap-1">
                @for (d of weekdays; track d.key) {
                  <button
                    type="button"
                    class="seg size-9 text-xs"
                    [attr.data-active]="selectedDays().has(d.key)"
                    (click)="toggleDay(d.key)"
                  >
                    {{ d.label }}
                  </button>
                }
              </div>
              <p class="text-xs text-[color:var(--ink-faint)]">Keine Auswahl = täglich.</p>
            </div>
          }
          @case ('COUNTDOWN') {
            <label class="block text-sm">
              <span class="text-[color:var(--ink-soft)]">In … Minuten schalten</span>
              <input
                type="number"
                min="1"
                [(ngModel)]="countdownMinutes"
                class="glass mt-1 block w-32 rounded-lg px-3 py-2"
              />
            </label>
          }
        }

        <button
          type="button"
          class="rounded-xl bg-[color:var(--accent)] px-5 py-2 text-sm font-semibold text-white"
          (click)="add()"
        >
          Anlegen
        </button>
      </article>
    </section>
  `,
})
export class BatterySchedulePage {
  private readonly api = inject(BatteryScheduleService);

  protected readonly types: BatteryScheduleType[] = ['SCHEDULE', 'COUNTDOWN'];
  protected readonly weekdays = WEEKDAYS;

  protected readonly schedules = signal<BatterySchedule[]>([]);
  protected readonly newType = signal<BatteryScheduleType>('SCHEDULE');
  protected readonly action = signal<RelayState>('ON');
  protected readonly selectedDays = signal<Set<Weekday>>(new Set());

  // Formularfelder (Template-driven).
  protected time = '22:00';
  protected countdownMinutes = 60;

  constructor() {
    this.reload();
  }

  protected typeLabel(t: BatteryScheduleType): string {
    return { SCHEDULE: 'Zeitplan', COUNTDOWN: 'Countdown' }[t];
  }

  protected describe(s: BatterySchedule): string {
    if (s.type === 'SCHEDULE') {
      const days = s.weekdays.length ? s.weekdays.join(', ') : 'Täglich';
      return `${s.time} · ${days}`;
    }
    return `Einmalig um ${this.hhmm(s.fireAt)}`;
  }

  protected toggleDay(d: Weekday): void {
    const next = new Set(this.selectedDays());
    if (next.has(d)) {
      next.delete(d);
    } else {
      next.add(d);
    }
    this.selectedDays.set(next);
  }

  protected add(): void {
    const t = this.newType();
    const req: CreateBatteryScheduleRequest = { type: t, action: this.action() };
    if (t === 'SCHEDULE') {
      req.time = this.time;
      req.weekdays = [...this.selectedDays()];
    } else {
      req.countdownSeconds = this.countdownMinutes * 60;
    }
    this.api.create(req).subscribe(() => this.reload());
  }

  protected toggle(s: BatterySchedule): void {
    this.api.setEnabled(s.id, !s.enabled).subscribe(() => this.reload());
  }

  protected remove(s: BatterySchedule): void {
    this.api.delete(s.id).subscribe(() => this.reload());
  }

  private reload(): void {
    this.api.all().subscribe((list) => this.schedules.set(list));
  }

  private hhmm(iso: string | null): string {
    if (!iso) {
      return '';
    }
    const d = new Date(iso);
    return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
  }
}
