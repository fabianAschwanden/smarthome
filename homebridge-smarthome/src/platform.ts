import type {
  API,
  Characteristic,
  DynamicPlatformPlugin,
  Logging,
  PlatformAccessory,
  PlatformConfig,
  Service,
} from 'homebridge';
import { ApiClient } from './api-client';
import { CoverHandler } from './accessories/cover';
import { SensorHandler } from './accessories/sensor';
import { SmokeHandler } from './accessories/smoke';
import { SwitchHandler } from './accessories/switch';
import { DEFAULT_POLL_SECONDS, MIN_POLL_SECONDS, PLATFORM_NAME, PLUGIN_NAME } from './settings';
import { DeviceBase, Snapshot } from './types';

/** Was die Plattform aus der Homebridge-Konfiguration liest. */
export interface SmarthomeConfig extends PlatformConfig {
  baseUrl?: string;
  pollIntervalSeconds?: number;
  /** Erlaubt HomeKit, als «kritisch» markierte Schalter auszuschalten (siehe ApiClient). */
  allowCriticalOff?: boolean;
}

/** Was ein Accessory-Typ können muss, damit die Plattform ihn verwalten kann. */
export interface DeviceHandler {
  /** Übernimmt einen frischen Zustand aus dem Poll-Zyklus. */
  update(device: DeviceBase): void;
}

/** Ein Gerät, wie es aus dem Snapshot in die Registrierung geht. */
interface Discovered {
  device: DeviceBase;
  /** Unterscheidet gleiche ids in verschiedenen Geräteklassen. */
  kind: string;
  create(accessory: PlatformAccessory): DeviceHandler;
}

/**
 * Die Bridge-Plattform: fragt die App periodisch ab, legt Accessories an bzw. entfernt
 * sie und reicht jeden neuen Zustand an den passenden Handler weiter.
 *
 * <p>Wiedererkannt werden Geräte über ihre stabile {@code id}, nicht über den Namen –
 * der ist in der App änderbar, und eine Umbenennung darf in HomeKit kein neues Gerät
 * erzeugen (das Zimmer und alle Automationen wären sonst weg).
 */
export class SmarthomePlatform implements DynamicPlatformPlugin {
  public readonly Service: typeof Service;
  public readonly Characteristic: typeof Characteristic;

  private readonly client: ApiClient;
  private readonly pollMs: number;
  private readonly allowCriticalOff: boolean;
  /** UUID → Handler, für alle aktuell registrierten Accessories. */
  private readonly handlers = new Map<string, DeviceHandler>();
  /** Aus dem Cache wiederhergestellte Accessories, bevor der erste Poll läuft. */
  private readonly cached: PlatformAccessory[] = [];
  // ReturnType statt NodeJS.Timeout: so braucht das Plugin keine @types/node.
  private timer?: ReturnType<typeof setInterval>;

  constructor(
    public readonly log: Logging,
    public readonly config: SmarthomeConfig,
    public readonly api: API,
  ) {
    this.Service = api.hap.Service;
    this.Characteristic = api.hap.Characteristic;

    const baseUrl = (config.baseUrl ?? '').replace(/\/+$/, '');
    this.client = new ApiClient(baseUrl, {
      debug: (m) => this.log.debug(m),
      warn: (m) => this.log.warn(m),
    });
    this.pollMs = Math.max(config.pollIntervalSeconds ?? DEFAULT_POLL_SECONDS, MIN_POLL_SECONDS) * 1000;
    this.allowCriticalOff = config.allowCriticalOff === true;

    if (!baseUrl) {
      // Ohne Basis-URL kann das Plugin nichts – lieber laut sein als stumm nichts tun.
      this.log.error('baseUrl fehlt in der Plugin-Konfiguration – es werden keine Geräte angelegt.');
      return;
    }

    api.on('didFinishLaunching', () => {
      void this.poll();
      this.timer = setInterval(() => void this.poll(), this.pollMs);
    });
    api.on('shutdown', () => {
      if (this.timer) {
        clearInterval(this.timer);
      }
    });
  }

