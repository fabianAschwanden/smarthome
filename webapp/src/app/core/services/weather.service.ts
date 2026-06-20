import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { catchError, of, startWith, switchMap, timer } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Weather } from '../models/weather';

/**
 * Pollt die Wettervorhersage vom eigenen Backend (BFF) und exponiert sie als Signal.
 * Das Backend cacht die externe Quelle; hier reicht ein gemächliches Intervall.
 * RxJS nur an dieser REST-Grenze.
 */
@Injectable({ providedIn: 'root' })
export class WeatherService {
  private readonly http = inject(HttpClient);

  private readonly weatherState = signal<Weather | null>(null);
  readonly weather = this.weatherState.asReadonly();

  private readonly intervalMs = 5 * 60 * 1000;

  constructor() {
    timer(0, this.intervalMs)
      .pipe(
        switchMap(() => this.http.get<Weather>('/api/weather').pipe(catchError(() => of(null)))),
        startWith(null),
        takeUntilDestroyed(),
      )
      .subscribe((value) => {
        if (value) {
          this.weatherState.set(value);
        }
      });
  }
}
