import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { RestoreSummary } from '../models/backup';

/**
 * Backup der Nutzerdaten (Zeitpläne, Alarm-Einstellungen, Bilder): Export als
 * JSON-Datei-Download, Restore aus einer hochgeladenen Datei. RxJS nur an der
 * REST-Grenze.
 */
@Injectable({ providedIn: 'root' })
export class BackupService {
  private readonly http = inject(HttpClient);

  /** Lädt das Backup herunter (Dateiname mit Tagesdatum). */
  async download(): Promise<void> {
    const blob = await firstValueFrom(this.http.get('/api/backup', { responseType: 'blob' }));
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = `smarthome-backup-${new Date().toISOString().slice(0, 10)}.json`;
    anchor.click();
    URL.revokeObjectURL(url);
  }

  /** Stellt ein Backup wieder her; das Backend ersetzt den Bestand je Kategorie. */
  async restore(file: File): Promise<RestoreSummary> {
    const parsed: unknown = JSON.parse(await file.text());
    return firstValueFrom(this.http.post<RestoreSummary>('/api/backup', parsed));
  }
}
