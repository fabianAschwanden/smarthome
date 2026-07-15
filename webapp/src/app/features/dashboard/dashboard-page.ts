import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { EnergyService } from '../../core/services/energy.service';
import { TuyaService } from '../../core/services/tuya.service';
import { ClimateService } from '../../core/services/climate.service';
import { CoverService } from '../../core/services/cover.service';
import { SensorService } from '../../core/services/sensor.service';
import { SafetyService } from '../../core/services/safety.service';
import { BatteryService } from '../../core/services/battery.service';
import { WeatherService } from '../../core/services/weather.service';
import { RoomService } from '../../core/services/room.service';
import { PowerReading } from '../../core/models/energy';
import { ClimateMode } from '../../core/models/climate';
import { PowerToggle } from '../../shared/power-toggle';
import { ItemImage } from '../../shared/item-image';
import { TempDial } from '../../shared/temp-dial';
import { BlindMini } from '../../shared/blind-mini';
import { EnergyFlow } from './energy-flow';
import { WeatherCard } from './weather-card';
import { OutdoorTempCard } from './outdoor-temp-card';

const CLIMATE_MODE_LABELS: Record<ClimateMode, string> = {
  COOL: 'Kühlen',
  HEAT: 'Heizen',
  AUTO: 'Auto',
  FAN: 'Lüften',
};

/**
 * Favoriten-Dashboard (Startseite): die wichtigsten Geräte auf einen Blick –
 * Energie (Verbrauch/Produktion), Stehlampe, Klima und Storen. Bündelt die
 * bestehenden Services; jede Kachel verlinkt auf ihre Detailseite.
 */
