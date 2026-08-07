import { describe, expect, it, vi } from 'vitest';
import { ClimateHandler } from '../src/accessories/climate';
import type { ApiClient } from '../src/api-client';
import type { ClimateDto } from '../src/types';
import { FakeAccessory, FakeService, HapStatusError, fakeAccessory, fakePlatform } from './harness';

const KLIMA: ClimateDto = {
  id: 'klima',
  name: 'Klimaanlage',
  room: 'Wohnzimmer',
  online: true,
  observedAt: '2026-08-06T20:00:00Z',
  power: false,
  mode: 'COOL',
  currentTemp: 25,
  targetTemp: 17,
  outdoorTemp: 27,
  boost: false,
};

function setup(initial: ClimateDto = KLIMA) {
  const accessory = fakeAccessory();
  const client = {
    setClimatePower: vi.fn(async () => {}),
    setClimateMode: vi.fn(async () => {}),
    setClimateTarget: vi.fn(async () => {}),
  };
  const handler = new ClimateHandler(
    fakePlatform(),
    accessory,
    client as unknown as ApiClient,
    initial,
  );
  const service = (accessory as unknown as FakeAccessory).getService('HeaterCooler') as FakeService;
  return {
    handler,
    client,
    service,
    active: service.getCharacteristic('Active'),
    currentState: service.getCharacteristic('CurrentHeaterCoolerState'),
    targetState: service.getCharacteristic('TargetHeaterCoolerState'),
    currentTemp: service.getCharacteristic('CurrentTemperature'),
    cooling: service.getCharacteristic('CoolingThresholdTemperature'),
    heating: service.getCharacteristic('HeatingThresholdTemperature'),
  };
}

describe('ClimateHandler – Modus-Matrix', () => {
  it('zeigt COOL und HEAT unveraendert', () => {
    expect(setup({ ...KLIMA, mode: 'COOL' }).targetState.getHandler!()).toBe(2);
    expect(setup({ ...KLIMA, mode: 'HEAT' }).targetState.getHandler!()).toBe(1);
  });

  it('zeigt AUTO als AUTO', () => {
    expect(setup({ ...KLIMA, mode: 'AUTO' }).targetState.getHandler!()).toBe(0);
  });

  it('zeigt FAN als AUTO, weil HomeKit den Modus nicht kennt', () => {
    expect(setup({ ...KLIMA, mode: 'FAN' }).targetState.getHandler!()).toBe(0);
  });

  it('zeigt einen unbekannten Modus als AUTO statt zu raten', () => {
    expect(setup({ ...KLIMA, mode: 'DRY' }).targetState.getHandler!()).toBe(0);
  });

  it('setzt aus HomeKit nur Modi, die HomeKit auch kennt', async () => {
    const { targetState, client } = setup({ ...KLIMA, mode: 'FAN' });
    await targetState.setHandler!(0);
    expect(client.setClimateMode).toHaveBeenCalledWith('klima', 'AUTO');
    await targetState.setHandler!(2);
    expect(client.setClimateMode).toHaveBeenLastCalledWith('klima', 'COOL');
    await targetState.setHandler!(1);
    expect(client.setClimateMode).toHaveBeenLastCalledWith('klima', 'HEAT');
  });
});

