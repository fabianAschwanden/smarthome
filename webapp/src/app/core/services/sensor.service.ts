import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { startWith, switchMap, timer } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Sensor } from '../models/sensor';

/** Pollt die Umweltsensoren vom eigenen Backend (BFF) und exponiert sie als Signal. */
@Injectable({ providedIn: 'root' })
export class SensorService {
  private readonly http = inject(HttpClient);

  private readonly sensorsState = signal<Sensor[] | null>(null);
  readonly sensors = this.sensorsState.asReadonly();

  private readonly intervalMs = 5000;

  constructor() {
    timer(0, this.intervalMs)
      .pipe(
        switchMap(() => this.http.get<Sensor[]>('/api/sensors')),
        startWith(null),
        takeUntilDestroyed(),
      )
      .subscribe((value) => {
        if (value) {
          this.sensorsState.set(value);
        }
      });
  }
}