@Component({
  selector: 'app-dashboard-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RouterLink,
    DecimalPipe,
    PowerToggle,
    ItemImage,
    TempDial,
    BlindMini,
    EnergyFlow,
    WeatherCard,
    OutdoorTempCard,
  ],
  template: `
    <section class="space-y-5">
      <!-- Sicherheits-Alarm: prominent, nur bei aktivem Rauchalarm -->
      @if (alarmActive(); as alarms) {
        <div
          class="animate-pulse rounded-2xl border border-red-400/50 bg-red-500/20 p-4 text-red-100"
          role="alert"
        >
          <p class="text-lg font-semibold">🔥 Rauchalarm!</p>
          <p class="text-sm">{{ alarms }}</p>
        </div>
      }

      <!-- Oben: 3 saubere Spalten — Energie | Wetter+Stehlampe+Rauchmelder | Klima -->
      <div class="grid items-start gap-4 lg:grid-cols-3">
        <!-- Spalte 1: Energie (Fronius) -->
        <a routerLink="/energy" class="block">
          <app-energy-flow [reading]="energy()" [batteryStatus]="batteryStatus()" />
        </a>

        <!-- Spalte 2: Wetter, darunter Stehlampe und Rauchmelder -->
        <div class="flex flex-col gap-4">
          <app-weather-card [weather]="weather()" />

          <!-- Stehlampe -->
          @if (stehlampe(); as s) {
            @if (room.shows(s.room)) {
              <article
                class="glass-card flex cursor-pointer items-center justify-between gap-4 p-5"
                routerLink="/switch"
              >
                <div class="flex min-w-0 items-center gap-3">
                  <app-item-image [itemId]="s.id" [label]="s.name" variant="avatar" />
                  <div class="min-w-0">
                    <h3 class="truncate font-medium">{{ s.name }}</h3>
                    <p class="mt-0.5 text-xs text-[color:var(--ink-soft)]">
                      {{ !s.online ? 'Offline' : s.state === 'ON' ? 'Ein' : 'Aus' }}
                    </p>
                  </div>
                </div>
                <app-power-toggle
                  [on]="s.state === 'ON'"
                  [disabled]="!s.online"
                  size="lg"
                  [label]="s.name"
                  (onChange)="switchToggle(s.id, $event)"
                  (click)="$event.stopPropagation()"
                />
              </article>
            }
          }

          <!-- Palmenbeleuchtung -->
          @if (palme(); as s) {
            @if (room.shows(s.room)) {
              <article
                class="glass-card flex cursor-pointer items-center justify-between gap-4 p-5"
                routerLink="/switch"
              >
                <div class="flex min-w-0 items-center gap-3">
                  <app-item-image [itemId]="s.id" [label]="s.name" variant="avatar" />
                  <div class="min-w-0">
                    <h3 class="truncate font-medium">{{ s.name }}</h3>
                    <p class="mt-0.5 text-xs text-[color:var(--ink-soft)]">
                      {{ !s.online ? 'Offline' : s.state === 'ON' ? 'Ein' : 'Aus' }}
                    </p>
                  </div>
                </div>
                <app-power-toggle
                  [on]="s.state === 'ON'"
                  [disabled]="!s.online"
                  size="lg"
                  [label]="s.name"
                  (onChange)="switchToggle(s.id, $event)"
                  (click)="$event.stopPropagation()"
                />
              </article>
            }
          }

          <!-- Sicherheit (Rauchmelder) -->
          @for (sm of smoke(); track sm.id) {
            <article
              class="glass-card flex items-center justify-between gap-3 p-4"
              [class]="sm.alarm === 'ALARM' ? 'border border-red-400/50 bg-red-500/15' : ''"
            >
              <div class="min-w-0">
                <h3 class="truncate text-sm font-medium">🛡 {{ sm.name }}</h3>
                <p class="mt-0.5 text-xs text-[color:var(--ink-soft)]">
                  🔋 {{ sm.battery >= 0 ? sm.battery + '%' : '–' }}
                </p>
              </div>
              <span
                class="shrink-0 rounded-full px-2.5 py-0.5 text-xs font-semibold"
                [class]="
                  !sm.online
                    ? 'bg-white/10 text-[color:var(--ink-soft)]'
                    : sm.alarm === 'ALARM'
                      ? 'bg-red-500/90 text-white'
                      : 'bg-emerald-400/20 text-emerald-200'
                "
              >
                {{ !sm.online ? '○' : sm.alarm === 'ALARM' ? '🔥' : '● OK' }}
              </span>
            </article>
          }
        </div>

        <!-- Spalte 3: Außen-/Innentemperatur und Klimaanlage -->
        <div class="flex flex-col gap-4">
          <!-- Außentemperatur (von der Klimaanlage gemeldet) -->
          @if (climate(); as c) {
            <a routerLink="/climate" class="block">
              <app-outdoor-temp-card [temp]="c.outdoorTemp" [online]="c.online" />
            </a>
          }

          <!-- Innentemperatur (Sensor, z. B. Küche) -->
          @if (sensor(); as s) {
            @if (room.shows(s.room)) {
              <article class="glass-card space-y-3 p-5">
                <header class="flex items-center justify-between">
                  <h3 class="font-medium">Innentemperatur</h3>
                  <span class="text-xs text-[color:var(--ink-faint)]">{{ s.room || s.name }}</span>
                </header>
                <div class="flex items-end justify-between">
                  <div>
                    <p class="text-xs text-[color:var(--ink-soft)]">Temperatur</p>
                    <p class="mt-0.5 text-3xl font-semibold tabular-nums">
                      {{
                        s.online && s.temperature > -100
                          ? (s.temperature | number: '1.0-1') + '°'
                          : '–'
                      }}
                    </p>
                  </div>
                  <div class="text-right">
                    <p class="text-xs text-[color:var(--ink-soft)]">Feuchte</p>
                    <p class="mt-0.5 text-2xl font-medium">
                      {{ s.online && s.humidity >= 0 ? s.humidity + '%' : '–' }}
                    </p>
                  </div>
                </div>
                @if (!s.online) {
                  <p class="text-xs text-amber-300/90">⚠ Sensor nicht erreichbar.</p>
                }
              </article>
            }
          }

          <!-- Klimaanlage mit Temperatur-Kreis -->
          @if (climate(); as c) {
            @if (room.shows(c.room)) {
              <article
                class="glass-card cursor-pointer space-y-3 p-5"
                [class.opacity-60]="!c.online"
                routerLink="/climate"
              >
                <header class="flex items-center justify-between gap-3">
                  <h3 class="truncate font-medium">{{ c.name }}</h3>
                  <app-power-toggle
                    [on]="c.power"
                    [disabled]="!c.online"
                    size="lg"
                    label="Klima ein/aus"
                    (onChange)="climatePower($event)"
                    (click)="$event.stopPropagation()"
                  />
                </header>
                <app-temp-dial
                  [target]="c.targetTemp"
                  [current]="c.currentTemp"
                  [label]="modeLabel(c.mode)"
                  emphasis="current"
                />
                <!-- Boost / Turbo: maximale Leistung -->
                <button
                  type="button"
                  [disabled]="!c.online"
                  class="tile-toggle w-full flex-row justify-center gap-2"
                  [class.tile-toggle-active]="c.boost"
                  (click)="$event.stopPropagation(); climateBoost(!c.boost)"
                >
                  <span class="text-base">🚀</span>
                  <span class="text-xs">Boost{{ c.boost ? ' · aktiv' : '' }}</span>
                </button>
                <p class="text-center text-xs text-[color:var(--ink-soft)]">Einstellen →</p>
              </article>
            }
          }
        </div>
      </div>

      <!-- Storen über volle Breite -->
      <div class="grid gap-4">
        <!-- Storen (im Raumfilter nur die des aktiven Raums) -->
        @if (visibleCovers().length > 0) {
          <article class="glass-card cursor-pointer space-y-3 p-5" routerLink="/covers">
            <header class="flex items-center justify-between">
              <h3 class="font-medium">Storen</h3>
              <span class="text-xs text-[color:var(--ink-soft)]">Alle →</span>
            </header>
            <div class="space-y-2">
              @for (cv of visibleCovers(); track cv.id) {
                <div class="flex items-center justify-between gap-3">
                  <span class="flex min-w-0 items-center gap-2.5 text-sm">
                    <app-blind-mini [closed]="cv.position < 0 ? 0 : 100 - cv.position" />
                    <span class="min-w-0 truncate">
                      {{ cv.name }}
                      <span class="text-[color:var(--ink-faint)]">
                        {{
                          cv.online
                            ? cv.position < 0
                              ? ''
                              : 100 - cv.position + '% zu'
                            : 'offline'
                        }}
                      </span>
                    </span>
                  </span>
                  <div class="flex shrink-0 gap-2">
                    <button
                      type="button"
                      [disabled]="!cv.online"
                      class="glass size-12 rounded-xl text-lg disabled:opacity-40"
                      (click)="$event.stopPropagation(); coverCmd(cv.id, 'OPEN')"
                      aria-label="Auf"
                    >
                      ▲
                    </button>
                    <button
                      type="button"
                      [disabled]="!cv.online"
                      class="glass size-12 rounded-xl text-lg disabled:opacity-40"
                      (click)="$event.stopPropagation(); coverCmd(cv.id, 'STOP')"
                      aria-label="Stopp"
                    >
                      ■
                    </button>
                    <button
                      type="button"
                      [disabled]="!cv.online"
                      class="glass size-12 rounded-xl text-lg disabled:opacity-40"
                      (click)="$event.stopPropagation(); coverCmd(cv.id, 'CLOSE')"
                      aria-label="Ab"
                    >
                      ▼
                    </button>
                  </div>
                </div>
              } @empty {
                <p class="text-sm text-[color:var(--ink-soft)]">Keine Storen.</p>
              }
            </div>
          </article>
        }
      </div>
    </section>
  `,
})
export class DashboardPage {
  private readonly energySvc = inject(EnergyService);
  private readonly tuya = inject(TuyaService);
  private readonly climateSvc = inject(ClimateService);
  private readonly coverSvc = inject(CoverService);
  private readonly sensorSvc = inject(SensorService);
  private readonly safetySvc = inject(SafetyService);
  private readonly batterySvc = inject(BatteryService);
  private readonly weatherSvc = inject(WeatherService);
  /** Raumfilter (geteilt mit der App-Shell): steuert, welche Kacheln sichtbar sind. */
  protected readonly room = inject(RoomService);