  /** Homebridge reicht hier die aus seinem Cache wiederhergestellten Accessories herein. */
  configureAccessory(accessory: PlatformAccessory): void {
    this.cached.push(accessory);
  }

  /** Ein Zyklus: lesen, Bestand angleichen, Zustände verteilen. */
  private async poll(): Promise<void> {
    try {
      const snapshot = await this.client.snapshot();
      const discovered = this.discover(snapshot);
      if (discovered.length === 0) {
        // Kein Gerät heisst fast immer: App nicht erreichbar. Bestehende Accessories
        // bleiben stehen – sie ganz zu entfernen würde in HomeKit Räume und
        // Automationen zerstören, nur weil die App gerade neu startet.
        this.log.debug('Keine Geräte im Snapshot – Bestand bleibt unverändert.');
        return;
      }
      this.sync(discovered);
      for (const item of discovered) {
        this.handlers.get(this.uuidFor(item))?.update(item.device);
      }
    } catch (error) {
      this.log.warn(`Poll-Zyklus fehlgeschlagen: ${(error as Error).message}`);
    }
  }

  /** Übersetzt den Snapshot in die Liste der Geräte, die HomeKit kennen soll. */
  private discover(snapshot: Snapshot): Discovered[] {
    const result: Discovered[] = [];
    for (const device of snapshot.switches) {
      result.push({
        device,
        kind: 'switch',
        create: (accessory) =>
          new SwitchHandler(this, accessory, this.client, device, this.allowCriticalOff),
      });
    }
    for (const device of snapshot.sensors) {
      result.push({
        device,
        kind: 'sensor',
        create: (accessory) => new SensorHandler(this, accessory, device),
      });
    }
    for (const device of snapshot.smoke) {
      result.push({
        device,
        kind: 'smoke',
        create: (accessory) => new SmokeHandler(this, accessory, device),
      });
    }
    for (const device of snapshot.covers) {
      result.push({
        device,
        kind: 'cover',
        create: (accessory) => new CoverHandler(this, accessory, this.client, device),
      });
    }
    // Klima folgt in Etappe 5.
    return result;
  }

  /** Legt fehlende Accessories an und entfernt verschwundene. */
  private sync(discovered: Discovered[]): void {
    const wanted = new Set(discovered.map((item) => this.uuidFor(item)));

    for (const item of discovered) {
      const uuid = this.uuidFor(item);
      if (this.handlers.has(uuid)) {
        continue;
      }
      const existing = this.cached.find((a) => a.UUID === uuid);
      if (existing) {
        existing.displayName = item.device.name;
        this.handlers.set(uuid, item.create(existing));
        this.log.info(`Gerät übernommen: ${item.device.name}`);
      } else {
        const accessory = new this.api.platformAccessory(item.device.name, uuid);
        accessory.context.id = item.device.id;
        accessory.context.kind = item.kind;
        this.handlers.set(uuid, item.create(accessory));
        this.api.registerPlatformAccessories(PLUGIN_NAME, PLATFORM_NAME, [accessory]);
        this.log.info(`Gerät hinzugefügt: ${item.device.name} (${item.device.room})`);
      }
    }

    const stale = this.cached.filter((a) => !wanted.has(a.UUID));
    if (stale.length > 0) {
      this.api.unregisterPlatformAccessories(PLUGIN_NAME, PLATFORM_NAME, stale);
      for (const accessory of stale) {
        this.handlers.delete(accessory.UUID);
        this.log.info(`Gerät entfernt: ${accessory.displayName}`);
      }
      this.cached.length = 0;
    }
  }

  /** Stabile UUID aus Geräteklasse und id – der Name darf sich ändern. */
  private uuidFor(item: Discovered): string {
    return this.api.hap.uuid.generate(`${PLUGIN_NAME}:${item.kind}:${item.device.id}`);
  }
}
