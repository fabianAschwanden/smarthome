import { Injectable, computed, inject, signal } from '@angular/core';
import { TuyaService } from './tuya.service';
import { ClimateService } from './climate.service';
import { ApplianceService } from './appliance.service';
import { SensorService } from './sensor.service';
import { CoverService } from './cover.service';
import { SafetyService } from './safety.service';
import { CameraService } from './camera.service';

/**
 * Leitet die Raumliste automatisch aus den tatsächlich konfigurierten Geräten ab
 * (distinct, alphabetisch). Reaktiv über die vorhandenen Geräte-Service-Signals –
 * kein eigenes Polling, kein zusätzlicher Backend-Aufruf.
 */
@Injectable({ providedIn: 'root' })
export class RoomService {
  private readonly tuya = inject(TuyaService);
  private readonly climate = inject(ClimateService);
  private readonly appliance = inject(ApplianceService);
  private readonly sensor = inject(SensorService);
  private readonly cover = inject(CoverService);
  private readonly safety = inject(SafetyService);
  private readonly camera = inject(CameraService);

  /** Alle Räume, in denen mindestens ein Gerät konfiguriert ist – sortiert. */
  readonly rooms = computed(() => {
    const sources: { room?: string }[][] = [
      this.tuya.switches() ?? [],
      this.climate.climate() ?? [],
      this.appliance.appliances() ?? [],
      this.sensor.sensors() ?? [],
      this.cover.covers() ?? [],
      this.safety.smokeDetectors() ?? [],
      this.camera.cameras() ?? [],
    ];
    const rooms = new Set<string>();
    for (const list of sources) {
      for (const item of list) {
        if (item.room) {
          rooms.add(item.room);
        }
      }
    }
    return [...rooms].sort((a, b) => a.localeCompare(b, 'de'));
  });

  /**
   * Aktiver Raumfilter; {@code null} = "Alle" (alles sichtbar, aktiver Raum nur
   * hervorgehoben). Ein konkreter Raum filtert die Seiten hart auf diesen Raum.
   * Geteilt zwischen App-Shell (Raum-Chips) und den Seiten (z. B. Dashboard).
   */
  private readonly activeRoomState = signal<string | null>(null);
  readonly activeRoom = this.activeRoomState.asReadonly();

  /** Raum wählen ({@code null} = "Alle"). Ein bereits aktiver Raum wird abgewählt. */
  select(room: string | null): void {
    this.activeRoomState.update((cur) => (cur === room ? null : room));
  }

  /**
   * Soll eine Kachel mit diesem Raum angezeigt werden? Bei "Alle" (oder raumlosen
   * Items) immer; sonst nur, wenn der Raum dem aktiven entspricht.
   */
  shows(room: string | null | undefined): boolean {
    const active = this.activeRoomState();
    if (active === null) {
      return true;
    }
    return room === active;
  }
}
