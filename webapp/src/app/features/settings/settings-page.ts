import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  signal,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AlertSettingsService } from '../../core/services/alert-settings.service';
import { BackupService } from '../../core/services/backup.service';
import { PowerToggle } from '../../shared/power-toggle';

/**
 * Einstellungen. Aktuell: Alerts (Push bei kritischem Alarm wie Rauchalarm) über
 * ntfy.sh ein-/ausschalten und den Topic festlegen, plus Test-Benachrichtigung.
 */
@Component({
  selector: 'app-settings-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, PowerToggle],
  template: `
    <section class="space-y-5">
      <h2 class="text-2xl font-semibold">Einstellungen</h2>

      @if (loaded()) {
        <article class="glass-card space-y-5 p-6">
          <!-- Kopf: Titel + Ein/Aus -->
          <header class="flex items-start justify-between gap-3">
            <div>
              <h3 class="text-lg font-semibold">Alarm-Benachrichtigung</h3>
              <p class="mt-0.5 text-sm text-[color:var(--ink-soft)]">
                Sendet bei einem kritischen Alarm (z. B. Rauchalarm) eine Push-Nachricht aufs Handy.
              </p>
            </div>
            <app-power-toggle
              [on]="enabled()"
              label="Alerts aktivieren"
              (onChange)="enabled.set($event)"
            />
          </header>

          <!-- ntfy-Topic -->
          <div>
            <label for="topic" class="mb-1.5 block text-sm text-[color:var(--ink-soft)]">
              ntfy-Topic
            </label>
            <input
              id="topic"
              type="text"
              [(ngModel)]="topic"
              [disabled]="!enabled()"
              placeholder="z. B. smarthome-meinhaus-7Kf3"
              autocomplete="off"
              autocapitalize="off"
              spellcheck="false"
              class="glass w-full rounded-xl px-4 py-2.5 text-sm outline-none placeholder:text-[color:var(--ink-faint)] disabled:opacity-40"
            />
            <p class="mt-1.5 text-xs text-[color:var(--ink-faint)]">
              Wähle einen langen, schwer erratbaren Namen – wer den Topic kennt, sieht die
              Meldungen. Auf dem Handy die <strong>ntfy</strong>-App installieren und genau diesen
              Topic abonnieren.
            </p>
          </div>

          <!-- Aktionen -->
          <div class="flex flex-wrap items-center gap-3">
            <button
              type="button"
              class="rounded-xl bg-[color:var(--accent)] px-5 py-2.5 text-sm font-medium text-white disabled:opacity-40"
              [disabled]="!dirty() || saving()"
              (click)="save()"
            >
              {{ saving() ? 'Speichere …' : 'Speichern' }}
            </button>
            <button
              type="button"
              class="glass rounded-xl px-5 py-2.5 text-sm disabled:opacity-40"
              [disabled]="!enabled() || !topic().trim() || dirty() || testing()"
              (click)="test()"
            >
              {{ testing() ? 'Sende …' : 'Test senden' }}
            </button>
            @if (message(); as m) {
              <span class="text-sm" [class.text-emerald-300]="ok()" [class.text-amber-300]="!ok()">
                {{ m }}
              </span>
            }
          </div>

          @if (dirty()) {
            <p class="text-xs text-[color:var(--ink-faint)]">
              Ungespeicherte Änderungen – zum Testen erst speichern.
            </p>
          }
        </article>
      } @else {
        <p class="text-[color:var(--ink-soft)]">Lade Einstellungen …</p>
      }

      <!-- Backup: Nutzerdaten sichern/wiederherstellen -->
      <article class="glass-card space-y-5 p-6">
        <header>
          <h3 class="text-lg font-semibold">Backup</h3>
          <p class="mt-0.5 text-sm text-[color:var(--ink-soft)]">
            Sichert Zeitpläne, Alarm-Einstellungen und Gerätebilder als JSON-Datei. Der
            Energie-Verlauf ist nicht enthalten – er wird laufend neu aufgezeichnet.
          </p>
        </header>
        <div class="flex flex-wrap items-center gap-3">
          <button
            type="button"
            class="rounded-xl bg-[color:var(--accent)] px-5 py-2.5 text-sm font-medium text-white disabled:opacity-40"
            [disabled]="backupBusy()"
            (click)="downloadBackup()"
          >
            {{ backupBusy() ? 'Arbeite …' : 'Backup herunterladen' }}
          </button>
          <label
            class="glass cursor-pointer rounded-xl px-5 py-2.5 text-sm"
            [class.opacity-40]="backupBusy()"
          >
            Backup wiederherstellen …
            <input
              type="file"
              accept="application/json,.json"
              class="sr-only"
              [disabled]="backupBusy()"
              (change)="restoreBackup($event)"
            />
          </label>
          @if (backupMessage(); as m) {
            <span
              class="text-sm"
              [class.text-emerald-300]="backupOk()"
              [class.text-amber-300]="!backupOk()"
            >
              {{ m }}
            </span>
          }
        </div>
        <p class="text-xs text-[color:var(--ink-faint)]">
          Wiederherstellen ersetzt die vorhandenen Zeitpläne, Einstellungen und Bilder durch den
          Stand aus der Datei.
        </p>
      </article>
    </section>
  `,
})
export class SettingsPage {
  private readonly api = inject(AlertSettingsService);
  private readonly backup = inject(BackupService);

