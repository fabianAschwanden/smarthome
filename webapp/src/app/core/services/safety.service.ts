import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { exhaustMap, startWith } from 'rxjs';
import { pollingTimer } from '../polling';
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
    pollingTimer(this.intervalMs)
      .pipe(
        // exhaustMap statt switchMap: ein laufender Abruf wird nicht abgebrochen, der Tick
        // dazwischen faellt aus. Mit switchMap kam bei einem Abruf ueber 3 s NIE eine Antwort
        // durch - die Liste blieb leer (26.09.2026, ein nicht erreichbarer Tuya-Schalter).
        exhaustMap(() => this.http.get<SmokeDetector[]>('/api/safety/smoke')),
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
