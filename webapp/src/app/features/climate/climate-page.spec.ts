import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ClimatePage } from './climate-page';
import { Climate } from '../../core/models/climate';

describe('ClimatePage', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ClimatePage],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
  });

  it('zeigt Klimaanlage mit Soll-/Ist-Temperatur und Modi', async () => {
    const fixture = TestBed.createComponent(ClimatePage);
    const httpMock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();

    // timer(0, …) ist asynchron; ein Makrotask-Tick löst den ersten Abruf aus.
    await new Promise((resolve) => setTimeout(resolve, 0));

    const list: Climate[] = [
      {
        id: 'klima',
        name: 'Klimaanlage',
        room: 'Wohnzimmer',
        power: true,
        boost: false,
        mode: 'COOL',
        targetTemp: 22,
        currentTemp: 21,
        outdoorTemp: 14,
        online: true,
        active: true,
        activity: 'COOLING',
        observedAt: 'x',
      },
    ];
    httpMock.expectOne('/api/climate').flush(list);
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Klimaanlage');
    expect(el.textContent).toContain('22°');
    expect(el.textContent).toContain('Kühlen');
    // Kuehlt gerade: kuehle Karte plus das Wort dazu - Farbe allein ist keine Information.
    expect(el.querySelector('article.thermal-cool')).not.toBeNull();
    expect(el.querySelector('article.thermal-warm')).toBeNull();
    expect(el.textContent).toContain('kühlt');
  });

  it('zeigt eine stillgelegte Klimaanlage ohne Bedienelemente und mit Reaktivieren', async () => {
    const fixture = TestBed.createComponent(ClimatePage);
    const httpMock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    await new Promise((resolve) => setTimeout(resolve, 0));

    const list: Climate[] = [
      {
        id: 'klima',
        name: 'Klimaanlage',
        room: 'Wohnzimmer',
        power: false,
        boost: false,
        mode: 'COOL',
        targetTemp: 22,
        currentTemp: 21,
        outdoorTemp: 14,
        online: false,
        active: false,
        activity: 'IDLE',
        observedAt: 'x',
      },
    ];
    httpMock.expectOne('/api/climate').flush(list);
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Stillgelegt');
    // Vom Strom genommen ist kein Fehler - keine Warnung, keine Schalter.
    expect(el.textContent).not.toContain('nicht erreichbar');
    expect(el.querySelector('.tile-toggle')).toBeNull();
    expect(el.querySelector('button.power-orb')).toBeNull();

    const knopf = Array.from(el.querySelectorAll('button')).find((b) =>
      b.textContent?.includes('Wieder in Betrieb nehmen'),
    );
    expect(knopf).toBeTruthy();
    knopf?.click();

    const request = httpMock.expectOne('/api/climate/klima/active');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({ active: true });
  });
});
