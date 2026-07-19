import { Injectable, computed, inject } from '@angular/core';
import { Router } from '@angular/router';
import { BatteryService } from '../services/battery.service';
import { ClimateService } from '../services/climate.service';
import { CoverService } from '../services/cover.service';
import { SafetyService } from '../services/safety.service';
import { SensorService } from '../services/sensor.service';
import { TuyaService } from '../services/tuya.service';
import { SearchEntry, SearchHit, search } from './search';

/** Statisch erreichbare Seiten mit deutschen Synonymen. */
const PAGES: SearchEntry[] = [
  page('Favoriten', '/', ['start', 'home', 'dashboard', 'uebersicht']),
  page('Energie', '/energy', ['strom', 'pv', 'solar', 'verbrauch', 'produktion', 'fronius']),
  page('Batterie', '/battery', ['akku', 'speicher', 'laden']),
  page('Batterie-Zeitsteuerung', '/battery/schedule', ['zeitplan', 'zeitsteuerung']),
  page('Schalter', '/switch', ['licht', 'lampe', 'steckdose', 'strom']),
  page('Storen', '/covers', ['rollladen', 'jalousie', 'beschattung', 'store']),
  page('Storen-Zeitsteuerung', '/covers/schedule', ['zeitplan', 'zeitsteuerung']),
  page('Wellness', '/wellness', ['spa', 'whirlpool', 'pool']),
  page('Klima', '/climate', ['klimaanlage', 'heizen', 'kuehlen', 'ac', 'temperatur']),
  page('Kameras', '/cameras', ['kamera', 'video', 'ueberwachung']),
  page('Einstellungen', '/settings', ['settings', 'konfiguration', 'alarm']),
];

function page(name: string, route: string, keywords: string[]): SearchEntry {
  return { kind: 'page', id: '', name, room: '', route, keywords, actions: [] };
}

/**
 * Globale Suche der Topbar: baut den Index live aus den Geräte-Signalen (Schalter,
 * Storen, Klima, Batterie, Sensoren, Rauchmelder) plus den Seiten und führt Treffer
 * aus – Befehle direkt über die Fach-Services, sonst Navigation zur Zielseite.
 */
@Injectable({ providedIn: 'root' })
export class SearchService {
  private readonly router = inject(Router);
  private readonly tuya = inject(TuyaService);
  private readonly covers = inject(CoverService);
  private readonly climate = inject(ClimateService);
  private readonly battery = inject(BatteryService);
  private readonly sensors = inject(SensorService);
  private readonly safety = inject(SafetyService);

  /** Live-Index: Geräte aus den gepollten Signalen + statische Seiten. */
  private readonly entries = computed<SearchEntry[]>(() => {
    const list: SearchEntry[] = [];
    for (const s of this.tuya.switches() ?? []) {
      list.push({
        kind: 'switch',
        id: s.id,
        name: s.name,
        room: s.room,
        route: '/switch',
        keywords: ['schalter', 'licht'],
        actions: ['ON', 'OFF'],
      });
    }
    for (const c of this.covers.covers() ?? []) {
      list.push({
        kind: 'cover',
        id: c.id,
        name: c.name,
        room: c.room,
        route: '/covers',
        keywords: ['store', 'storen', 'rollladen', 'jalousie'],
        actions: ['OPEN', 'CLOSE', 'STOP'],
      });
    }
    for (const c of this.climate.climate() ?? []) {
      list.push({
        kind: 'climate',
        id: c.id,
        name: c.name,
        room: c.room,
        route: '/climate',
        keywords: ['klima', 'klimaanlage', 'ac'],
        actions: ['ON', 'OFF', 'BOOST'],
      });
    }
    if (this.battery.control() !== null) {
      list.push({
        kind: 'battery',
        id: 'battery',
        name: 'Batterie',
        room: 'Keller',
        route: '/battery',
        keywords: ['akku', 'speicher', 'batterieladung'],
        actions: ['ON', 'OFF'],
      });
    }
    for (const s of this.sensors.sensors() ?? []) {
      list.push({
        kind: 'sensor',
        id: s.id,
        name: s.name,
        room: s.room,
        route: '/',
        keywords: ['sensor', 'temperatur', 'feuchte', 'innentemperatur'],
        actions: [],
      });
    }
    for (const s of this.safety.smokeDetectors() ?? []) {
      list.push({
        kind: 'smoke',
        id: s.id,
        name: s.name,
        room: s.room,
        route: '/',
        keywords: ['rauchmelder', 'alarm', 'sicherheit'],
        actions: [],
      });
    }
    return [...list, ...PAGES];
  });

  search(text: string): SearchHit[] {
    return search(this.entries(), text);
  }

  /**
   * Führt einen Treffer aus. Befehle gehen direkt an den Fach-Service und liefern
   * einen Bestätigungstext; ohne Befehl wird zur Zielseite navigiert (leerer Text).
   */
  execute(hit: SearchHit): string {
    const { entry, action } = hit;
    if (action === undefined) {
      void this.router.navigateByUrl(entry.route);
      return '';
    }
    switch (entry.kind) {
      case 'switch':
        this.tuya.switchTo(entry.id, action === 'ON' ? 'ON' : 'OFF');
        return `${entry.name} ${action === 'ON' ? 'eingeschaltet' : 'ausgeschaltet'}`;
      case 'cover':
        this.covers.command(
          entry.id,
          action === 'OPEN' ? 'OPEN' : action === 'STOP' ? 'STOP' : 'CLOSE',
        );
        return `${entry.name}: ${action === 'OPEN' ? 'fährt auf' : action === 'STOP' ? 'gestoppt' : 'fährt zu'}`;
      case 'climate':
        if (action === 'BOOST') {
          const current = (this.climate.climate() ?? []).find((c) => c.id === entry.id);
          this.climate.setBoost(entry.id, !(current?.boost ?? false));
          return `${entry.name}: Boost ${current?.boost ? 'aus' : 'ein'}`;
        }
        this.climate.setPower(entry.id, action === 'ON');
        return `${entry.name} ${action === 'ON' ? 'eingeschaltet' : 'ausgeschaltet'}`;
      case 'battery':
        this.battery.switchRelayManual(action === 'ON' ? 'ON' : 'OFF');
        return `Batterieladung ${action === 'ON' ? 'ein (Modus Manuell)' : 'aus (Modus Manuell)'}`;
      default:
        void this.router.navigateByUrl(entry.route);
        return '';
    }
  }
}
