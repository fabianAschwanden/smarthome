import type { PlatformAccessory } from 'homebridge';
import { ApiClient } from '../api-client';
import type { DeviceHandler, SmarthomePlatform } from '../platform';
import { DeviceBase, SwitchDto } from '../types';

/**
 * Ein Tuya-Schalter als HomeKit-Switch.
 *
 * <p>Der Handler haelt keinen eigenen Zustand als Wahrheit: Gelesen wird, was der letzte
 * Poll-Zyklus geliefert hat; geschrieben wird ueber die API, und der naechste Zyklus
 * bestaetigt das Ergebnis. So kann HomeKit nicht auseinanderlaufen mit dem Dashboard.
 */
export class SwitchHandler implements DeviceHandler {
  private state: SwitchDto;

  constructor(
    private readonly platform: SmarthomePlatform,
    private readonly accessory: PlatformAccessory,
    private readonly client: ApiClient,
    initial: SwitchDto,
    private readonly allowCriticalOff: boolean,
  ) {
    this.state = initial;

    this.accessory
      .getService(platform.Service.AccessoryInformation)!
      .setCharacteristic(platform.Characteristic.Manufacturer, 'smarthome')
      .setCharacteristic(platform.Characteristic.Model, 'Tuya Switch')
      .setCharacteristic(platform.Characteristic.SerialNumber, initial.id);

    const service =
      this.accessory.getService(platform.Service.Switch) ??
      this.accessory.addService(platform.Service.Switch, initial.name);

    service
      .getCharacteristic(platform.Characteristic.On)
      .onGet(() => this.readOn())
      .onSet(async (value) => this.writeOn(value === true));
  }

  update(device: DeviceBase): void {
    const next = device as SwitchDto;
    const changed = next.state !== this.state.state || next.online !== this.state.online;
    this.state = next;
    if (!changed) {
      return;
    }
    // Nur bei Aenderung melden - sonst wecken wir HomeKit im Poll-Takt ohne Anlass.
    this.accessory
      .getService(this.platform.Service.Switch)
      ?.updateCharacteristic(this.platform.Characteristic.On, next.state === 'ON');
  }

  /**
   * Ein offline gemeldetes Geraet fuehrt zu einem Fehler statt zu einem alten Wert.
   * Die Home-App zeigt dann "keine Antwort" - das ist ehrlicher als ein Zustand, den
   * niemand mehr bestaetigen kann.
   */
  private readOn(): boolean {
    if (!this.state.online) {
      throw new this.platform.api.hap.HapStatusError(
        this.platform.api.hap.HAPStatus.SERVICE_COMMUNICATION_FAILURE,
      );
    }
    return this.state.state === 'ON';
  }

  private async writeOn(on: boolean): Promise<void> {
    // Die Bestaetigung fuer kritische Schalter wird nur umgangen, wenn es der Betreiber
    // ausdruecklich erlaubt hat (siehe ApiClient.setSwitch).
    const confirm = this.state.critical && !on ? this.allowCriticalOff : false;
    try {
      await this.client.setSwitch(this.state.id, on, confirm);
      this.state = { ...this.state, state: on ? 'ON' : 'OFF' };
    } catch (error) {
      const message = (error as Error).message;
      if (this.state.critical && !on && message.includes('409')) {
        this.platform.log.warn(
          `${this.state.name} ist als kritisch markiert und laesst sich nur im Dashboard ` +
            'ausschalten. Zum Erlauben: allowCriticalOff in der Plugin-Konfiguration.',
        );
      } else {
        this.platform.log.warn(`${this.state.name} schalten fehlgeschlagen: ${message}`);
      }
      throw new this.platform.api.hap.HapStatusError(
        this.platform.api.hap.HAPStatus.SERVICE_COMMUNICATION_FAILURE,
      );
    }
  }
}
