import type { PlatformAccessory } from 'homebridge';
import type { DeviceHandler, SmarthomePlatform } from '../platform';
import { DeviceBase, LOW_BATTERY_PERCENT, SmokeDto, hasBattery } from '../types';

/**
 * Ein Rauchmelder als SmokeSensor, bei bekanntem Batteriestand zusaetzlich mit
 * StatusLowBattery.
 *
 * <p>Anders als Schalter und Sensoren meldet dieser Handler bei {@code online: false}
 * KEINEN Fehler, sondern den zuletzt bekannten Alarmzustand. Batteriebetriebene Melder
 * funken nur sporadisch - "nicht erreichbar" ist hier der Normalfall und kein Defekt
 * (so steht es auch in der Domaene). Wuerde die Home-App dauerhaft "keine Antwort"
 * zeigen, gewoehnte man sich an, die Kachel zu ignorieren - und uebersaehe den einen
 * Moment, auf den es ankommt.
 *
 * <p>Ein einmal gemeldeter ALARM bleibt damit sichtbar, auch wenn der Melder danach
 * schweigt. Das ist die richtige Richtung fuer den Irrtum.
 */
export class SmokeHandler implements DeviceHandler {
  private state: SmokeDto;
  private readonly withBattery: boolean;

  constructor(
    private readonly platform: SmarthomePlatform,
    private readonly accessory: PlatformAccessory,
    initial: SmokeDto,
  ) {
    this.state = initial;
    this.withBattery = hasBattery(initial);

    this.accessory
      .getService(platform.Service.AccessoryInformation)!
      .setCharacteristic(platform.Characteristic.Manufacturer, 'smarthome')
      .setCharacteristic(platform.Characteristic.Model, 'Rauchmelder')
      .setCharacteristic(platform.Characteristic.SerialNumber, initial.id);

    const service =
      this.accessory.getService(platform.Service.SmokeSensor) ??
      this.accessory.addService(platform.Service.SmokeSensor, initial.name);

    service
      .getCharacteristic(platform.Characteristic.SmokeDetected)
      .onGet(() => this.readSmokeDetected());

    if (this.withBattery) {
      service
        .getCharacteristic(platform.Characteristic.StatusLowBattery)
        .onGet(() => this.readLowBattery());
    }
    // battery: -1 heisst "unbekannt". Dann wird der Batteriestatus gar nicht gemeldet -
    // "leer" zu behaupten waere eine Falschmeldung, "voll" eine gefaehrliche.
  }

  update(device: DeviceBase): void {
    const next = device as SmokeDto;
    const changed = next.alarm !== this.state.alarm || next.battery !== this.state.battery;
    this.state = next;
    if (!changed) {
      return;
    }
    const service = this.accessory.getService(this.platform.Service.SmokeSensor);
    service?.updateCharacteristic(
      this.platform.Characteristic.SmokeDetected,
      this.smokeDetectedValue(),
    );
    if (this.withBattery && hasBattery(next)) {
      service?.updateCharacteristic(
        this.platform.Characteristic.StatusLowBattery,
        this.lowBatteryValue(),
      );
    }
  }

  private readSmokeDetected(): number {
    return this.smokeDetectedValue();
  }

  private readLowBattery(): number {
    return this.lowBatteryValue();
  }

  private smokeDetectedValue(): number {
    const smoke = this.platform.Characteristic.SmokeDetected;
    // Alles ausser einem ausdruecklichen ALARM gilt als kein Alarm - ein unbekannter
    // Zustand darf keinen Alarm ausloesen, sonst glaubt ihm niemand mehr.
    return this.state.alarm === 'ALARM' ? smoke.SMOKE_DETECTED : smoke.SMOKE_NOT_DETECTED;
  }

  private lowBatteryValue(): number {
    // Unbekannt wird als normal gemeldet: Der Dienst existiert nur, wenn beim Anlegen
    // ein Wert vorlag - ein spaeteres "unbekannt" darf keinen Fehlalarm ausloesen.
    if (!hasBattery(this.state)) {
      return this.platform.Characteristic.StatusLowBattery.BATTERY_LEVEL_NORMAL;
    }
    return this.state.battery <= LOW_BATTERY_PERCENT
      ? this.platform.Characteristic.StatusLowBattery.BATTERY_LEVEL_LOW
      : this.platform.Characteristic.StatusLowBattery.BATTERY_LEVEL_NORMAL;
  }
}
