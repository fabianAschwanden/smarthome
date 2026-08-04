import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { BatteryPage } from './battery-page';
import { BatteryControl } from '../../core/models/battery';
import { Surplus } from '../../core/models/forecast';

describe('BatteryPage', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [BatteryPage],
      // RouterLink ("Zeitsteuerung →") braucht einen Router im Test.
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
  });

  it('zeigt Modus und Relais-Zustand an', async () => {
    const fixture = TestBed.createComponent(BatteryPage);
    const httpMock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();

    // Der Service pollt über timer(0, …) asynchron; ein Makrotask-Tick löst den
    // ersten Abruf aus (zoneless Test, daher kein fakeAsync).
    await new Promise((resolve) => setTimeout(resolve, 0));

    const control: BatteryControl = {
      mode: 'MANUAL',
      desiredState: 'ON',
      changedAt: '2026-06-19T12:00:00Z',
    };
    httpMock.expectOne('/api/battery').flush(control);
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    expect(element.textContent).toContain('EIN');

    // Im Manuell-Modus ist der Power-Toggle aktiv und zeigt den EIN-Zustand.
    const toggle = element.querySelector<HTMLButtonElement>('button.power-orb');
    expect(toggle).toBeTruthy();
    expect(toggle?.disabled).toBe(false);
    expect(toggle?.getAttribute('aria-pressed')).toBe('true');
  });

  it('zeigt die Ladeempfehlung und uebernimmt sie als Zeitplan', async () => {
    const fixture = TestBed.createComponent(BatteryPage);
    const httpMock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    await new Promise((resolve) => setTimeout(resolve, 0));

    httpMock.expectOne('/api/battery').flush({
      mode: 'MANUAL',
      desiredState: 'OFF',
      changedAt: '2026-08-04T08:00:00Z',
    } satisfies BatteryControl);
    httpMock.expectOne('/api/forecast/pv').flush(null, { status: 204, statusText: 'No Content' });
    const surplus: Surplus = {
      baselineWeekdayWatt: new Array(24).fill(400),
      baselineWeekendWatt: new Array(24).fill(400),
      windows: [],
      recommendation: {
        from: '2026-08-04T09:00:00Z',
        to: '2026-08-04T13:00:00Z',
        expectedKwh: 6.4,
        peakWatt: 2100,
        confidence: 'ROUGH',
      },
    };
    httpMock.expectOne('/api/forecast/surplus').flush(surplus);
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    expect(element.textContent).toContain('Ladeempfehlung');
    expect(element.textContent).toContain('6.4 kWh');
    // ROUGH muss sichtbar sein, sonst wirkt eine Faustformel belastbar.
    expect(element.textContent).toContain('grob');

    const knopf = Array.from(element.querySelectorAll('button')).find((b) =>
      b.textContent?.includes('Als Zeitplan übernehmen'),
    );
    expect(knopf).toBeTruthy();
    knopf?.click();

    const request = httpMock.expectOne('/api/forecast/recommendation/apply');
    expect(request.request.method).toBe('POST');
    request.flush({
      id: 'x',
      type: 'COUNTDOWN',
      action: 'ON',
      enabled: true,
      time: null,
      weekdays: [],
      fireAt: '2026-08-04T09:00:00Z',
    });
    fixture.detectChanges();

    expect(element.textContent).toContain('Zeitplan angelegt');
  });

  it('zeigt keine Karte, wenn keine Empfehlung vorliegt', async () => {
    const fixture = TestBed.createComponent(BatteryPage);
    const httpMock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    await new Promise((resolve) => setTimeout(resolve, 0));

    httpMock.expectOne('/api/battery').flush({
      mode: 'AUTO',
      desiredState: 'OFF',
      changedAt: '2026-08-04T08:00:00Z',
    } satisfies BatteryControl);
    httpMock.expectOne('/api/forecast/pv').flush(null, { status: 204, statusText: 'No Content' });
    httpMock.expectOne('/api/forecast/surplus').flush({
      baselineWeekdayWatt: new Array(24).fill(0),
      baselineWeekendWatt: new Array(24).fill(0),
      windows: [],
      recommendation: null,
    } satisfies Surplus);
    fixture.detectChanges();

    // Kein Fenster an einem trueben Tag ist ein normaler Zustand, keine Fehlermeldung.
    expect((fixture.nativeElement as HTMLElement).textContent).not.toContain('Ladeempfehlung');
  });
});
