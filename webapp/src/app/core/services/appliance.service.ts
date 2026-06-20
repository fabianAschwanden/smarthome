import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { startWith, switchMap, timer } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Appliance, ApplianceFunction, FunctionState } from '../models/appliance';

/**
 * Pollt die Wellness-Anlagen vom eigenen Backend (BFF) und exponiert sie als Signal;
 * Funktions-Schaltbefehle aktualisieren die betroffene Anlage direkt.
 *
 * <p>Die Gecko-Steuerung ist langsam (ein Schaltbefehl kann ~1–2 min dauern). Damit
 * der Toggle nicht zurückspringt, während der Befehl läuft, wird der Klick-Zustand
 * <b>optimistisch</b> gesetzt und als "pending" gehalten – das Polling überschreibt
 * diese Funktion erst wieder, wenn der Befehl quittiert ist.</p>
 */
@Injectable({ providedIn: 'root' })
export class ApplianceService {
  private readonly http = inject(HttpClient);

  private readonly appliancesState = signal<Appliance[] | null>(null);
  readonly appliances = this.appliancesState.asReadonly();

  /** Laufende Schaltbefehle je Anlage/Funktion: "id:FN" -> erwarteter Zustand. */
  private readonly pending = new Map<string, FunctionState>();

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
          // Noch laufende Schaltbefehle überstimmen den (evtl. veralteten) Poll-Wert.
          this.appliancesState.set(value.map((a) => this.applyPending(a)));
        }
      });
  }

  switchFunction(id: string, fn: ApplianceFunction, state: FunctionState): void {
    // Optimistisch: Klick-Zustand sofort anzeigen + als pending merken.
    this.pending.set(`${id}:${fn}`, state);
    this.patchFunction(id, fn, state);

    this.http.post<Appliance>(`/api/appliances/${id}/functions/${fn}`, { state }).subscribe({
      next: (updated) => {
        this.pending.delete(`${id}:${fn}`);
        this.merge(updated);
      },
      error: () => this.pending.delete(`${id}:${fn}`),
    });
  }

  /** Soll-Temperatur einer beheizten Anlage setzen (°C). */
  setTargetTemp(id: string, target: number): void {
    this.http
      .post<Appliance>(`/api/appliances/${id}/temperature`, { target })
      .subscribe((updated) => this.merge(updated));
  }

  /** Setzt eine Funktion im lokalen Signal sofort (optimistisch). */
  private patchFunction(id: string, fn: ApplianceFunction, state: FunctionState): void {
    const current = this.appliancesState();
    if (current) {
      this.appliancesState.set(
        current.map((a) =>
          a.id === id ? { ...a, functions: { ...a.functions, [fn]: state } } : a,
        ),
      );
    }
  }

  /** Überschreibt im Anlagen-Stand alle Funktionen, für die noch ein Befehl läuft. */
  private applyPending(a: Appliance): Appliance {
    let functions = a.functions;
    for (const [key, state] of this.pending) {
      const [id, fn] = key.split(':');
      if (id === a.id) {
        functions = { ...functions, [fn]: state };
      }
    }
    return functions === a.functions ? a : { ...a, functions };
  }

  private merge(updated: Appliance): void {
    const current = this.appliancesState();
    if (current) {
      const withPending = this.applyPending(updated);
      this.appliancesState.set(current.map((a) => (a.id === withPending.id ? withPending : a)));
    }
  }
}
