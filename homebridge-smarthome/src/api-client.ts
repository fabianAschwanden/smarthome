import {
  ApplianceDto,
  ClimateDto,
  CoverDto,
  DeviceKind,
  SensorDto,
  SmokeDto,
  Snapshot,
  SwitchDto,
} from './types';

/** Minimaler Logger – entkoppelt den Client von Homebridge, damit er testbar bleibt. */
export interface ClientLog {
  debug(message: string): void;
  warn(message: string): void;
}

/**
 * Liest und schaltet über die REST-API der App. Bewusst der einzige Ort im Plugin, der
 * HTTP kennt – alles andere arbeitet auf den DTOs.
 *
 * <p>Ein Ausfall einzelner Endpunkte darf nicht den ganzen Poll-Zyklus kippen: Jeder
 * Endpunkt wird für sich behandelt, eine leere Liste ersetzt einen Fehler. Sonst
 * verschwänden bei einem Aussetzer eines Endpunkts alle Geräte aus HomeKit.
 */
export class ApiClient {
  constructor(
    private readonly baseUrl: string,
    private readonly log: ClientLog,
    private readonly timeoutMs = 8000,
  ) {}

  /**
   * Alle Geräte in einem Zug. Ein Endpunkt, der nicht antwortet, liefert eine leere
   * Liste UND steht in {@code unavailable} - denn "keine Antwort" ist nicht "keine
   * Geräte". Am 20.09.2026 hat die Brücke die Klimaanlage zweimal aus HomeKit geworfen
   * und wieder angelegt, nur weil /api/climate beim App-Start die 8 Sekunden riss.
   */
  async snapshot(): Promise<Snapshot> {
    const [switches, appliances, covers, climate, sensors, smoke] = await Promise.all([
      this.getList<SwitchDto>('/api/switches'),
      this.getList<ApplianceDto>('/api/appliances'),
      this.getList<CoverDto>('/api/covers'),
      this.getList<ClimateDto>('/api/climate'),
      this.getList<SensorDto>('/api/sensors'),
      this.getList<SmokeDto>('/api/safety/smoke'),
    ]);
    const unavailable: DeviceKind[] = [];
    const listOrEmpty = <T>(list: T[] | null, kind: DeviceKind): T[] => {
      if (list === null) {
        unavailable.push(kind);
        return [];
      }
      return list;
    };
    return {
      switches: listOrEmpty(switches, 'switch'),
      appliances: listOrEmpty(appliances, 'appliance'),
      covers: listOrEmpty(covers, 'cover'),
      climate: listOrEmpty(climate, 'climate'),
      sensors: listOrEmpty(sensors, 'sensor'),
      smoke: listOrEmpty(smoke, 'smoke'),
      unavailable,
    };
  }

  /**
   * Schaltet einen Schalter.
   *
   * <p>{@code confirm} entscheidet über die Sicherung der App: Als «kritisch» markierte
   * Schalter (hier der Homecinema) verlangen beim AUSschalten eine Bestätigung, sonst
   * antwortet die API mit 409. HomeKit kann keinen Rückfrage-Dialog zeigen – deshalb
   * wird die Sicherung standardmässig NICHT umgangen: Einschalten geht, Ausschalten
   * scheitert sichtbar und bleibt dem Dashboard vorbehalten.
   *
   * <p>Eine Fehlerkennung von Siri oder ein versehentlicher Tap in der Home-App sind
   * genau der Unfall, gegen den das Flag gesetzt wurde – und eine Automation schaltet
   * ganz ohne Menschen. Wer das anders will, setzt {@code allowCriticalOff} in der
   * Plugin-Konfiguration.
   */
  async setSwitch(id: string, on: boolean, confirm = false): Promise<void> {
    await this.post(`/api/switches/${encodeURIComponent(id)}`, {
      state: on ? 'ON' : 'OFF',
      confirm,
    });
  }

  /** Schaltet eine Funktion einer Wellness-Anlage (PUMP, HEATER, LIGHT, ...). */
  async setApplianceFunction(id: string, fn: string, on: boolean): Promise<void> {
    await this.post(
      `/api/appliances/${encodeURIComponent(id)}/functions/${encodeURIComponent(fn)}`,
      { state: on ? 'ON' : 'OFF' },
    );
  }

  async setApplianceTemperature(id: string, target: number): Promise<void> {
    await this.post(`/api/appliances/${encodeURIComponent(id)}/temperature`, { target });
  }

  /** Zielposition in der Geräteskala: 0 = zu, 100 = offen – wie HomeKit (siehe cover.ts). */
  async setCoverPosition(id: string, domainPosition: number): Promise<void> {
    await this.post(`/api/covers/${encodeURIComponent(id)}/position`, { position: domainPosition });
  }

  async sendCoverCommand(id: string, command: string): Promise<void> {
    await this.post(`/api/covers/${encodeURIComponent(id)}/command`, { command });
  }

  async setClimatePower(id: string, on: boolean): Promise<void> {
    await this.post(`/api/climate/${encodeURIComponent(id)}/power`, { power: on });
  }

  async setClimateMode(id: string, mode: string): Promise<void> {
    await this.post(`/api/climate/${encodeURIComponent(id)}/mode`, { mode });
  }

  async setClimateTarget(id: string, targetTemp: number): Promise<void> {
    await this.post(`/api/climate/${encodeURIComponent(id)}/target`, { targetTemp });
  }

  /** null = keine brauchbare Antwort; die leere Liste bleibt "wirklich keine Geraete". */
  private async getList<T>(path: string): Promise<T[] | null> {
    try {
      const response = await this.fetchWithTimeout(path, { method: 'GET' });
      if (!response.ok) {
        this.log.warn(`${path}: HTTP ${response.status}`);
        return null;
      }
      const body = (await response.json()) as unknown;
      if (!Array.isArray(body)) {
        // Die SPA-Rueckfallebene der App lieferte frueher HTML mit Status 200 – ein
        // Typcheck ist deshalb billiger als das Vertrauen auf den Statuscode.
        this.log.warn(`${path}: unerwartete Antwort (keine Liste)`);
        return null;
      }
      return body as T[];
    } catch (error) {
      this.log.warn(`${path}: ${(error as Error).message}`);
      return null;
    }
  }

  private async post(path: string, body: unknown): Promise<void> {
    const response = await this.fetchWithTimeout(path, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    });
    if (!response.ok) {
      // Bewusst werfen: HomeKit soll den Fehlschlag sehen und den alten Zustand
      // wiederherstellen, statt eine Schaltung vorzutäuschen.
      throw new Error(`${path}: HTTP ${response.status}`);
    }
  }

  private async fetchWithTimeout(path: string, init: RequestInit): Promise<Response> {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), this.timeoutMs);
    try {
      this.log.debug(`${init.method} ${path}`);
      return await fetch(`${this.baseUrl}${path}`, { ...init, signal: controller.signal });
    } finally {
      clearTimeout(timer);
    }
  }
}
