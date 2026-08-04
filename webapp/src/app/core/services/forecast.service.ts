import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, startWith, switchMap } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { pollingTimer } from '../polling';
import { BatterySchedule } from '../models/battery-schedule';
import { PvForecast, Surplus } from '../models/forecast';

/**
 * Holt PV-Prognose und Überschussfenster vom eigenen Backend (BFF) und exponiert sie als
 * Signals.
 *
 * <p>Bewusst selten gepollt: Das Backend rechnet stündlich neu, häufigeres Fragen liefert
 * nur dieselben Zahlen. Fünf Minuten reichen, damit ein Wechsel spätestens beim nächsten
 * Blick aufs Dashboard sichtbar ist.
 */
@Injectable({ providedIn: 'root' })
export class ForecastService {
  private readonly http = inject(HttpClient);

  private readonly pvState = signal<PvForecast | null>(null);
  /** Aktuelle Prognose; null, solange das Backend noch nie gerechnet hat (HTTP 204). */
  readonly pv = this.pvState.asReadonly();

  private readonly surplusState = signal<Surplus | null>(null);
  readonly surplus = this.surplusState.asReadonly();

  private readonly intervalMs = 5 * 60 * 1000;

  constructor() {
    pollingTimer(this.intervalMs)
      .pipe(
        switchMap(() => this.http.get<PvForecast>('/api/forecast/pv')),
        startWith(null),
        takeUntilDestroyed(),
      )
      .subscribe((forecast) => this.pvState.set(forecast ?? null));

    pollingTimer(this.intervalMs)
      .pipe(
        switchMap(() => this.http.get<Surplus>('/api/forecast/surplus')),
        startWith(null),
        takeUntilDestroyed(),
      )
      .subscribe((surplus) => {
        if (surplus) {
          this.surplusState.set(surplus);
        }
      });
  }

  /**
   * Übernimmt die aktuelle Empfehlung als Batterie-Zeitplan (Use Case 14).
   *
   * <p>Antwortet das Backend mit 409, liegt gerade keine Empfehlung vor – der Aufrufer
   * behandelt das als Fachfall, nicht als Fehler.
   */
  applyRecommendation(): Observable<BatterySchedule> {
    return this.http.post<BatterySchedule>('/api/forecast/recommendation/apply', {});
  }
}
