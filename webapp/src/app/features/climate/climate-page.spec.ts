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
        mode: 'COOL',
        targetTemp: 22,
        currentTemp: 21,
        outdoorTemp: 14,
        online: true,
        observedAt: 'x',
      },
    ];
    httpMock.expectOne('/api/climate').flush(list);
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Klimaanlage');
    expect(el.textContent).toContain('22°');
    expect(el.textContent).toContain('Kühlen');
  });
});
