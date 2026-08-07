import { describe, expect, it, vi } from 'vitest';
import { ApplianceHandler } from '../src/accessories/appliance';
import type { ApiClient } from '../src/api-client';
import type { ApplianceDto } from '../src/types';
import { FakeAccessory, FakeService, HapStatusError, fakeAccessory, fakePlatform } from './harness';

const WHIRLPOOL: ApplianceDto = {
  id: 'whirlpool',
  name: 'Whirlpool',
  room: 'Wellness',
  online: true,
  observedAt: '2026-08-07T09:00:00Z',
  functions: { PUMP: 'OFF', HEATER: 'ON', LIGHT: 'OFF', MASSAGE: 'OFF', FILTER: 'OFF' },
  temperature: { current: 30, target: 25, min: 8, max: 41 },
};

const BECKEN: ApplianceDto = {
  ...WHIRLPOOL,
  id: 'pool',
  name: 'Schwimmbecken',
  room: 'Garten',
  functions: { PUMP: 'OFF', HEATER: 'ON', LIGHT: 'OFF', FILTER: 'OFF' },
  temperature: { current: 30, target: 15, min: 8, max: 41 },
};

function setup(initial: ApplianceDto = WHIRLPOOL) {
  const accessory = fakeAccessory();
  const client = {
    setApplianceFunction: vi.fn(async () => {}),
    setApplianceTemperature: vi.fn(async () => {}),
  };
  const handler = new ApplianceHandler(
    fakePlatform(),
    accessory,
    client as unknown as ApiClient,
    initial,
  );
  const fake = accessory as unknown as FakeAccessory;
  return {
    handler,
    client,
    fake,
    thermostat: fake.getService('Thermostat') as FakeService,
    fn: (name: string) => fake.getServiceById(name === 'LIGHT' ? 'Lightbulb' : 'Switch', name),
  };
}

describe('ApplianceHandler – Aufbau', () => {
  it('macht aus dem Licht eine Lampe und aus dem Rest Schalter', () => {
    // In HomeKit ist das ein Unterschied: Eine Lampe laesst sich mit "alle Lichter aus"
    // ansprechen, ein Schalter nicht.
    const { fake } = setup();
    expect(fake.getServiceById('Lightbulb', 'LIGHT')).toBeDefined();
    expect(fake.getServiceById('Switch', 'PUMP')).toBeDefined();
    expect(fake.getServiceById('Switch', 'MASSAGE')).toBeDefined();
    expect(fake.getServiceById('Switch', 'FILTER')).toBeDefined();
  });

  it('zeigt die Heizung nur als Thermostat, nicht zusaetzlich als Schalter', () => {
    // Zwei Bedienelemente fuer dieselbe Sache waeren eine Einladung zum Widerspruch.
    const { fake, thermostat } = setup();
    expect(thermostat).toBeDefined();
    expect(fake.getServiceById('Switch', 'HEATER')).toBeUndefined();
  });

  it('legt nur die Funktionen an, die das Geraet meldet', () => {
    // Das Becken kennt keine Massage.
    const { fake } = setup(BECKEN);
    expect(fake.getServiceById('Switch', 'MASSAGE')).toBeUndefined();
    expect(fake.getServiceById('Switch', 'FILTER')).toBeDefined();
  });

  it('benennt jeden Dienst eigen, damit die Home-App sie unterscheiden kann', () => {
    const { fn } = setup();
    expect(fn('PUMP')!.getCharacteristic('ConfiguredName').value).toBe('Whirlpool Pumpe');
    expect(fn('LIGHT')!.getCharacteristic('ConfiguredName').value).toBe('Whirlpool Licht');
  });
});

