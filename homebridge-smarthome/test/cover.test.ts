import { describe, expect, it, vi } from 'vitest';
import { CoverHandler } from '../src/accessories/cover';
import type { ApiClient } from '../src/api-client';
import type { CoverDto } from '../src/types';
import { FakeAccessory, FakeService, HapStatusError, fakeAccessory, fakePlatform } from './harness';

const STORE: CoverDto = {
  id: 'store-links',
  name: 'Store links',
  room: 'Wohnzimmer',
  online: true,
  observedAt: '2026-08-06T20:00:00Z',
  position: 0,
};

function setup(initial: CoverDto = STORE) {
  const accessory = fakeAccessory();
  const client = {
    setCoverPosition: vi.fn(async () => {}),
    sendCoverCommand: vi.fn(async () => {}),
  };
  const handler = new CoverHandler(
    fakePlatform(),
    accessory,
    client as unknown as ApiClient,
    initial,
  );
  const service = (accessory as unknown as FakeAccessory).getService(
    'WindowCovering',
  ) as FakeService;
  return {
    handler,
    client,
    current: service.getCharacteristic('CurrentPosition'),
    target: service.getCharacteristic('TargetPosition'),
    state: service.getCharacteristic('PositionState'),
    hold: service.getCharacteristic('HoldPosition'),
  };
}

describe('CoverHandler', () => {
  it('reicht die Position unveraendert durch - beide Skalen zaehlen 0 = zu', () => {
    // Die Probe aufs Exempel gegen den Umsetzungsplan, der eine Invertierung behauptete:
    // 0 heisst in der REST-API wie in HomeKit "geschlossen".
    expect(setup({ ...STORE, position: 0 }).current.getHandler!()).toBe(0);
    expect(setup({ ...STORE, position: 100 }).current.getHandler!()).toBe(100);
    expect(setup({ ...STORE, position: 30 }).current.getHandler!()).toBe(30);
  });

  it('schickt die Zielposition unveraendert an die App', async () => {
    const { target, client } = setup();
    await target.setHandler!(70);
    expect(client.setCoverPosition).toHaveBeenCalledWith('store-links', 70);
    expect(target.getHandler!()).toBe(70);
  });

  it('meldet waehrend der Fahrt die Richtung', async () => {
    const { target, state } = setup({ ...STORE, position: 20 });
    await target.setHandler!(80);
    expect(state.getHandler!()).toBe(1); // INCREASING - faehrt auf
    const runter = setup({ ...STORE, position: 80 });
    await runter.target.setHandler!(20);
    expect(runter.state.getHandler!()).toBe(0); // DECREASING
  });

  it('meldet Stillstand, sobald das Ziel erreicht ist', async () => {
    const { handler, target, state, current } = setup({ ...STORE, position: 20 });
    await target.setHandler!(80);
    handler.update({ ...STORE, position: 80 });
    expect(state.getHandler!()).toBe(2); // STOPPED
    expect(current.value).toBe(80);
  });

  it('gibt das Ziel auf, wenn sich die Store nicht mehr bewegt', () => {
    // Die Store haelt auch mal bei 98 statt 100 - auf exakte Gleichheit ist kein
    // Verlass, sonst zeigte HomeKit dauerhaft "faehrt".
    const { handler, target, state } = setup({ ...STORE, position: 100 });
    void target.setHandler!(0);
    handler.update({ ...STORE, position: 2 });
    handler.update({ ...STORE, position: 2 });
    handler.update({ ...STORE, position: 2 });
    expect(state.getHandler!()).toBe(2);
    expect(target.getHandler!()).toBe(2);
  });

  it('folgt einer Fahrt, die das Dashboard ausgeloest hat', () => {
    const { handler, target, current } = setup({ ...STORE, position: 0 });
    handler.update({ ...STORE, position: 50 });
    handler.update({ ...STORE, position: 50 });
    handler.update({ ...STORE, position: 50 });
    expect(current.value).toBe(50);
    expect(target.getHandler!()).toBe(50);
  });

  it('haelt die Store ueber HoldPosition an', async () => {
    const { handler, hold, client, target } = setup({ ...STORE, position: 100 });
    void target.setHandler!(0);
    handler.update({ ...STORE, position: 60 });
    await hold.setHandler!(true);
    expect(client.sendCoverCommand).toHaveBeenCalledWith('store-links', 'STOP');
    expect(target.getHandler!()).toBe(60);
  });

  it('meldet eine offline gemeldete Store als nicht erreichbar', () => {
    const { current } = setup({ ...STORE, online: false });
    expect(() => current.getHandler!()).toThrow(HapStatusError);
  });

  it('gibt den Platzhalter -1 niemals als Position aus', () => {
    const { current } = setup({ ...STORE, position: -1 });
    expect(() => current.getHandler!()).toThrow(HapStatusError);
  });

  it('meldet einen fehlgeschlagenen Fahrbefehl als Fehler', async () => {
    const { target, client } = setup();
    client.setCoverPosition.mockRejectedValueOnce(new Error('HTTP 503'));
    await expect(target.setHandler!(50)).rejects.toThrow(HapStatusError);
  });
});