  protected readonly weather = this.weatherSvc.weather;

  /** Energie-Kennzahl fürs Dashboard: Fronius bevorzugt, sonst erste OK-Quelle. */
  protected readonly energy = computed<PowerReading | undefined>(() => {
    const readings = this.energySvc.snapshot()?.readings ?? [];
    return (
      readings.find((r) => r.source === 'FRONIUS' && r.status === 'OK') ??
      readings.find((r) => r.status === 'OK')
    );
  });

  /**
   * Batterie-Leistung aus der besten Quelle: der angezeigte Fronius hat oft keine
   * (P_Akku null), die Batterie hängt am SMARTFOX. Erste OK-Quelle mit Wert gewinnt.
   */
  /**
   * Batterie-Zustand fürs Dashboard: Die Batterie wird nur per Taster (Relais)
   * geschaltet – die Lade-Leistung ist dabei systembedingt tief. Daher gilt:
   * Relais ein = lädt, Relais aus = aus. 'unknown' bis der Steuerstand geladen ist.
   */
  protected readonly batteryStatus = computed<'charging' | 'off' | 'unknown'>(() => {
    const control = this.batterySvc.control();
    if (!control) {
      return 'unknown';
    }
    return control.desiredState === 'ON' ? 'charging' : 'off';
  });

