import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, startWith, switchMap, timer } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { EnergyHistory, EnergySnapshot, HistoryRange } from '../models/energy';

/**
 * Pollt den aktuellen Energie-Schnappschuss vom eigenen Backend (BFF) und exponiert
 * ihn als Signal. Zusätzlich der Tagesverlauf (für die Dashboard-Sparkline, seltener
 * gepollt) und der Verlauf auf Abruf (für die Detailseite). RxJS nur an dieser
 * REST-Grenze.
 */
@Injectable({ providedIn: 'root' })
export class EnergyService {
  private readonly http = inject(HttpClient);

  private readonly snapshotState = signal<EnergySnapshot | null>(null);
  readonly snapshot = this.snapshotState.asReadonly();

  private readonly dayHistoryState = signal<EnergyHistory | null>(null);
  /** Tagesverlauf (kWh je Stunde) für die Dashboard-Sparkline. */
  readonly dayHistory = this.dayHistoryState.asReadonly();

  private readonly intervalMs = 2000;
  private readonly historyIntervalMs = 300_000; // 5 min – passt zum Sampling-Intervall

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

    timer(0, this.historyIntervalMs)
      .pipe(
        switchMap(() => this.history('day')),
        startWith(null),
        takeUntilDestroyed(),
      )
      .subscribe((history) => {
        if (history) {
          this.dayHistoryState.set(history);
        }
      });
  }

  /** Verlauf auf Abruf (Detailseite): Tag/Woche/Monat. */
  history(range: HistoryRange): Observable<EnergyHistory> {
    return this.http.get<EnergyHistory>(`/api/energy/history?range=${range}`);
  }
}