  protected readonly enabled = signal(false);
  protected readonly topic = signal('');
  protected readonly saving = signal(false);
  protected readonly testing = signal(false);
  protected readonly message = signal<string | null>(null);
  protected readonly ok = signal(true);
  protected readonly backupBusy = signal(false);
  protected readonly backupMessage = signal<string | null>(null);
  protected readonly backupOk = signal(true);

  private readonly current = this.api.settings;
  protected readonly loaded = computed(() => this.current() !== null);

  /** Geändert gegenüber dem gespeicherten Stand? */
  protected readonly dirty = computed(() => {
    const c = this.current();
    if (!c) {
      return false;
    }
    return c.enabled !== this.enabled() || c.ntfyTopic !== this.topic().trim();
  });

  constructor() {
    // Sobald die Einstellungen geladen sind, die Formularfelder befüllen.
    effect(() => {
      const c = this.current();
      if (c) {
        this.enabled.set(c.enabled);
        this.topic.set(c.ntfyTopic);
      }
    });
  }

  protected async save(): Promise<void> {
    this.saving.set(true);
    this.message.set(null);
    try {
      await this.api.save({ enabled: this.enabled(), ntfyTopic: this.topic().trim() });
      this.ok.set(true);
      this.message.set('Gespeichert.');
    } catch {
      this.ok.set(false);
      this.message.set('Speichern fehlgeschlagen.');
    } finally {
      this.saving.set(false);
    }
  }

  protected async test(): Promise<void> {
    this.testing.set(true);
    this.message.set(null);
    const sent = await this.api.sendTest();
    this.ok.set(sent);
    this.message.set(
      sent ? 'Test-Push gesendet.' : 'Test fehlgeschlagen – Topic/Verbindung prüfen.',
    );
    this.testing.set(false);
  }

  protected async downloadBackup(): Promise<void> {
    this.backupBusy.set(true);
    this.backupMessage.set(null);
    try {
      await this.backup.download();
      this.backupOk.set(true);
      this.backupMessage.set('Backup heruntergeladen.');
    } catch {
      this.backupOk.set(false);
      this.backupMessage.set('Export fehlgeschlagen.');
    } finally {
      this.backupBusy.set(false);
    }
  }

  protected async restoreBackup(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) {
      return;
    }
    if (
      !confirm(
        'Backup wiederherstellen? Vorhandene Zeitpläne, Einstellungen und Bilder werden ersetzt.',
      )
    ) {
      return;
    }
    this.backupBusy.set(true);
    this.backupMessage.set(null);
    try {
      const s = await this.backup.restore(file);
      this.backupOk.set(true);
      this.backupMessage.set(
        `Wiederhergestellt: ${s.switchSchedules + s.batterySchedules + s.coverSchedules} Zeitpläne, ${s.itemImages} Bilder.`,
      );
      this.api.reload();
    } catch {
      this.backupOk.set(false);
      this.backupMessage.set('Wiederherstellen fehlgeschlagen – Datei prüfen.');
    } finally {
      this.backupBusy.set(false);
    }
  }
}
