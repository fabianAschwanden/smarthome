import type { PlatformAccessory } from 'homebridge';
import type { DeviceHandler, SmarthomePlatform } from '../platform';
import { DeviceBase, SensorDto, hasHumidity, hasTemperature } from '../types';

/**
 * Ein Umweltsensor als TemperatureSensor, bei vorhandenem Messwert zusaetzlich als
 * HumiditySensor.
 *
 * <p>Ueber die Feuchte entscheidet der Sensor selbst: Wer keine meldet, bekommt den
 * Dienst nicht - eine leere Kachel in der Home-App waere schlimmer als gar keine. Der
 * Aussensensor liefert eine Feuchte (SPEC §8 liess offen, ob sie mangels Kalibrierung
 * unterdrueckt wird); sie wird gezeigt, damit Home-App und Dashboard denselben Wert
 * nennen. Zwei verschiedene Zahlen fuer dieselbe Messung waeren die groessere Zumutung.
 */
export class SensorHandler implements DeviceHandler {
  private state: SensorDto;
  private readonly withHumidity: boolean;

  constructor(
    private readonly platform: SmarthomePlatform,
    private readonly accessory: PlatformAccessory,
    initial: SensorDto,
  ) {
    this.state = initial;
    this.withHumidity = hasHumidity(initial);

    this.accessory
      .getService(platform.Service.AccessoryInformation)!
      .setCharacteristic(platform.Characteristic.Manufacturer, 'smarthome')
      .setCharacteristic(platform.Characteristic.Model, 'Umweltsensor')
      .setCharacteristic(platform.Characteristic.SerialNumber, initial.id);

    const temperature =
      this.accessory.getService(platform.Service.TemperatureSensor) ??
      this.accessory.addService(platform.Service.TemperatureSensor, initial.name);
    temperature
      .getCharacteristic(platform.Characteristic.CurrentTemperature)
      .onGet(() => this.readTemperature());

    const humidity = this.accessory.getService(platform.Service.HumiditySensor);
    if (this.withHumidity) {
      (humidity ?? this.accessory.addService(platform.Service.HumiditySensor, initial.name))
        .getCharacteristic(platform.Characteristic.CurrentRelativeHumidity)
        .onGet(() => this.readHumidity());
    } else if (humidity) {
      // Der Sensor meldete frueher eine Feuchte und tut es nicht mehr: Dienst entfernen,
      // statt eine Kachel stehen zu lassen, die nur noch Fehler liefert.
      this.accessory.removeService(humidity);
    }
  }

  update(device: DeviceBase): void {
    const next = device as SensorDto;
    const changed =
      next.temperature !== this.state.temperature ||
      next.humidity !== this.state.humidity ||
      next.online !== this.state.online;
    this.state = next;
    if (!changed || !next.online) {
      return;
    }
    if (hasTemperature(next)) {
      this.accessory
        .getService(this.platform.Service.TemperatureSensor)
        ?.updateCharacteristic(this.platform.Characteristic.CurrentTemperature, next.temperature);
    }
    if (this.withHumidity && hasHumidity(next)) {
      this.accessory
        .getService(this.platform.Service.HumiditySensor)
        ?.updateCharacteristic(this.platform.Characteristic.CurrentRelativeHumidity, next.humidity);
    }
  }

  /**
   * Ein offline gemeldeter oder platzhalterbehafteter Wert wird zum Fehler: Die Domaene
   * schickt -1000 bzw. -1, wenn nichts vorliegt. Diese Zahlen als Messwert zu melden
   * hiesse, HomeKit -1000 °C anzeigen zu lassen.
   */
  private readTemperature(): number {
    if (!this.state.online || !hasTemperature(this.state)) {
      throw this.unavailable();
    }
    return this.state.temperature;
  }

  private readHumidity(): number {
    if (!this.state.online || !hasHumidity(this.state)) {
      throw this.unavailable();
    }
    return this.state.humidity;
  }

  private unavailable(): Error {
    return new this.platform.api.hap.HapStatusError(
      this.platform.api.hap.HAPStatus.SERVICE_COMMUNICATION_FAILURE,
    );
  }
}
