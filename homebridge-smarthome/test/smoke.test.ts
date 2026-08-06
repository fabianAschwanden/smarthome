import { describe, expect, it } from 'vitest';
import { SmokeHandler } from '../src/accessories/smoke';
import type { SmokeDto } from '../src/types';
import { FakeAccessory, FakeService, fakeAccessory, fakePlatform } from './harness';

const MELDER: SmokeDto = {
  id: 'rauchmelder',
  name: 'Rauchmelder',
  room: 'Wohnzimmer',
  online: false,
  observedAt: '2026-08-06T20:00:00Z',
  alarm: 'OK',
  battery: -1,
};

function setup(initial: SmokeDto = MELDER) {
  const accessory = fakeAccessory();
  const handler = new SmokeHandler(fakePlatform(), accessory, initial);
  const service = (accessory as unknown as FakeAccessory).getService('SmokeSensor') as FakeService;
  return { handler, service };
}

describe('SmokeHandler', () => {
  it('meldet OK als "kein Rauch"', () => {
    const { service } = setup();
    expect(service.getCharacteristic('SmokeDetected').getHandler!()).toBe(0);
  });

  it('meldet ALARM als "Rauch erkannt"', () => {
    const { service } = setup({ ...MELDER, alarm: 'ALARM' });
    expect(service.getCharacteristic('SmokeDetected').getHandler!()).toBe(1);
  });

  it('meldet trotz offline den zuletzt bekannten Alarm', () => {
    // Batteriemelder funken sporadisch; ein dauerhaftes "keine Antwort" wuerde die
    // Kachel entwerten - und ein einmal gemeldeter ALARM muss sichtbar bleiben.
    const { service } = setup({ ...MELDER, alarm: 'ALARM', online: false });
    expect(service.getCharacteristic('SmokeDetected').getHandler!()).toBe(1);
  });

  it('meldet ohne bekannten Batteriestand gar keinen', () => {
    const { service } = setup();
    expect(service.characteristics.has('StatusLowBattery')).toBe(false);
  });

  it('meldet einen niedrigen Batteriestand', () => {
    const { service } = setup({ ...MELDER, battery: 15 });
    expect(service.getCharacteristic('StatusLowBattery').getHandler!()).toBe(1);
  });

  it('meldet einen ausreichenden Batteriestand als normal', () => {
    const { service } = setup({ ...MELDER, battery: 80 });
    expect(service.getCharacteristic('StatusLowBattery').getHandler!()).toBe(0);
  });

  it('uebernimmt einen neuen Alarm', () => {
    const { handler, service } = setup();
    handler.update({ ...MELDER, alarm: 'ALARM' });
    expect(service.getCharacteristic('SmokeDetected').value).toBe(1);
  });

  it('deutet einen unbekannten Zustand nicht als Alarm', () => {
    const { service } = setup({ ...MELDER, alarm: 'UNBEKANNT' });
    expect(service.getCharacteristic('SmokeDetected').getHandler!()).toBe(0);
  });
});
