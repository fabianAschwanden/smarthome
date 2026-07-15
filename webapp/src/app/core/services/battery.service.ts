import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { startWith, switchMap, timer } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { BatteryControl, ControlMode, RelayState } from '../models/battery';

/**
 * Pollt den Steuerstand der Batterie vom eigenen Backend (BFF) und exponiert ihn
 * als Signal; Modus- und Schaltbefehle aktualisieren das Signal direkt.
 * RxJS nur an dieser REST-Grenze.
 */
@Injectable({ providedIn: 'root' })
export class BatteryService {
  private readonly http = inject(HttpClient);

  private readonly controlState = signal<BatteryControl | null>(null);
  readonly control = this.controlState.asReadonly();

  private readonly intervalMs = 3000;

  constructor() {
    timer(0, this.intervalMs)
      .pipe(
        switchMap(() => this.http.get<BatteryControl>('/api/battery')),
        startWith(null),
        takeUntilDestroyed(),
      )
      .subscribe((control) => {
        if (control) {
          this.controlState.set(control);
        }
      });
  }

  changeMode(mode: ControlMode): void {
    this.http.put<BatteryControl>('/api/battery/mode', { mode }).subscribe((control) => {
      this.controlState.set(control);
    });
  }

  switchRelay(state: RelayState): void {
    this.http.post<BatteryControl>('/api/battery/relay', { state }).subscribe((control) => {
      this.controlState.set(control);
    });
  }

  /**
   * Schaltet das Relais und wechselt dabei nötigenfalls auf MANUAL: Das Backend
   * lehnt Schaltbefehle im AUTO-Modus ab (ManualSwitchNotAllowed). Für die
   * Dashboard-Kachel, wo kein Modus-Umschalter Platz hat – die Automatik bleibt
   * danach aus, bis sie auf der Batterie-Seite wieder eingeschaltet wird.
   */
  switchRelayManual(state: RelayState): void {
    const relay = () => this.http.post<BatteryControl>('/api/battery/relay', { state });
    const request =
      this.controlState()?.mode === 'MANUAL'
        ? relay()
        : this.http
            .put<BatteryControl>('/api/battery/mode', { mode: 'MANUAL' })
            .pipe(switchMap(relay));
    request.subscribe((control) => {
      this.controlState.set(control);
    });
  }
}
