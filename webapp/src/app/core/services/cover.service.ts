import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { startWith, switchMap, timer } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Cover, CoverCommand } from '../models/cover';

/**
 * Pollt die Liste der Storen vom eigenen Backend (BFF) und exponiert sie als Signal;
 * Befehle/Positionen aktualisieren die betroffene Store direkt. RxJS nur an der Grenze.
 *
 * <p>Die {@code position} ist die rohe Geräteposition (Tuya dp 2). Die UI-Semantik
 * „100 % = zu" wird in der Komponente abgebildet, nicht hier.</p>
 */
@Injectable({ providedIn: 'root' })
export class CoverService {
  private readonly http = inject(HttpClient);

  private readonly coversState = signal<Cover[] | null>(null);
  readonly covers = this.coversState.asReadonly();

  private readonly intervalMs = 3000;

  constructor() {
    timer(0, this.intervalMs)
      .pipe(
        switchMap(() => this.http.get<Cover[]>('/api/covers')),
        startWith(null),
        takeUntilDestroyed(),
      )
      .subscribe((value) => {
        if (value) {
          this.coversState.set(value);
        }
      });
  }

  command(id: string, command: CoverCommand): void {
    this.http.post<Cover>(`/api/covers/${id}/command`, { command }).subscribe((c) => this.merge(c));
  }

  setPosition(id: string, position: number): void {
    this.http
      .post<Cover>(`/api/covers/${id}/position`, { position })
      .subscribe((c) => this.merge(c));
  }

  private merge(updated: Cover): void {
    const current = this.coversState();
    if (current) {
      this.coversState.set(current.map((c) => (c.id === updated.id ? updated : c)));
    }
  }
}