describe('ApplianceHandler – Thermostat', () => {
  it('bietet nur AUS und HEIZEN an', () => {
    // Kuehlen kann die Anlage nicht; einen Modus anzubieten, den das Geraet nicht hat,
    // waere ein Versprechen, das beim Antippen bricht.
    const { thermostat } = setup();
    expect(thermostat.getCharacteristic('TargetHeatingCoolingState').props).toEqual({
      validValues: [0, 1],
    });
  });

  it('haelt sich an die Grenzen der Anlage', () => {
    const { thermostat } = setup();
    expect(thermostat.getCharacteristic('TargetTemperature').props).toEqual({
      minValue: 8,
      maxValue: 41,
      minStep: 1,
    });
    expect(thermostat.getCharacteristic('TargetTemperature').value).toBe(25);
  });

  it('meldet die laufende Heizung als HEIZEN', () => {
    const { thermostat } = setup();
    expect(thermostat.getCharacteristic('CurrentHeatingCoolingState').getHandler!()).toBe(1);
  });

  it('meldet die abgeschaltete Heizung als AUS', () => {
    const { thermostat } = setup({ ...WHIRLPOOL, functions: { ...WHIRLPOOL.functions, HEATER: 'OFF' } });
    expect(thermostat.getCharacteristic('CurrentHeatingCoolingState').getHandler!()).toBe(0);
  });

  it('schaltet die Heizung ueber den Modus', async () => {
    const { thermostat, client } = setup();
    await thermostat.getCharacteristic('TargetHeatingCoolingState').setHandler!(0);
    expect(client.setApplianceFunction).toHaveBeenCalledWith('whirlpool', 'HEATER', false);
  });

  it('rundet die Soll-Temperatur auf ganze Grad', async () => {
    const { thermostat, client } = setup();
    await thermostat.getCharacteristic('TargetTemperature').setHandler!(28.5);
    expect(client.setApplianceTemperature).toHaveBeenCalledWith('whirlpool', 29);
  });

  it('gibt den Platzhalter -1 niemals als Ist-Temperatur aus', () => {
    const { thermostat } = setup({
      ...WHIRLPOOL,
      temperature: { ...WHIRLPOOL.temperature!, current: -1 },
    });
    expect(() => thermostat.getCharacteristic('CurrentTemperature').getHandler!()).toThrow(
      HapStatusError,
    );
  });
});

describe('ApplianceHandler – Funktionen schalten', () => {
  it('schaltet die Pumpe', async () => {
    const { fn, client } = setup();
    await fn('PUMP')!.getCharacteristic('On').setHandler!(true);
    expect(client.setApplianceFunction).toHaveBeenCalledWith('whirlpool', 'PUMP', true);
    expect(fn('PUMP')!.getCharacteristic('On').getHandler!()).toBe(true);
  });

  it('meldet eine offline gemeldete Anlage als nicht erreichbar', () => {
    const { fn } = setup({ ...WHIRLPOOL, online: false });
    expect(() => fn('PUMP')!.getCharacteristic('On').getHandler!()).toThrow(HapStatusError);
  });

  it('behaelt den Zustand, wenn die App den Befehl ablehnt', async () => {
    const { fn, client } = setup();
    client.setApplianceFunction.mockRejectedValueOnce(new Error('HTTP 503'));
    await expect(fn('PUMP')!.getCharacteristic('On').setHandler!(true)).rejects.toThrow(
      HapStatusError,
    );
    expect(fn('PUMP')!.getCharacteristic('On').getHandler!()).toBe(false);
  });

  it('uebernimmt Zustaende aus dem Poll-Zyklus', () => {
    const { handler, fn, thermostat } = setup();
    handler.update({
      ...WHIRLPOOL,
      functions: { ...WHIRLPOOL.functions, PUMP: 'ON', HEATER: 'OFF' },
      temperature: { current: 32, target: 27, min: 8, max: 41 },
    });
    expect(fn('PUMP')!.getCharacteristic('On').value).toBe(true);
    expect(thermostat.getCharacteristic('CurrentHeatingCoolingState').value).toBe(0);
    expect(thermostat.getCharacteristic('TargetTemperature').value).toBe(27);
  });
});
