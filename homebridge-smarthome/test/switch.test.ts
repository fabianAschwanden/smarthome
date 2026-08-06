import { describe, expect, it, vi } from 'vitest';
import { SwitchHandler } from '../src/accessories/switch';
import type { ApiClient } from '../src/api-client';
import type { SwitchDto } from '../src/types';
import {
  FakeAccessory,
  FakeService,
  HapStatusError,
  fakeAccessory,
  fakePlatform,
} from './harness';

const STEHLAMPE: SwitchDto = {
  id: 'stehlampe',
  name: 'Stehlampe',
  room: 'Wohnzimmer',
  online: true,
  observedAt: '2026-08-06T20:00:00Z',
  state: 'OFF',
  critical: false,
  hint: '',
};

function setup(initial: SwitchDto = STEHLAMPE, allowCriticalOff = false) {
  const platform = fakePlatform();
  const accessory = fakeAccessory();
  const client = { setSwitch: vi.fn(async () => {}) };
  const handler = new SwitchHandler(
    platform,
    accessory,
    client as unknown as ApiClient,
    initial,
    allowCriticalOff,
  );
  const service = (accessory as unknown as FakeAccessory).getService('Switch') as FakeService;
  return { platform, handler, client, on: service.getCharacteristic('On') };
}

describe('SwitchHandler', () => {
  it('bildet ON auf true ab', () => {
    const { on } = setup({ ...STEHLAMPE, state: 'ON' });
    expect(on.getHandler!()).toBe(true);
  });

  it('bildet OFF auf false ab', () => {
    const { on } = setup();
    expect(on.getHandler!()).toBe(false);
  });

  it('meldet ein offline gemeldetes Geraet als nicht erreichbar', () => {
    const { on } = setup({ ...STEHLAMPE, state: 'ON', online: false });
    // Kein alter Wert: HomeKit soll "keine Antwort" zeigen statt einen Zustand,
    // den niemand mehr bestaetigen kann.
    expect(() => on.getHandler!()).toThrow(HapStatusError);
  });

  it('schaltet ueber die API und uebernimmt den Zustand sofort', async () => {
    const { on, client } = setup();
    await on.setHandler!(true);
    expect(client.setSwitch).toHaveBeenCalledWith('stehlampe', true, false);
    expect(on.getHandler!()).toBe(true);
  });

  it('umgeht die Bestaetigung eines kritischen Schalters nicht', async () => {
    const { on, client } = setup({ ...STEHLAMPE, id: 'homecinema', critical: true, state: 'ON' });
    await on.setHandler!(false).catch(() => {});
    expect(client.setSwitch).toHaveBeenCalledWith('homecinema', false, false);
  });

  it('umgeht sie nur, wenn der Betreiber es erlaubt hat', async () => {
    const { on, client } = setup(
      { ...STEHLAMPE, id: 'homecinema', critical: true, state: 'ON' },
      true,
    );
    await on.setHandler!(false);
    expect(client.setSwitch).toHaveBeenCalledWith('homecinema', false, true);
  });

  it('erlaubt das Einschalten eines kritischen Schalters ohne Bestaetigung', async () => {
    const { on, client } = setup({ ...STEHLAMPE, id: 'homecinema', critical: true });
    await on.setHandler!(true);
    expect(client.setSwitch).toHaveBeenCalledWith('homecinema', true, false);
  });

  it('haelt den alten Zustand, wenn die API den Befehl ablehnt', async () => {
    const { on, client, platform } = setup({ ...STEHLAMPE, critical: true, state: 'ON' });
    client.setSwitch.mockRejectedValueOnce(new Error('/api/switches/stehlampe: HTTP 409'));
    await expect(on.setHandler!(false)).rejects.toThrow(HapStatusError);
    expect(on.getHandler!()).toBe(true);
    expect(platform.warnings.join(' ')).toContain('allowCriticalOff');
  });

  it('meldet HomeKit nur echte Aenderungen', () => {
    const { handler, on } = setup();
    handler.update({ ...STEHLAMPE, state: 'ON' });
    expect(on.value).toBe(true);
  });
});
