import type { PlatformAccessory } from 'homebridge';
import { ApiClient } from '../api-client';
import type { DeviceHandler, SmarthomePlatform } from '../platform';
import {
  CLIMATE_MAX_TEMP,
  CLIMATE_MIN_TEMP,
  ClimateDto,
  DeviceBase,
  hasCurrentTemp,
} from '../types';

/**
 * Die Klimaanlage als HeaterCooler.
 *
 * <p>Die Modus-Matrix ist die heikle Stelle: Die Anlage kennt COOL, HEAT, AUTO und FAN,
 * HomeKit nur die ersten drei. FAN wird deshalb als AUTO <b>angezeigt</b> und aus
 * HomeKit nie <b>gesetzt</b> - Lueften ohne Temperaturziel hat in HomeKits Modell kein
 * Gegenstueck. Wer nur lueften will, tut das im Dashboard; das gilt genauso fuer Boost.
 *
 * <p>Ein Sonderfall steckt in den Schwellwerten: HomeKit fuehrt getrennte Soll-Werte
 * fuers Heizen und fuers Kuehlen, die Anlage hat genau einen. Beide Merkmale zeigen
 * deshalb dieselbe Soll-Temperatur, und beide schreiben auf dasselbe Feld.
 */
export class ClimateHandler implements DeviceHandler {
  private state: ClimateDto;

  constructor(
    private readonly platform: SmarthomePlatform,
    private readonly accessory: PlatformAccessory,
    private readonly client: ApiClient,
    initial: ClimateDto,
  ) {
    this.state = initial;

    this.accessory
      .getService(platform.Service.AccessoryInformation)!
      .setCharacteristic(platform.Characteristic.Manufacturer, 'smarthome')
      .setCharacteristic(platform.Characteristic.Model, 'Klimaanlage')
      .setCharacteristic(platform.Characteristic.SerialNumber, initial.id);

    const service =
      this.accessory.getService(platform.Service.HeaterCooler) ??
      this.accessory.addService(platform.Service.HeaterCooler, initial.name);

    service
      .getCharacteristic(platform.Characteristic.Active)
      .onGet(() => this.activeValue())
      .onSet(async (value) => this.writePower(value === 1 || value === true));

    service
      .getCharacteristic(platform.Characteristic.CurrentHeaterCoolerState)
      .onGet(() => this.currentStateValue());

    service
      .getCharacteristic(platform.Characteristic.TargetHeaterCoolerState)
      .onGet(() => this.targetStateValue())
      .onSet(async (value) => this.writeMode(Number(value)));

    service
      .getCharacteristic(platform.Characteristic.CurrentTemperature)
      .onGet(() => this.readCurrentTemp());

    // Ganze Grad, 16..30 - die Invarianten der Domaene. Liesse man HomeKit halbe Grad
    // anbieten, wuerde die App jeden zweiten Wunsch mit einem Fehler beantworten.
    for (const threshold of [
      platform.Characteristic.CoolingThresholdTemperature,
      platform.Characteristic.HeatingThresholdTemperature,
    ]) {
      service
        .getCharacteristic(threshold)
        .setProps({ minValue: CLIMATE_MIN_TEMP, maxValue: CLIMATE_MAX_TEMP, minStep: 1 })
        .onGet(() => this.state.targetTemp)
        .onSet(async (value) => this.writeTarget(Math.round(Number(value))));
    }
  }

  update(device: DeviceBase): void {
    const next = device as ClimateDto;
    const changed =
      next.power !== this.state.power ||
      next.mode !== this.state.mode ||
      next.targetTemp !== this.state.targetTemp ||
      next.currentTemp !== this.state.currentTemp ||
      next.online !== this.state.online;
    this.state = next;
    if (!changed || !next.online) {
      return;
    }
    const service = this.accessory.getService(this.platform.Service.HeaterCooler);
    const characteristic = this.platform.Characteristic;
    service?.updateCharacteristic(characteristic.Active, this.activeValue());
    service?.updateCharacteristic(
      characteristic.CurrentHeaterCoolerState,
      this.currentStateValue(),
    );
    service?.updateCharacteristic(characteristic.TargetHeaterCoolerState, this.targetStateValue());
    service?.updateCharacteristic(characteristic.CoolingThresholdTemperature, next.targetTemp);
    service?.updateCharacteristic(characteristic.HeatingThresholdTemperature, next.targetTemp);
    if (hasCurrentTemp(next)) {
      service?.updateCharacteristic(characteristic.CurrentTemperature, next.currentTemp);
    }
  }

