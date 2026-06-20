import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ScheduleService } from '../../core/services/schedule.service';
import {
  CreateScheduleRequest,
  Schedule,
  ScheduleType,
  SwitchState,
  Weekday,
} from '../../core/models/schedule';

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
 * Zeitsteuerung eines Schalters (vgl. Smart-Life-App): Liste der Regeln plus
 * Anlegen je Typ (Schedule/Countdown/Random/Inching). Glass-Design, responsive.
 */
@Component({
  selector: 'app-schedule-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, RouterLink],
  template: `
    <section class="space-y-5">
      <div class="flex items-center gap-3">
        <a routerLink="/switch" class="rail-btn" aria-label="Zurück">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M15 18l-6-6 6-6" stroke-linecap="round" stroke-linejoin="round" />
          </svg>
        </a>
        <h2 class="text-2xl font-semibold">Zeitsteuerung</h2>
        <span class="chip">{{ switchId() }}</span>
      </div>

      <!-- Bestehende Regeln -->
      <div class="grid gap-3 sm:grid-cols-2">
        @for (s of schedules(); track s.id) {
          <article class="glass-card flex items-center justify-between gap-3 p-4">
            <div class="min-w-0">
              <p class="font-medium">{{ describe(s) }}</p>
              <p class="text-xs text-[color:var(--ink-faint)]">
                {{ s.type }} · Schalter {{ s.action }}
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

        <!-- Aktion (für Schedule/Countdown/Random) -->
        @if (newType() !== 'INCHING') {
          <div class="flex items-center gap-2 text-sm">
            <span class="text-[color:var(--ink-soft)]">Aktion</span>
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
        }

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
          @case ('RANDOM') {
            <div class="flex flex-wrap gap-4">
              <label class="block text-sm">
                <span class="text-[color:var(--ink-soft)]">Von</span>
                <input
                  type="time"
                  [(ngModel)]="windowStart"
                  class="glass mt-1 block rounded-lg px-3 py-2"
                />
              </label>
              <label class="block text-sm">
                <span class="text-[color:var(--ink-soft)]">Bis</span>
                <input
                  type="time"
                  [(ngModel)]="windowEnd"
                  class="glass mt-1 block rounded-lg px-3 py-2"
                />
              </label>
            </div>
          }
          @case ('INCHING') {
            <label class="block text-sm">
              <span class="text-[color:var(--ink-soft)]">EIN, dann AUS nach … Sekunden</span>
              <input
                type="number"
                min="1"
                [(ngModel)]="pulseSeconds"
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
export class SchedulePage {
  private readonly route = inject(ActivatedRoute);
  private readonly api = inject(ScheduleService);

  protected readonly types: ScheduleType[] = ['SCHEDULE', 'COUNTDOWN', 'RANDOM', 'INCHING'];
  protected readonly weekdays = WEEKDAYS;

  protected readonly switchId = signal(this.route.snapshot.paramMap.get('id') ?? '');
  protected readonly schedules = signal<Schedule[]>([]);

  protected readonly newType = signal<ScheduleType>('SCHEDULE');
  protected readonly action = signal<SwitchState>('ON');
  protected readonly selectedDays = signal<Set<Weekday>>(new Set());

  // Formularfelder (Template-driven).
  protected time = '07:00';
  protected countdownMinutes = 30;
  protected windowStart = '18:00';
  protected windowEnd = '23:00';
  protected pulseSeconds = 60;

  protected readonly isInching = computed(() => this.newType() === 'INCHING');

  constructor() {
    this.reload();
  }

  protected typeLabel(t: ScheduleType): string {
    return { SCHEDULE: 'Zeitplan', COUNTDOWN: 'Countdown', RANDOM: 'Zufall', INCHING: 'Impuls' }[t];
  }

  protected describe(s: Schedule): string {
    switch (s.type) {
      case 'SCHEDULE': {
        const days = s.weekdays.length ? s.weekdays.join(', ') : 'Täglich';
        return `${s.time} · ${days}`;
      }
      case 'COUNTDOWN':
        return `Einmalig um ${this.hhmm(s.fireAt)}`;
      case 'RANDOM':
        return `Zufällig ${s.windowStart}–${s.windowEnd}`;
      case 'INCHING':
        return `Impuls ${s.pulseSeconds}s`;
    }
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
    const req: CreateScheduleRequest = {
      switchId: this.switchId(),
      type: t,
      action: this.action(),
    };
    if (t === 'SCHEDULE') {
      req.time = this.time;
      req.weekdays = [...this.selectedDays()];
    } else if (t === 'COUNTDOWN') {
      req.countdownSeconds = this.countdownMinutes * 60;
    } else if (t === 'RANDOM') {
      req.windowStart = this.windowStart;
      req.windowEnd = this.windowEnd;
    } else if (t === 'INCHING') {
      req.pulseSeconds = this.pulseSeconds;
    }
    this.api.create(req).subscribe(() => this.reload());
  }

  protected toggle(s: Schedule): void {
    this.api.setEnabled(s.id, !s.enabled).subscribe(() => this.reload());
  }

  protected remove(s: Schedule): void {
    this.api.delete(s.id).subscribe(() => this.reload());
  }

  private reload(): void {
    this.api.forSwitch(this.switchId()).subscribe((list) => this.schedules.set(list));
  }

  private hhmm(iso: string | null): string {
    if (!iso) {
      return '';
    }
    const d = new Date(iso);
    return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
  }
}
