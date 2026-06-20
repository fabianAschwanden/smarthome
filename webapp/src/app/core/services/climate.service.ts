import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, startWith, switchMap, timer } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Climate, ClimateMode } from '../models/climate';

/**
 * Pollt die Klimaanlagen vom eigenen Backend (BFF) und exponiert sie als Signal;
 * Power-/Modus-/Temperatur-Befehle aktualisieren das betroffene Gerät direkt.
 */
@Injectable({ providedIn: 'root' })
export class ClimateService {
  private readonly http = inject(HttpClient);

  private readonly climateState = signal<Climate[] | null>(null);
  readonly climate = this.climateState.asReadonly();

  private readonly intervalMs = 3000;

  constructor() {
    timer(0, this.intervalMs)
      .pipe(
        switchMap(() => this.http.get<Climate[]>('/api/climate')),
        startWith(null),
        takeUntilDestroyed(),
      )
      .subscribe((value) => {
        if (value) {
          this.climateState.set(value);
        }
      });
  }

  setPower(id: string, on: boolean): void {
    this.apply(this.http.post<Climate>(`/api/climate/${id}/power`, { on }));
  }

  setMode(id: string, mode: ClimateMode): void {
    this.apply(this.http.post<Climate>(`/api/climate/${id}/mode`, { mode }));
  }

  setTargetTemp(id: string, temperature: number): void {
    this.apply(this.http.post<Climate>(`/api/climate/${id}/target`, { temperature }));
  }

  private apply(request: Observable<Climate>): void {
    request.subscribe((updated) => {
      const current = this.climateState();
      if (current) {
        this.climateState.set(current.map((c) => (c.id === updated.id ? updated : c)));
      }
    });
  }
}
