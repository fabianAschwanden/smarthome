import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { AppliancePage } from './appliance-page';
import { Appliance } from '../../core/models/appliance';

describe('AppliancePage', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AppliancePage],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
  });

  it('zeigt Anlagen mit ihren Funktionen', async () => {
    const fixture = TestBed.createComponent(AppliancePage);
    const httpMock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();

    // timer(0, …) ist asynchron; ein Makrotask-Tick löst den ersten Abruf aus.
    await new Promise((resolve) => setTimeout(resolve, 0));

    const list: Appliance[] = [
      {
        id: 'whirlpool',
        name: 'Whirlpool',
        room: 'Wellness',
        online: true,
        active: true,
        observedAt: 'x',
        functions: { PUMP: 'OFF', HEATER: 'ON' },
      },
    ];
    httpMock.expectOne('/api/appliances').flush(list);
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Whirlpool');
    expect(el.textContent).toContain('Pumpe');
  });

  it('zeigt keinen Schalter für die Heizung', async () => {
    // Die Heizung eines Gecko-Spas lässt sich nicht schalten – sie folgt der
    // Soll-Temperatur, und die App weist ein Ein/Aus mit 503 zurück. Ein Knopf, der nur
    // scheitern kann, gehört nicht auf die Kachel.
    const fixture = TestBed.createComponent(AppliancePage);
    const httpMock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    await new Promise((resolve) => setTimeout(resolve, 0));

    const list: Appliance[] = [
      {
        id: 'whirlpool',
        name: 'Whirlpool',
        room: 'Wellness',
        online: true,
        active: true,
        observedAt: 'x',
        functions: { PUMP: 'OFF', HEATER: 'ON' },
      },
    ];
    httpMock.expectOne('/api/appliances').flush(list);
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Pumpe');
    expect(el.textContent).not.toContain('Heizung');
  });

  it('zeigt eine stillgelegte Anlage ohne Bedienelemente und mit Reaktivieren', async () => {
    // Vom Strom genommen ist kein Fehler: keine rote Lampe, kein "Nicht erreichbar",
    // keine Schalter, die nur scheitern koennten - aber ein Weg zurueck.
    const fixture = TestBed.createComponent(AppliancePage);
    const httpMock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    await new Promise((resolve) => setTimeout(resolve, 0));

    const list: Appliance[] = [
      {
        id: 'pool',
        name: 'Schwimmbecken',
        room: 'Garten',
        online: false,
        active: false,
        observedAt: 'x',
        functions: { PUMP: 'OFF', HEATER: 'OFF' },
        temperature: { target: 15, current: 20, min: 8, max: 41, activity: 'IDLE' },
      },
    ];
    httpMock.expectOne('/api/appliances').flush(list);
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Stillgelegt');
    expect(el.textContent).not.toContain('Nicht erreichbar');
    expect(el.querySelector('.tile-toggle')).toBeNull();

    const knopf = Array.from(el.querySelectorAll('button')).find((b) =>
      b.textContent?.includes('Wieder in Betrieb nehmen'),
    );
    expect(knopf).toBeTruthy();
    knopf?.click();

    const request = httpMock.expectOne('/api/appliances/pool/active');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({ active: true });
    request.flush({ ...list[0], active: true, online: true });
    fixture.detectChanges();

    expect(el.textContent).toContain('Online');
    expect(el.querySelector('.tile-toggle')).not.toBeNull();
  });

  it('faerbt eine heizende Anlage warm ein und sagt es dazu', async () => {
    const fixture = TestBed.createComponent(AppliancePage);
    const httpMock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    await new Promise((resolve) => setTimeout(resolve, 0));

    const list: Appliance[] = [
      {
        id: 'whirlpool',
        name: 'Whirlpool',
        room: 'Wellness',
        online: true,
        active: true,
        observedAt: 'x',
        functions: { PUMP: 'OFF', HEATER: 'ON' },
        temperature: { target: 33, current: 29, min: 8, max: 41, activity: 'HEATING' },
      },
    ];
    httpMock.expectOne('/api/appliances').flush(list);
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('article.thermal-warm')).not.toBeNull();
    expect(el.textContent).toContain('heizt');
  });
});