describe('ClimateHandler – was die Anlage gerade tut', () => {
  it('meldet die ausgeschaltete Anlage als inaktiv', () => {
    const { active, currentState } = setup();
    expect(active.getHandler!()).toBe(0);
    expect(currentState.getHandler!()).toBe(0);
  });

  it('meldet Kuehlen, solange es zu warm ist', () => {
    const { currentState } = setup({ ...KLIMA, power: true, currentTemp: 25, targetTemp: 17 });
    expect(currentState.getHandler!()).toBe(3);
  });

  it('meldet Leerlauf, sobald die Soll-Temperatur erreicht ist', () => {
    const { currentState } = setup({ ...KLIMA, power: true, currentTemp: 17, targetTemp: 17 });
    expect(currentState.getHandler!()).toBe(1);
  });

  it('meldet Heizen, solange es zu kalt ist', () => {
    const { currentState } = setup({
      ...KLIMA,
      power: true,
      mode: 'HEAT',
      currentTemp: 18,
      targetTemp: 22,
    });
    expect(currentState.getHandler!()).toBe(2);
  });

  it('leitet die Richtung im AUTO-Modus aus der Ist-Temperatur ab', () => {
    const warm = setup({ ...KLIMA, power: true, mode: 'AUTO', currentTemp: 26, targetTemp: 22 });
    expect(warm.currentState.getHandler!()).toBe(3);
    const kalt = setup({ ...KLIMA, power: true, mode: 'AUTO', currentTemp: 18, targetTemp: 22 });
    expect(kalt.currentState.getHandler!()).toBe(2);
  });

  it('meldet FAN als Leerlauf - die Anlage laeuft, regelt aber nicht', () => {
    const { currentState } = setup({ ...KLIMA, power: true, mode: 'FAN' });
    expect(currentState.getHandler!()).toBe(1);
  });
});

describe('ClimateHandler – Temperaturen', () => {
  it('haelt sich an die Grenzen der Domaene', () => {
    const { cooling, heating } = setup();
    expect(cooling.props).toEqual({ minValue: 16, maxValue: 30, minStep: 1 });
    expect(heating.props).toEqual({ minValue: 16, maxValue: 30, minStep: 1 });
  });

  it('zeigt in beiden Schwellwerten dieselbe Soll-Temperatur', () => {
    const { cooling, heating } = setup();
    expect(cooling.getHandler!()).toBe(17);
    expect(heating.getHandler!()).toBe(17);
  });

  it('setzt den Startwert in die Grenzen, statt HAPs Vorgabe stehen zu lassen', () => {
    // HAP legt die Schwellwerte mit 10 bzw. 0 °C an - beides unter unserem Minimum.
    // Ohne gesetzten Startwert meldet HomeKit das bei jedem Start als illegalen Wert.
    const { cooling, heating } = setup();
    expect(cooling.value).toBe(17);
    expect(heating.value).toBe(17);
  });

  it('rundet auf ganze Grad, weil die App nur solche annimmt', async () => {
    const { cooling, client } = setup();
    await cooling.setHandler!(21.5);
    expect(client.setClimateTarget).toHaveBeenCalledWith('klima', 22);
  });

  it('gibt den Platzhalter -1 niemals als Ist-Temperatur aus', () => {
    const { currentTemp } = setup({ ...KLIMA, currentTemp: -1 });
    expect(() => currentTemp.getHandler!()).toThrow(HapStatusError);
  });

  it('meldet eine offline gemeldete Anlage als nicht erreichbar', () => {
    const { currentTemp } = setup({ ...KLIMA, online: false });
    expect(() => currentTemp.getHandler!()).toThrow(HapStatusError);
  });
});

describe('ClimateHandler – Schalten', () => {
  it('schaltet die Anlage ein', async () => {
    const { active, client } = setup();
    await active.setHandler!(1);
    expect(client.setClimatePower).toHaveBeenCalledWith('klima', true);
    expect(active.getHandler!()).toBe(1);
  });

  it('meldet einen fehlgeschlagenen Befehl als Fehler und behaelt den Zustand', async () => {
    const { active, client } = setup();
    client.setClimatePower.mockRejectedValueOnce(new Error('HTTP 503'));
    await expect(active.setHandler!(1)).rejects.toThrow(HapStatusError);
    expect(active.getHandler!()).toBe(0);
  });

  it('uebernimmt einen Zustandswechsel aus dem Poll-Zyklus', () => {
    const { handler, active, currentState } = setup();
    handler.update({ ...KLIMA, power: true, currentTemp: 25, targetTemp: 17 });
    expect(active.value).toBe(1);
    expect(currentState.value).toBe(3);
  });
});
