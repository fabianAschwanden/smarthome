import { describe, expect, it } from 'vitest';
import { SensorHandler } from '../src/accessories/sensor';
import type { SensorDto } from '../src/types';
import { FakeAccessory, FakeService, HapStatusError, fakeAccessory, fakePlatform } from './harness';

const INNEN: SensorDto = {
  id: 'innen',
  name: 'Innen',
  room: 'Wohnzimmer',
  online: true,
  observedAt: '2026-08-06T20:00:00Z',
  temperature: 26.2,
  humidity: 58,
};

function setup(initial: SensorDto = INNEN) {
  const accessory = fakeAccessory();
  const handler = new SensorHandler(fakePlatform(), accessory, initial);
  const fake = accessory as unknown as FakeAccessory;
  return {
    handler,
    temperature: (fake.getService('TemperatureSensor') as FakeService).getCharacteristic(
      'CurrentTemperature',
    ),
    humidity: fake.getService('HumiditySensor'),
  };
}

describe('SensorHandler', () => {
  it('meldet Temperatur und Feuchte des Innensensors', () => {
    const { temperature, humidity } = setup();
    expect(temperature.getHandler!()).toBe(26.2);
    expect(humidity!.getCharacteristic('CurrentRelativeHumidity').getHandler!()).toBe(58);
  });

  it('legt ohne gemeldete Feuchte keinen Feuchte-Dienst an', () => {
    // -1 heisst "unbekannt" - eine leere Kachel waere schlimmer als gar keine.
    const { humidity } = setup({ ...INNEN, humidity: -1 });
    expect(humidity).toBeUndefined();
  });

  it('meldet den Aussensensor mit Feuchte, wie das Dashboard ihn zeigt', () => {
    const { humidity } = setup({ ...INNEN, id: 'aussen', name: 'Aussen', humidity: 66 });
    expect(humidity!.getCharacteristic('CurrentRelativeHumidity').getHandler!()).toBe(66);
  });

  it('meldet einen offline gemeldeten Sensor als nicht erreichbar', () => {
    const { temperature } = setup({ ...INNEN, online: false });
    expect(() => temperature.getHandler!()).toThrow(HapStatusError);
  });

  it('gibt den Platzhalter -1000 niemals als Messwert aus', () => {
    const { temperature } = setup({ ...INNEN, temperature: -1000 });
    expect(() => temperature.getHandler!()).toThrow(HapStatusError);
  });

  it('uebernimmt neue Messwerte', () => {
    const { handler, temperature, humidity } = setup();
    handler.update({ ...INNEN, temperature: 21.4, humidity: 61 });
    expect(temperature.value).toBe(21.4);
    expect(humidity!.getCharacteristic('CurrentRelativeHumidity').value).toBe(61);
  });

  it('schiebt keinen Wert nach, solange der Sensor offline ist', () => {
    const { handler, temperature } = setup();
    handler.update({ ...INNEN, online: false, temperature: -1000 });
    expect(temperature.value).toBeUndefined();
  });
});
