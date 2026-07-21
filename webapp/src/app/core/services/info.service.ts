import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AppInfo } from '../models/app-info';

/**
 * Lädt die Release-Info (Version, Build-Zeit) einmalig vom Backend und exponiert sie
 * als Signal. Ändert sich zur Laufzeit nicht – daher kein Polling.
 */
@Injectable({ providedIn: 'root' })
export class InfoService {
  private readonly http = inject(HttpClient);

  private readonly state = signal<AppInfo | null>(null);
  readonly info = this.state.asReadonly();

  constructor() {
    this.http
      .get<AppInfo>('/api/info')
      .pipe(takeUntilDestroyed())
      .subscribe((value) => this.state.set(value));
  }
}
