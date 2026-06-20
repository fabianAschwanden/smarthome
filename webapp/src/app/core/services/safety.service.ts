import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { startWith, switchMap, timer } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { SmokeDetector } from '../models/safety';

/** Pollt die Rauchmelder vom eigenen Backend (BFF) und exponiert sie als Signal. */
@Injectable({ providedIn: 'root' })
export class SafetyService {
  private readonly http = inject(HttpClient);

  private readonly smokeState = signal<SmokeDetector[] | null>(null);
  readonly smokeDetectors = this.smokeState.asReadonly();

  private readonly intervalMs = 5000;

  constructor() {
    timer(0, this.intervalMs)
      .pipe(
        switchMap(() => this.http.get<SmokeDetector[]>('/api/safety/smoke')),
        startWith(null),
        takeUntilDestroyed(),
      )
      .subscribe((value) => {
        if (value) {
          this.smokeState.set(value);
        }
      });
  }
}
