import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { firstValueFrom } from 'rxjs';
import { AlertSettings } from '../models/alert-settings';

/** Lädt und speichert die Alert-Einstellungen über das eigene Backend (BFF). */
@Injectable({ providedIn: 'root' })
export class AlertSettingsService {
  private readonly http = inject(HttpClient);

  private readonly state = signal<AlertSettings | null>(null);
  readonly settings = this.state.asReadonly();

  constructor() {
    this.http
      .get<AlertSettings>('/api/alert-settings')
      .pipe(takeUntilDestroyed())
      .subscribe((value) => this.state.set(value));
  }

  /** Lädt die Einstellungen neu (z. B. nach einem Backup-Restore). */
  reload(): void {
    this.http.get<AlertSettings>('/api/alert-settings').subscribe((value) => this.state.set(value));
  }

  /** Speichert die Einstellungen und aktualisiert das Signal. */
  async save(settings: AlertSettings): Promise<void> {
    const saved = await firstValueFrom(
      this.http.put<AlertSettings>('/api/alert-settings', settings),
    );
    this.state.set(saved);
  }

  /** Sendet eine Test-Benachrichtigung; {@code true} bei Erfolg. */
  async sendTest(): Promise<boolean> {
    try {
      await firstValueFrom(this.http.post('/api/alert-settings/test', null));
      return true;
    } catch {
      return false;
    }
  }
}
