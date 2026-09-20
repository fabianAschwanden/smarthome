import { describe, expect, it, vi } from 'vitest';
import type { API, Logging, PlatformAccessory } from 'homebridge';
import { SmarthomePlatform } from '../src/platform';
import type { ApplianceDto, Snapshot, SwitchDto } from '../src/types';
import { FakeAccessory } from './harness';

/**
 * Ein Accessory, wie Homebridge es anlegt oder aus dem Cache zurueckgibt - mit dem
 * AccessoryInformation-Service, den Homebridge jedem Accessory von sich aus mitgibt.
 */
class FakePlatformAccessory extends FakeAccessory {
  constructor(
    displayName: string,
    public readonly UUID: string,
  ) {
    super();
    this.displayName = displayName;
    this.addService('AccessoryInformation', 'info');
  }
}

const STEHLAMPE: SwitchDto = {
  id: 'stehlampe',
  name: 'Stehlampe',
  room: 'Wohnzimmer',
  online: true,
  observedAt: '2026-09-20T09:00:00Z',
  state: 'OFF',
  critical: false,
  hint: '',
};

const BECKEN: ApplianceDto = {
  id: 'pool',
  name: 'Schwimmbecken',
  room: 'Garten',
  online: true,
  observedAt: '2026-09-20T09:00:00Z',
  functions: { PUMP: 'OFF', HEATER: 'OFF', LIGHT: 'OFF', FILTER: 'OFF' },
  temperature: { current: 20, target: 15, min: 8, max: 41 },
};

function snapshot(appliances: ApplianceDto[]): Snapshot {
  return { switches: [STEHLAMPE], appliances, covers: [], climate: [], sensors: [], smoke: [] };
}

/**
 * Baut die Plattform mit einer HAP-Attrappe und einem austauschbaren Snapshot. Der
 * Poll-Zyklus wird von Hand angestossen - kein Timer, kein Netz.
 */
function setup() {
  const registered: FakePlatformAccessory[] = [];
  const unregistered: FakePlatformAccessory[] = [];
  const listeners = new Map<string, () => void>();
  const api = {
    hap: {
      Service: new Proxy({}, { get: (_t, name) => String(name) }),
      Characteristic: new Proxy({}, { get: (_t, name) => String(name) }),
      uuid: { generate: (seed: string) => `uuid-${seed}` },
      HapStatusError: class extends Error {},
      HAPStatus: { SERVICE_COMMUNICATION_FAILURE: -70402 },
    },
    platformAccessory: FakePlatformAccessory,
    registerPlatformAccessories: (_p: string, _n: string, list: FakePlatformAccessory[]) => {
      registered.push(...list);
    },
    unregisterPlatformAccessories: (_p: string, _n: string, list: FakePlatformAccessory[]) => {
      unregistered.push(...list);
    },
    on: (event: string, handler: () => void) => {
      listeners.set(event, handler);
    },
  };
  const log = { debug: vi.fn(), info: vi.fn(), warn: vi.fn(), error: vi.fn() };
  const platform = new SmarthomePlatform(
    log as unknown as Logging,
    { platform: 'Smarthome', baseUrl: 'http://smarthome.test' },
    api as unknown as API,
  );
  let current = snapshot([BECKEN]);
  // Der echte Client spraeche mit dem Netz; hier liefert er den vorbereiteten Snapshot.
  (platform as unknown as { client: { snapshot: () => Promise<Snapshot> } }).client = {
    snapshot: async () => current,
  };
  const poll = () =>
    (platform as unknown as { poll: () => Promise<void> }).poll();
  return {
    platform,
    log,
    poll,
    registered,
    unregistered,
    setSnapshot: (s: Snapshot) => {
      current = s;
    },
    restore: (accessory: FakePlatformAccessory) =>
      platform.configureAccessory(accessory as unknown as PlatformAccessory),
  };
}

describe('SmarthomePlatform: stillgelegte Anlagen', () => {
  it('legt eine deaktivierte Anlage gar nicht erst an', async () => {
    const t = setup();
    t.setSnapshot(snapshot([{ ...BECKEN, online: false, active: false }]));

    await t.poll();

    // "Keine Antwort" waere falsch - die Anlage ist nicht kaputt, sie ist vom Strom.
    expect(t.registered.map((a) => a.displayName)).toEqual(['Stehlampe']);
  });

  it('entfernt eine Anlage aus HomeKit, sobald sie stillgelegt wird - auch im laufenden Betrieb', async () => {
    const t = setup();
    await t.poll();
    expect(t.registered.map((a) => a.displayName)).toEqual(['Stehlampe', 'Schwimmbecken']);

    t.setSnapshot(snapshot([{ ...BECKEN, online: false, active: false }]));
    await t.poll();

    expect(t.unregistered.map((a) => a.displayName)).toEqual(['Schwimmbecken']);
  });

  it('nimmt eine reaktivierte Anlage wieder auf', async () => {
    const t = setup();
    t.setSnapshot(snapshot([{ ...BECKEN, online: false, active: false }]));
    await t.poll();

    t.setSnapshot(snapshot([BECKEN]));
    await t.poll();

    expect(t.registered.map((a) => a.displayName)).toContain('Schwimmbecken');
  });

  it('raeumt ein aus dem Cache wiederhergestelltes Accessory weg, das im Snapshot fehlt', async () => {
    const t = setup();
    const alt = new FakePlatformAccessory('Altes Geraet', 'uuid-homebridge-smarthome:switch:alt');
    t.restore(alt);

    await t.poll();

    expect(t.unregistered).toContain(alt);
  });

  it('behaelt ein Accessory, wenn eine Anlage nur nicht erreichbar ist', async () => {
    const t = setup();
    await t.poll();

    t.setSnapshot(snapshot([{ ...BECKEN, online: false }]));
    await t.poll();

    // Nicht erreichbar ist ein Fehlerzustand, kein Grund, das Geraet aus HomeKit zu
    // loeschen - sonst verschwaenden Raeume und Automationen bei jedem Aussetzer.
    expect(t.unregistered).toEqual([]);
  });
});
