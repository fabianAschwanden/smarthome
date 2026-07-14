import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { DashboardPage } from './dashboard-page';

describe('DashboardPage', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DashboardPage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
  });

  it('zeigt die wichtigsten Kacheln (Energie, Stehlampe, Klima, Storen)', async () => {
    const fixture = TestBed.createComponent(DashboardPage);
    const httpMock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();

    // Die vier Services pollen über timer(0, …); ein Makrotask-Tick löst die Abrufe aus.
    await new Promise((resolve) => setTimeout(resolve, 0));

    httpMock.expectOne('/api/energy/current').flush({
      timestamp: 'x',
      readings: [
        {
          source: 'SMARTFOX',
          timestamp: 'x',
          gridWatt: -1500,
          pvWatt: 3900,
          batteryWatt: null,
          consumptionWatt: 2500,
          status: 'OK',
        },
        {
          source: 'FRONIUS',
          timestamp: 'x',
          gridWatt: -1600,
          pvWatt: 4000,
          batteryWatt: null,
          consumptionWatt: 2400,
          status: 'OK',
        },
      ],
      comparison: null,
    });
    httpMock.expectOne('/api/switches').flush([
      {
        id: 'stehlampe',
        name: 'Stehlampe',
        room: 'Wohnzimmer',
        state: 'ON',
        online: true,
        critical: false,
        observedAt: 'x',
      },
    ]);
    httpMock.expectOne('/api/climate').flush([
      {
        id: 'klima',
        name: 'Klimaanlage',
        room: 'Wohnzimmer',
        power: false,
        boost: false,
        mode: 'AUTO',
        targetTemp: 22,
        currentTemp: 21,
        outdoorTemp: 14,
        online: true,
        observedAt: 'x',
      },
    ]);
    httpMock.expectOne('/api/covers').flush([
      {
        id: 'store1',
        name: 'Store 1',
        room: 'Wohnzimmer',
        position: 50,
        online: true,
        observedAt: 'x',
      },
    ]);
    httpMock.expectOne('/api/sensors').flush([
      {
        id: 'innen',
        name: 'Innen',
        room: 'Wohnzimmer',
        temperature: 21.5,
        humidity: 45,
        online: true,
        observedAt: 'x',
      },
    ]);
    httpMock.expectOne('/api/safety/smoke').flush([
      {
        id: 'rauchmelder',
        name: 'Rauchmelder',
        room: 'Wohnzimmer',
        alarm: 'OK',
        battery: 100,
        online: true,
        observedAt: 'x',
      },
    ]);
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Erzeugung'); // Energiefluss-Karte
    expect(el.textContent).toContain('Verbrauch');
    expect(el.textContent).toContain('Stehlampe');
    expect(el.textContent).toContain('Klimaanlage');
    expect(el.textContent).toContain('Store 1');
    expect(el.textContent).toContain('4.00 kW'); // Fronius-Produktion (4000 W, nicht SMARTFOX 3900)
    expect(el.textContent).toContain('Rauchmelder');
    expect(el.textContent).toContain('Innentemperatur');
    expect(el.textContent).toContain('45%'); // Feuchte
  });
});
