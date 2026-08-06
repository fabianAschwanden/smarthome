/** Minimale HAP-Attrappe: gerade so viel, wie die Handler tatsaechlich anfassen. */
import type { PlatformAccessory } from 'homebridge';
import type { SmarthomePlatform } from '../src/platform';

export class FakeCharacteristic {
  value: unknown;
  getHandler?: () => unknown;
  setHandler?: (value: unknown) => Promise<void>;

  onGet(handler: () => unknown): this {
    this.getHandler = handler;
    return this;
  }

  onSet(handler: (value: unknown) => Promise<void>): this {
    this.setHandler = handler;
    return this;
  }
}

export class FakeService {
  readonly characteristics = new Map<string, FakeCharacteristic>();

  constructor(public readonly type: string) {}

  getCharacteristic(name: string): FakeCharacteristic {
    let characteristic = this.characteristics.get(name);
    if (!characteristic) {
      characteristic = new FakeCharacteristic();
      this.characteristics.set(name, characteristic);
    }
    return characteristic;
  }

  setCharacteristic(name: string, value: unknown): this {
    this.getCharacteristic(name).value = value;
    return this;
  }

  updateCharacteristic(name: string, value: unknown): this {
    this.getCharacteristic(name).value = value;
    return this;
  }
}

export class FakeAccessory {
  readonly services = new Map<string, FakeService>();
  displayName = '';
  context: Record<string, unknown> = {};

  getService(type: string): FakeService | undefined {
    return this.services.get(type);
  }

  addService(type: string, name: string): FakeService {
    const service = new FakeService(type);
    service.setCharacteristic('Name', name);
    this.services.set(type, service);
    return service;
  }
}

export class HapStatusError extends Error {
  constructor(public readonly hapStatus: number) {
    super(`HapStatusError ${hapStatus}`);
  }
}

export const SERVICE_COMMUNICATION_FAILURE = -70402;

export interface FakePlatform {
  warnings: string[];
}

/** Liefert ein Objekt, das sich fuer die Handler wie die Plattform verhaelt. */
export function fakePlatform(): SmarthomePlatform & FakePlatform {
  const warnings: string[] = [];
  return {
    warnings,
    Service: { AccessoryInformation: 'AccessoryInformation', Switch: 'Switch' },
    Characteristic: {
      Manufacturer: 'Manufacturer',
      Model: 'Model',
      SerialNumber: 'SerialNumber',
      On: 'On',
    },
    log: {
      debug: () => {},
      info: () => {},
      warn: (message: string) => warnings.push(message),
      error: () => {},
    },
    api: {
      hap: { HapStatusError, HAPStatus: { SERVICE_COMMUNICATION_FAILURE } },
    },
  } as unknown as SmarthomePlatform & FakePlatform;
}

/** Ein Accessory, das bereits die Informations-Services besitzt. */
export function fakeAccessory(): PlatformAccessory {
  const accessory = new FakeAccessory();
  accessory.addService('AccessoryInformation', 'info');
  return accessory as unknown as PlatformAccessory;
}
