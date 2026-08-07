import type { PlatformAccessory, Service as HapService } from 'homebridge';
import { ApiClient } from '../api-client';
import type { DeviceHandler, SmarthomePlatform } from '../platform';
import {
  ApplianceDto,
  CLIMATE_TEMP_UNKNOWN,
  DeviceBase,
  HEATER_FUNCTION,
  LIGHT_FUNCTION,
} from '../types';

/**
 * Whirlpool und Schwimmbecken (Use Case 6).
 *
 * <p>Eine Anlage ist kein einzelnes HomeKit-Geraet, sondern ein Buendel: Die Heizung
 * wird zum Thermostat, das Licht zur Lampe, alles Uebrige (Pumpe, Massage, Filter) zu
 * je einem Schalter. Welche Funktionen es gibt, sagt das Geraet selbst - der Whirlpool
 * kennt Massage, das Becken nicht. Deshalb wird ueber die gemeldeten Funktionen
 * iteriert, statt sie hier aufzuzaehlen.
 *
 * <p>Der Thermostat kennt <b>nur HEIZEN</b> - weder Kuehlen noch Aus. Die Heizung eines
 * Gecko-Spas laesst sich gar nicht schalten: Sie ist dauerhaft aktiv und folgt der
 * Soll-Temperatur; die App weist ein Ein/Aus mit 503 zurueck. Einen Modus anzubieten,
 * den das Geraet nicht hat, waere ein Versprechen, das beim Antippen bricht.
 */
export class ApplianceHandler implements DeviceHandler {
  private state: ApplianceDto;
  /** Funktionsname -> Dienst, fuer alle als Schalter oder Lampe abgebildeten Funktionen. */
  private readonly switchServices = new Map<string, HapService>();
  private readonly hasThermostat: boolean;

  constructor(
    private readonly platform: SmarthomePlatform,
    private readonly accessory: PlatformAccessory,
    private readonly client: ApiClient,
    initial: ApplianceDto,
  ) {
    this.state = initial;
    this.hasThermostat = initial.temperature !== null && HEATER_FUNCTION in initial.functions;

    this.accessory
      .getService(platform.Service.AccessoryInformation)!
      .setCharacteristic(platform.Characteristic.Manufacturer, 'smarthome')
      .setCharacteristic(platform.Characteristic.Model, 'Wellness-Anlage')
      .setCharacteristic(platform.Characteristic.SerialNumber, initial.id);

    if (this.hasThermostat) {
      this.setUpThermostat(initial);
    }

    for (const fn of Object.keys(initial.functions)) {
      if (fn === HEATER_FUNCTION) {
        // Nie als Schalter: Die Heizung laesst sich nicht schalten, ein Knopf dafuer
        // koennte nur scheitern. Bedient wird sie ueber die Soll-Temperatur.
        continue;
      }
      this.setUpFunction(initial, fn);
    }
  }

  private setUpThermostat(initial: ApplianceDto): void {
    const platform = this.platform;
    const temperature = initial.temperature!;
    const service =
      this.accessory.getService(platform.Service.Thermostat) ??
      // Ohne Subtyp: Der Thermostat ist auf der Anlage einzigartig, und nur so findet
      // ihn getService() spaeter im Poll-Zyklus wieder.
      this.accessory.addService(platform.Service.Thermostat, initial.name);

    service
      .getCharacteristic(platform.Characteristic.CurrentHeatingCoolingState)
      .onGet(() => this.heatingState());
    service
      .getCharacteristic(platform.Characteristic.TargetHeatingCoolingState)
      // Nur HEIZEN: Die Anlage kann weder kuehlen noch ihre Heizung abschalten.
      // HomeKit zeigt den Modus damit als feststehend und bietet kein Umschalten an.
      .setProps({ validValues: [platform.Characteristic.TargetHeatingCoolingState.HEAT] })
      .onGet(() => platform.Characteristic.TargetHeatingCoolingState.HEAT);
    service
      .getCharacteristic(platform.Characteristic.CurrentTemperature)
      .onGet(() => this.currentTemp());
    service
      .getCharacteristic(platform.Characteristic.TargetTemperature)
      .setProps({ minValue: temperature.min, maxValue: temperature.max, minStep: 1 })
      // Startwert nach setProps, sonst liegt HAPs Vorgabe ausserhalb der Grenzen.
      .updateValue(temperature.target)
      .onGet(() => this.state.temperature?.target ?? temperature.min)
      .onSet(async (value) => this.writeTarget(Math.round(Number(value))));
  }

