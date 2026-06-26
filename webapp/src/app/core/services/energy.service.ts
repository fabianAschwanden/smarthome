import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { startWith, switchMap, timer } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { EnergySnapshot } from '../models/energy';

/**
 * Pollt den aktuellen Energie-Schnappschuss vom eigenen Backend (BFF) und
 * exponiert ihn als Signal. RxJS nur an dieser REST-Grenze.
 */
@Injectable({ providedIn: 'root' })
export class EnergyService {
  private readonly http = inject(HttpClient);

  private readonly snapshotState = signal<EnergySnapshot | null>(null);
  readonly snapshot = this.snapshotState.asReadonly();

  private readonly intervalMs = 2000;

  constructor() {
    timer(0, this.intervalMs)
      .pipe(
        switchMap(() => this.http.get<EnergySnapshot>('/api/energy/current')),
        startWith(null),
        takeUntilDestroyed(),
      )
      .subscribe((snapshot) => {
        if (snapshot) {
          this.snapshotState.set(snapshot);
        }
      });
  }
}
