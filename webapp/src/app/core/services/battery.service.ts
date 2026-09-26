import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { exhaustMap, startWith, switchMap } from 'rxjs';
import { pollingTimer } from '../polling';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  BatteryControl,
  ChargingSession,
  ControlMode,
  RelayState,
  SunGuard,
} from '../models/battery';

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

  private readonly sessionsState = signal<ChargingSession[]>([]);
  /** Letzte Ladevorgänge; die Energie darin ist geschätzt. */
  readonly chargingSessions = this.sessionsState.asReadonly();

  /**
   * Lädt die Ladevorgänge nach. Bewusst nicht gepollt: Ein Eintrag entsteht erst, wenn
   * ein Ladevorgang endet – ein Timer wäre reine Beschäftigung.
   */
  loadChargingSessions(): void {
    this.http
      .get<ChargingSession[]>('/api/battery/charging-sessions')
      .subscribe((sessions) => this.sessionsState.set(sessions));
  }

  private readonly sunGuardState = signal<SunGuard | null>(null);
  /** Stand des Ohne-Sonne-Ausschalters. */
  readonly sunGuard = this.sunGuardState.asReadonly();

  /**
   * Lädt den Stand des Wächters. Nicht gepollt: Er ändert sich zweimal am Tag –
   * morgens scharf, abends ausgelöst. Ein Drei-Sekunden-Takt wäre reine Beschäftigung.
   */
  loadSunGuard(): void {
    this.http
      .get<SunGuard>('/api/battery/sun-guard')
      .subscribe((guard) => this.sunGuardState.set(guard));
  }

  setSunGuard(enabled: boolean): void {
    this.http
      .put<SunGuard>('/api/battery/sun-guard', { enabled })
      .subscribe((guard) => this.sunGuardState.set(guard));
  }

  private readonly intervalMs = 3000;

  constructor() {
    pollingTimer(this.intervalMs)
      .pipe(
        // exhaustMap statt switchMap: ein laufender Abruf wird nicht abgebrochen, der Tick
        // dazwischen faellt aus. Mit switchMap kam bei einem Abruf ueber 3 s NIE eine Antwort
        // durch - die Liste blieb leer (26.09.2026, ein nicht erreichbarer Tuya-Schalter).
        exhaustMap(() => this.http.get<BatteryControl>('/api/battery')),
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
