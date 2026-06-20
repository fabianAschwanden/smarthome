import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { startWith, switchMap, timer } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Appliance, ApplianceFunction, FunctionState } from '../models/appliance';

/**
 * Pollt die Wellness-Anlagen vom eigenen Backend (BFF) und exponiert sie als Signal;
 * Funktions-Schaltbefehle aktualisieren die betroffene Anlage direkt.
 */
@Injectable({ providedIn: 'root' })
export class ApplianceService {
  private readonly http = inject(HttpClient);

  private readonly appliancesState = signal<Appliance[] | null>(null);
  readonly appliances = this.appliancesState.asReadonly();

  private readonly intervalMs = 3000;

  constructor() {
    timer(0, this.intervalMs)
      .pipe(
        switchMap(() => this.http.get<Appliance[]>('/api/appliances')),
        startWith(null),
        takeUntilDestroyed(),
      )
      .subscribe((value) => {
        if (value) {
          this.appliancesState.set(value);
        }
      });
  }

  switchFunction(id: string, fn: ApplianceFunction, state: FunctionState): void {
    this.http
      .post<Appliance>(`/api/appliances/${id}/functions/${fn}`, { state })
      .subscribe((updated) => this.merge(updated));
  }

  /** Soll-Temperatur einer beheizten Anlage setzen (°C). */
  setTargetTemp(id: string, target: number): void {
    this.http
      .post<Appliance>(`/api/appliances/${id}/temperature`, { target })
      .subscribe((updated) => this.merge(updated));
  }

  private merge(updated: Appliance): void {
    const current = this.appliancesState();
    if (current) {
      this.appliancesState.set(current.map((a) => (a.id === updated.id ? updated : a)));
    }
  }
}