  protected readonly stehlampe = computed(() =>
    (this.tuya.switches() ?? []).find((s) => s.id === 'stehlampe'),
  );

  protected readonly palme = computed(() =>
    (this.tuya.switches() ?? []).find((s) => s.id === 'palmenbeleuchtung'),
  );

  protected readonly climate = computed(() => (this.climateSvc.climate() ?? [])[0]);

  protected readonly sensor = computed(() => (this.sensorSvc.sensors() ?? [])[0]);

  protected readonly smoke = this.safetySvc.smokeDetectors;

  /** Namen der Melder im Alarmzustand (für das Banner); leerer String = kein Alarm. */
  protected readonly alarmActive = computed(() => {
    const alarming = (this.safetySvc.smokeDetectors() ?? []).filter(
      (s) => s.online && s.alarm === 'ALARM',
    );
    return alarming.length ? alarming.map((s) => s.name).join(', ') : '';
  });

  protected readonly covers = this.coverSvc.covers;

  /** Storen, gefiltert nach aktivem Raum (bei "Alle" alle). */
  protected readonly visibleCovers = computed(() =>
    (this.coverSvc.covers() ?? []).filter((cv) => this.room.shows(cv.room)),
  );

  protected modeLabel(mode: ClimateMode): string {
    return CLIMATE_MODE_LABELS[mode] ?? mode;
  }

  protected switchToggle(id: string, on: boolean): void {
    this.tuya.switchTo(id, on ? 'ON' : 'OFF');
  }

  protected climatePower(on: boolean): void {
    const c = this.climate();
    if (c) {
      this.climateSvc.setPower(c.id, on);
    }
  }

  protected climateBoost(on: boolean): void {
    const c = this.climate();
    if (c) {
      this.climateSvc.setBoost(c.id, on);
    }
  }

  protected coverCmd(id: string, command: 'OPEN' | 'CLOSE' | 'STOP'): void {
    this.coverSvc.command(id, command);
  }
}
