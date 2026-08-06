import type { PlatformAccessory } from 'homebridge';
import { ApiClient } from '../api-client';
import type { DeviceHandler, SmarthomePlatform } from '../platform';
import { CoverDto, DeviceBase, hasPosition } from '../types';

/**
 * Eine Store als WindowCovering.
 *
 * <p><b>Es wird nicht invertiert.</b> Die REST-API rechnet in der Geraeteskala
 * 0 = zu / 100 = offen - dieselbe Richtung wie HomeKit (belegt in
 * {@code docs/cover/SPEC.md} §2, im {@code Cover}-Record und im Port
 * {@code ControlCovers}). Das Dashboard zeigt "% zu" und spiegelt dafuer selbst; diese
 * Spiegelung gehoert der Oberflaeche und darf hier kein zweites Mal passieren. Der
 * Umsetzungsplan behauptete das Gegenteil - er hatte die Anzeige fuer die Schnittstelle
 * gehalten.
 */
export class CoverHandler implements DeviceHandler {
  private state: CoverDto;
  /** Zuletzt angeforderte Position; ohne eigenen Befehl gleich der Ist-Position. */
  private target: number;
  /** Zaehlt Poll-Zyklen ohne Positionsaenderung, um Stillstand zu erkennen. */
  private unchangedPolls = 0;

  constructor(
    private readonly platform: SmarthomePlatform,
    private readonly accessory: PlatformAccessory,
    private readonly client: ApiClient,
    initial: CoverDto,
  ) {
    this.state = initial;
    this.target = hasPosition(initial) ? initial.position : 0;

    this.accessory
      .getService(platform.Service.AccessoryInformation)!
      .setCharacteristic(platform.Characteristic.Manufacturer, 'smarthome')
      .setCharacteristic(platform.Characteristic.Model, 'Store')
      .setCharacteristic(platform.Characteristic.SerialNumber, initial.id);

    const service =
      this.accessory.getService(platform.Service.WindowCovering) ??
      this.accessory.addService(platform.Service.WindowCovering, initial.name);

    service
      .getCharacteristic(platform.Characteristic.CurrentPosition)
      .onGet(() => this.readPosition());
    service
      .getCharacteristic(platform.Characteristic.TargetPosition)
      .onGet(() => this.target)
      .onSet(async (value) => this.writeTarget(Number(value)));
    service
      .getCharacteristic(platform.Characteristic.PositionState)
      .onGet(() => this.positionState());
    service
      .getCharacteristic(platform.Characteristic.HoldPosition)
      .onSet(async (value) => {
        if (value === true) {
          await this.stop();
        }
      });
  }

  update(device: DeviceBase): void {
    const next = device as CoverDto;
    const moved = next.position !== this.state.position;
    this.unchangedPolls = moved ? 0 : this.unchangedPolls + 1;
    this.state = next;

    if (!next.online || !hasPosition(next)) {
      return;
    }
    // Ziel erreicht oder Bewegung zum Stillstand gekommen: Das Ziel nachziehen, sonst
    // meldet HomeKit dauerhaft "faehrt". Auf exakte Gleichheit ist kein Verlass - die
    // Store haelt auch mal bei 98 statt 100, und ueber das Dashboard oder eine
    // Zeitsteuerung gestellte Positionen kennt dieser Handler ohnehin nie.
    if (next.position === this.target || this.unchangedPolls >= STILL_POLLS) {
      this.target = next.position;
    }

    const service = this.accessory.getService(this.platform.Service.WindowCovering);
    service?.updateCharacteristic(this.platform.Characteristic.CurrentPosition, next.position);
    service?.updateCharacteristic(this.platform.Characteristic.TargetPosition, this.target);
    service?.updateCharacteristic(this.platform.Characteristic.PositionState, this.positionState());
  }

  private readPosition(): number {
    if (!this.state.online || !hasPosition(this.state)) {
      throw this.unavailable();
    }
    return this.state.position;
  }

  private async writeTarget(position: number): Promise<void> {
    try {
      await this.client.setCoverPosition(this.state.id, position);
      this.target = position;
      this.unchangedPolls = 0;
    } catch (error) {
      this.platform.log.warn(`${this.state.name} fahren fehlgeschlagen: ${(error as Error).message}`);
      throw this.unavailable();
    }
  }

  private async stop(): Promise<void> {
    try {
      await this.client.sendCoverCommand(this.state.id, 'STOP');
      // Nach einem Stopp ist die Ist-Position das Ziel - wo sie steht, bleibt sie.
      this.target = hasPosition(this.state) ? this.state.position : this.target;
    } catch (error) {
      this.platform.log.warn(`${this.state.name} anhalten fehlgeschlagen: ${(error as Error).message}`);
      throw this.unavailable();
    }
  }

  private positionState(): number {
    const states = this.platform.Characteristic.PositionState;
    if (!hasPosition(this.state) || this.target === this.state.position) {
      return states.STOPPED;
    }
    return this.target > this.state.position ? states.INCREASING : states.DECREASING;
  }

  private unavailable(): Error {
    return new this.platform.api.hap.HapStatusError(
      this.platform.api.hap.HAPStatus.SERVICE_COMMUNICATION_FAILURE,
    );
  }
}

/** So viele Poll-Zyklen ohne Aenderung gelten als "steht". */
const STILL_POLLS = 2;
