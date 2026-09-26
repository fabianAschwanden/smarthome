import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { exhaustMap, startWith } from 'rxjs';
import { pollingTimer } from '../polling';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { SwitchState, TuyaSwitch } from '../models/tuya';

/**
 * Pollt die Liste der Tuya-Schalter vom eigenen Backend (BFF) und exponiert sie als
 * Signal; ein Schaltbefehl aktualisiert das betroffene Gerät direkt. RxJS nur an
 * dieser REST-Grenze.
 */
@Injectable({ providedIn: 'root' })
export class TuyaService {
  private readonly http = inject(HttpClient);

  private readonly switchesState = signal<TuyaSwitch[] | null>(null);
  readonly switches = this.switchesState.asReadonly();

  private readonly intervalMs = 3000;

  constructor() {
    pollingTimer(this.intervalMs)
      .pipe(
        // exhaustMap statt switchMap: ein laufender Abruf wird nicht abgebrochen, der Tick
        // dazwischen faellt aus. Mit switchMap kam bei einem Abruf ueber 3 s NIE eine Antwort
        // durch - die Liste blieb leer (26.09.2026, ein nicht erreichbarer Tuya-Schalter).
        exhaustMap(() => this.http.get<TuyaSwitch[]>('/api/switches')),
        startWith(null),
        takeUntilDestroyed(),
      )
      .subscribe((value) => {
        if (value) {
          this.switchesState.set(value);
        }
      });
  }

  switchTo(id: string, state: SwitchState, confirm = false): void {
    this.http.post<TuyaSwitch>(`/api/switches/${id}`, { state, confirm }).subscribe((updated) => {
      const current = this.switchesState();
      if (current) {
        this.switchesState.set(current.map((s) => (s.id === updated.id ? updated : s)));
      }
    });
  }
}
