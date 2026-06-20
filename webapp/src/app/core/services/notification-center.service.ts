import { Injectable, computed, inject } from '@angular/core';
import { SafetyService } from './safety.service';
import { SensorService } from './sensor.service';
import { CoverService } from './cover.service';
import { TuyaService } from './tuya.service';
import { ClimateService } from './climate.service';
import { AppNotification } from '../models/notification';

const LOW_BATTERY_PERCENT = 20;

/**
 * Nachrichtenzentrale: leitet aus den bestehenden Geräte-Signalen (BFF) die aktuellen
 * Meldungen ab – Rauchmelder-Alarm, Erreichbarkeit (offline) und niedriger Akku.
 * Rein reaktiv über computed-Signale; keine eigene Datenhaltung, keine REST-Calls.
 */
@Injectable({ providedIn: 'root' })
export class NotificationCenterService {
  private readonly safety = inject(SafetyService);
  private readonly sensors = inject(SensorService);
  private readonly covers = inject(CoverService);
  private readonly tuya = inject(TuyaService);
  private readonly climate = inject(ClimateService);

  /** Alle aktuellen Meldungen, nach Schweregrad sortiert (Alarm zuerst). */
  readonly notifications = computed<AppNotification[]>(() => {
    const out: AppNotification[] = [];

    // Rauchmelder: Alarm (hart), offline (Warnung), niedriger Akku (Warnung).
    for (const sm of this.safety.smokeDetectors() ?? []) {
      if (sm.alarm === 'ALARM') {
        out.push({
          id: `smoke:${sm.id}:alarm`,
          severity: 'alarm',
          title: `Rauchalarm – ${sm.name}`,
          detail: `${sm.room || 'Rauchmelder'}: Rauch erkannt!`,
          source: 'smoke',
        });
      } else if (!sm.online) {
        out.push({
          id: `smoke:${sm.id}:offline`,
          severity: 'warning',
          title: `${sm.name} nicht erreichbar`,
          detail: 'Rauchmelder meldet sich seit über 5 Minuten nicht.',
          source: 'smoke',
        });
      }
      if (sm.online && sm.battery >= 0 && sm.battery < LOW_BATTERY_PERCENT) {
        out.push({
          id: `smoke:${sm.id}:battery`,
          severity: 'warning',
          title: `${sm.name}: Akku niedrig`,
          detail: `Batterie bei ${sm.battery} %.`,
          source: 'battery',
        });
      }
    }

    // Erreichbarkeit der übrigen Geräte (Warnung).
    for (const s of this.tuya.switches() ?? []) {
      if (!s.online) {
        out.push(offline(`switch:${s.id}`, s.name, 'switch', 'Schalter'));
      }
    }
    for (const c of this.covers.covers() ?? []) {
      if (!c.online) {
        out.push(offline(`cover:${c.id}`, c.name, 'cover', 'Store'));
      }
    }
    for (const s of this.sensors.sensors() ?? []) {
      if (!s.online) {
        out.push(offline(`sensor:${s.id}`, s.name, 'sensor', 'Sensor'));
      }
    }
    for (const c of this.climate.climate() ?? []) {
      if (!c.online) {
        out.push(offline(`climate:${c.id}`, c.name, 'climate', 'Klimaanlage'));
      }
    }

    const rank = { alarm: 0, warning: 1, info: 2 };
    return out.sort((a, b) => rank[a.severity] - rank[b.severity]);
  });

  /** Anzahl Meldungen (für das Badge). */
  readonly count = computed(() => this.notifications().length);

  /** Mindestens ein aktiver Alarm? (Glocke leuchtet/pulsiert). */
  readonly hasAlarm = computed(() => this.notifications().some((n) => n.severity === 'alarm'));
}

function offline(
  id: string,
  name: string,
  source: AppNotification['source'],
  kind: string,
): AppNotification {
  return {
    id: `${id}:offline`,
    severity: 'warning',
    title: `${name} nicht erreichbar`,
    detail: `${kind} antwortet nicht (Netz/IP prüfen).`,
    source,
  };
}
