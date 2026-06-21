import { Injectable, computed, inject } from '@angular/core';
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
}
