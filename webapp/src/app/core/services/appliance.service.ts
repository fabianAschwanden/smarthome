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

  /** Laufende Soll-Temperatur-Befehle je Anlage: id -> erwartete Soll-Temperatur. */
  private readonly pendingTemp = new Map<string, number>();

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
    // Optimistisch (Gecko-Befehl ist langsam, sonst springt der Wert zurück).
    this.pendingTemp.set(id, target);
    this.patchTarget(id, target);

    this.http.post<Appliance>(`/api/appliances/${id}/temperature`, { target }).subscribe({
      next: (updated) => {
        this.pendingTemp.delete(id);
        this.merge(updated);
      },
      error: () => this.pendingTemp.delete(id),
    });
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

  /** Setzt die Soll-Temperatur im lokalen Signal sofort (optimistisch). */
  private patchTarget(id: string, target: number): void {
    const current = this.appliancesState();
    if (current) {
      this.appliancesState.set(
        current.map((a) =>
          a.id === id && a.temperature
            ? { ...a, temperature: { ...a.temperature, target } }
            : a,
        ),
      );
    }
  }

  /** Überschreibt im Anlagen-Stand alle Funktionen/Soll-Temp, für die noch ein Befehl läuft. */
  private applyPending(a: Appliance): Appliance {
    let functions = a.functions;
    for (const [key, state] of this.pending) {
      const [id, fn] = key.split(':');
      if (id === a.id) {
        functions = { ...functions, [fn]: state };
      }
    }
    let result = functions === a.functions ? a : { ...a, functions };
    const t = this.pendingTemp.get(a.id);
    if (t !== undefined && result.temperature) {
      result = { ...result, temperature: { ...result.temperature, target: t } };
    }
    return result;
  }

  private merge(updated: Appliance): void {
    const current = this.appliancesState();
    if (current) {
      const withPending = this.applyPending(updated);
      this.appliancesState.set(current.map((a) => (a.id === withPending.id ? withPending : a)));
    }
  }
}
