/** Minimale HAP-Attrappe: gerade so viel, wie die Handler tatsaechlich anfassen. */
import type { PlatformAccessory } from 'homebridge';
import type { SmarthomePlatform } from '../src/platform';

export class FakeCharacteristic {
  value: unknown;
  props?: Record<string, unknown>;
  getHandler?: () => unknown;
  setHandler?: (value: unknown) => Promise<void>;

  setProps(props: Record<string, unknown>): this {
    this.props = props;
    return this;
  }

  updateValue(value: unknown): this {
    this.value = value;
    return this;
  }

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

  /**
   * Schluessel ist immer der primitive String: Die Attrappe reicht fuer Merkmale mit
   * Konstanten (SmokeDetected) String-Objekte herein, und die waeren als Map-Schluessel
   * nie gleich einem Literal aus dem Test.
   */
  getCharacteristic(name: string): FakeCharacteristic {
    const key = String(name);
    let characteristic = this.characteristics.get(key);
    if (!characteristic) {
      characteristic = new FakeCharacteristic();
      this.characteristics.set(key, characteristic);
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
  /** Schluessel ist "typ" bzw. "typ:subtype" - eine Anlage haelt mehrere Schalter. */
  readonly services = new Map<string, FakeService>();
  displayName = '';
  context: Record<string, unknown> = {};

  getService(type: string): FakeService | undefined {
    return this.services.get(String(type));
  }

  getServiceById(type: string, subtype: string): FakeService | undefined {
    return this.services.get(`${String(type)}:${subtype}`);
  }

  addService(type: string, name: string, subtype?: string): FakeService {
    const service = new FakeService(String(type));
    service.setCharacteristic('Name', name);
    this.services.set(subtype ? `${String(type)}:${subtype}` : String(type), service);
    return service;
  }

  removeService(service: FakeService): void {
    this.services.delete(service.type);
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
    Service: {
      AccessoryInformation: 'AccessoryInformation',
      Switch: 'Switch',
      TemperatureSensor: 'TemperatureSensor',
      HumiditySensor: 'HumiditySensor',
      SmokeSensor: 'SmokeSensor',
      WindowCovering: 'WindowCovering',
      HeaterCooler: 'HeaterCooler',
      Thermostat: 'Thermostat',
      Lightbulb: 'Lightbulb',
    },
    Characteristic: {
      Manufacturer: 'Manufacturer',
      Model: 'Model',
      SerialNumber: 'SerialNumber',
      On: 'On',
      CurrentTemperature: 'CurrentTemperature',
      TargetTemperature: 'TargetTemperature',
      ConfiguredName: 'ConfiguredName',
      CurrentHeatingCoolingState: Object.assign('CurrentHeatingCoolingState', {
        OFF: 0,
        HEAT: 1,
        COOL: 2,
      }),
      TargetHeatingCoolingState: Object.assign('TargetHeatingCoolingState', {
        OFF: 0,
        HEAT: 1,
        COOL: 2,
        AUTO: 3,
      }),
      CurrentRelativeHumidity: 'CurrentRelativeHumidity',
      // Die Konstanten entsprechen den HAP-Werten.
      SmokeDetected: Object.assign('SmokeDetected', {
        SMOKE_NOT_DETECTED: 0,
        SMOKE_DETECTED: 1,
      }),
      CurrentPosition: 'CurrentPosition',
      CoolingThresholdTemperature: 'CoolingThresholdTemperature',
      HeatingThresholdTemperature: 'HeatingThresholdTemperature',
      Active: Object.assign('Active', { INACTIVE: 0, ACTIVE: 1 }),
      CurrentHeaterCoolerState: Object.assign('CurrentHeaterCoolerState', {
        INACTIVE: 0,
        IDLE: 1,
        HEATING: 2,
        COOLING: 3,
      }),
      TargetHeaterCoolerState: Object.assign('TargetHeaterCoolerState', {
        AUTO: 0,
        HEAT: 1,
        COOL: 2,
      }),
      TargetPosition: 'TargetPosition',
      HoldPosition: 'HoldPosition',
      PositionState: Object.assign('PositionState', {
        DECREASING: 0,
        INCREASING: 1,
        STOPPED: 2,
      }),
      StatusLowBattery: Object.assign('StatusLowBattery', {
        BATTERY_LEVEL_NORMAL: 0,
        BATTERY_LEVEL_LOW: 1,
      }),
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