  private activeValue(): number {
    const active = this.platform.Characteristic.Active;
    return this.state.power ? active.ACTIVE : active.INACTIVE;
  }

  /** Was die Anlage gerade tut - nicht, was sie tun soll. */
  private currentStateValue(): number {
    const states = this.platform.Characteristic.CurrentHeaterCoolerState;
    if (!this.state.power) {
      return states.INACTIVE;
    }
    switch (this.state.mode) {
      case 'COOL':
        return this.reachedTarget(-1) ? states.IDLE : states.COOLING;
      case 'HEAT':
        return this.reachedTarget(1) ? states.IDLE : states.HEATING;
      case 'AUTO':
        if (!hasCurrentTemp(this.state) || this.state.currentTemp === this.state.targetTemp) {
          return states.IDLE;
        }
        return this.state.currentTemp > this.state.targetTemp ? states.COOLING : states.HEATING;
      default:
        // FAN und alles Unbekannte: Die Anlage laeuft, aber sie regelt keine Temperatur.
        // IDLE ist die ehrliche Antwort - "kuehlt" waere gelogen.
        return states.IDLE;
    }
  }

  /**
   * Hat die Anlage ihr Ziel erreicht? {@code direction} gibt an, in welche Richtung sie
   * arbeitet: -1 kuehlen (fertig, sobald es kuehl genug ist), +1 heizen.
   */
  private reachedTarget(direction: number): boolean {
    if (!hasCurrentTemp(this.state)) {
      return false;
    }
    return direction < 0
      ? this.state.currentTemp <= this.state.targetTemp
      : this.state.currentTemp >= this.state.targetTemp;
  }

  private targetStateValue(): number {
    const states = this.platform.Characteristic.TargetHeaterCoolerState;
    switch (this.state.mode) {
      case 'COOL':
        return states.COOL;
      case 'HEAT':
        return states.HEAT;
      default:
        // AUTO, FAN und Unbekanntes. FAN als AUTO zu zeigen ist eine bewusste Notluege:
        // HomeKit kennt keinen Modus ohne Temperaturziel, und "aus" waere falscher.
        return states.AUTO;
    }
  }

  private readCurrentTemp(): number {
    if (!this.state.online || !hasCurrentTemp(this.state)) {
      throw this.unavailable();
    }
    return this.state.currentTemp;
  }

  private async writePower(on: boolean): Promise<void> {
    await this.send(() => this.client.setClimatePower(this.state.id, on), 'schalten', () => {
      this.state = { ...this.state, power: on };
    });
  }

  private async writeMode(value: number): Promise<void> {
    const states = this.platform.Characteristic.TargetHeaterCoolerState;
    // FAN steht hier bewusst nicht: Aus HomeKit gesetzt wird nur, was HomeKit auch kennt.
    const mode = value === states.COOL ? 'COOL' : value === states.HEAT ? 'HEAT' : 'AUTO';
    await this.send(() => this.client.setClimateMode(this.state.id, mode), 'umschalten', () => {
      this.state = { ...this.state, mode };
    });
  }

  private async writeTarget(temperature: number): Promise<void> {
    await this.send(
      () => this.client.setClimateTarget(this.state.id, temperature),
      'einstellen',
      () => {
        this.state = { ...this.state, targetTemp: temperature };
      },
    );
  }

  private async send(
    call: () => Promise<void>,
    action: string,
    onSuccess: () => void,
  ): Promise<void> {
    try {
      await call();
      onSuccess();
    } catch (error) {
      this.platform.log.warn(
        `${this.state.name} ${action} fehlgeschlagen: ${(error as Error).message}`,
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