  private setUpFunction(initial: ApplianceDto, fn: string): void {
    const platform = this.platform;
    const isLight = fn === LIGHT_FUNCTION;
    const type = isLight ? platform.Service.Lightbulb : platform.Service.Switch;
    const label = `${initial.name} ${functionLabel(fn)}`;

    const service =
      this.accessory.getServiceById(type, fn) ?? this.accessory.addService(type, label, fn);
    // Ohne eigenen Namen je Dienst zeigt die Home-App mehrere gleich heissende Kacheln.
    service.setCharacteristic(platform.Characteristic.Name, label);
    service.setCharacteristic(platform.Characteristic.ConfiguredName, label);

    service
      .getCharacteristic(platform.Characteristic.On)
      .onGet(() => this.isOn(fn))
      .onSet(async (value) => this.writeFunction(fn, value === true));

    this.switchServices.set(fn, service);
  }

  update(device: DeviceBase): void {
    const next = device as ApplianceDto;
    this.state = next;
    if (!next.online) {
      return;
    }
    for (const [fn, service] of this.switchServices) {
      service.updateCharacteristic(this.platform.Characteristic.On, next.functions[fn] === 'ON');
    }
    if (!this.hasThermostat) {
      return;
    }
    const service = this.accessory.getService(this.platform.Service.Thermostat);
    service?.updateCharacteristic(
      this.platform.Characteristic.CurrentHeatingCoolingState,
      this.heatingState(),
    );
    if (next.temperature && next.temperature.current !== CLIMATE_TEMP_UNKNOWN) {
      service?.updateCharacteristic(
        this.platform.Characteristic.CurrentTemperature,
        next.temperature.current,
      );
    }
    if (next.temperature) {
      service?.updateCharacteristic(
        this.platform.Characteristic.TargetTemperature,
        next.temperature.target,
      );
    }
  }

  private isOn(fn: string): boolean {
    if (!this.state.online) {
      throw this.unavailable();
    }
    return this.state.functions[fn] === 'ON';
  }

  /**
   * HomeKit unterscheidet im Thermostat nur AUS, HEIZEN und KUEHLEN - kein "wartet".
   * Eine laufende Heizung meldet deshalb HEIZEN, auch wenn die Soll-Temperatur
   * gerade erreicht ist.
   */
  private heatingState(): number {
    const states = this.platform.Characteristic.CurrentHeatingCoolingState;
    return this.state.functions[HEATER_FUNCTION] === 'OFF' ? states.OFF : states.HEAT;
  }

  private currentTemp(): number {
    const temperature = this.state.temperature;
    if (!this.state.online || !temperature || temperature.current === CLIMATE_TEMP_UNKNOWN) {
      throw this.unavailable();
    }
    return temperature.current;
  }

  private async writeFunction(fn: string, on: boolean): Promise<void> {
    try {
      await this.client.setApplianceFunction(this.state.id, fn, on);
      this.state = { ...this.state, functions: { ...this.state.functions, [fn]: on ? 'ON' : 'OFF' } };
    } catch (error) {
      this.platform.log.warn(
        `${this.state.name}: ${fn} schalten fehlgeschlagen: ${(error as Error).message}`,
      );
      throw this.unavailable();
    }
  }

  private async writeTarget(target: number): Promise<void> {
    try {
      await this.client.setApplianceTemperature(this.state.id, target);
      if (this.state.temperature) {
        this.state = { ...this.state, temperature: { ...this.state.temperature, target } };
      }
    } catch (error) {
      this.platform.log.warn(
        `${this.state.name}: Temperatur setzen fehlgeschlagen: ${(error as Error).message}`,
      );
      throw this.unavailable();
    }
  }

  private unavailable(): Error {
    return new this.platform.api.hap.HapStatusError(
      this.platform.api.hap.HAPStatus.SERVICE_COMMUNICATION_FAILURE,
    );
  }
}

/** Deutscher Name der Funktion; Unbekanntes bleibt, wie das Geraet es nennt. */
function functionLabel(fn: string): string {
  const labels: Record<string, string> = {
    PUMP: 'Pumpe',
    LIGHT: 'Licht',
    MASSAGE: 'Massage',
    FILTER: 'Filter',
    HEATER: 'Heizung',
  };
  return labels[fn] ?? fn;
}
