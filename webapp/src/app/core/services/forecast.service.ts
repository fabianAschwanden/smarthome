import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, startWith, switchMap } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { pollingTimer } from '../polling';
import { BatterySchedule } from '../models/battery-schedule';
import { CoverSchedule } from '../models/cover-schedule';
import { Accuracy, AutoApply, HeatProtection, PvForecast, Surplus } from '../models/forecast';

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

  private readonly accuracyState = signal<Accuracy | null>(null);
  /** Prognosegüte der letzten Tage; null, solange noch nichts geladen wurde. */
  readonly accuracy = this.accuracyState.asReadonly();

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

    // Einmal beim Start: Die Nachrichtenzentrale soll den Zustand kennen, ohne dass
    // vorher jemand die Batterie-Seite geöffnet haben muss.
    this.loadAutoApply();
  }

  /**
   * Lädt die Prognosegüte nach. Bewusst nicht gepollt: Der Wert ändert sich einmal je
   * Tag, ein Timer wäre reine Beschäftigung.
   */
  private readonly heatProtectionState = signal<HeatProtection | null>(null);
  /** Beschattungsfenster; null, wenn keines vorliegt – an kühlen Tagen der Normalfall. */
  readonly heatProtection = this.heatProtectionState.asReadonly();

  /**
   * Lädt das Beschattungsfenster. Antwortet das Backend mit 204, liegt keines vor; der
   * Signal-Wert wird dann bewusst auf null gesetzt statt den alten Stand zu behalten.
   */
  loadHeatProtection(): void {
    this.http
      .get<HeatProtection>('/api/forecast/heat-protection', { observe: 'response' })
      .subscribe((response) => this.heatProtectionState.set(response.body ?? null));
  }

  /** Übernimmt das Fenster als Storen-Zeitsteuerung (Use Case 5). 409 = kein Fenster. */
  applyHeatProtection(): Observable<CoverSchedule[]> {
    return this.http.post<CoverSchedule[]>('/api/forecast/heat-protection/apply', {});
  }

  private readonly autoApplyState = signal<AutoApply | null>(null);
  /** Zustand der Lade-Automatik; treibt auch die Meldung in der Nachrichtenzentrale. */
  readonly autoApply = this.autoApplyState.asReadonly();

  loadAutoApply(): void {
    this.http
      .get<AutoApply>('/api/forecast/auto-apply')
      .subscribe((state) => this.autoApplyState.set(state));
  }

  setAutoApply(enabled: boolean): void {
    this.http
      .put<AutoApply>('/api/forecast/auto-apply', { enabled })
      .subscribe((state) => this.autoApplyState.set(state));
  }

  loadAccuracy(): void {
    this.http
      .get<Accuracy>('/api/forecast/accuracy')
      .subscribe((accuracy) => this.accuracyState.set(accuracy));
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
