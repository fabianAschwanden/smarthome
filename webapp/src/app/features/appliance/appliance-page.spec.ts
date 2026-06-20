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
        observedAt: 'x',
        functions: { PUMP: 'OFF', HEATER: 'ON' },
      },
    ];
    httpMock.expectOne('/api/appliances').flush(list);
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Whirlpool');
    expect(el.textContent).toContain('Pumpe');
    expect(el.textContent).toContain('Heizung');
  });
});
