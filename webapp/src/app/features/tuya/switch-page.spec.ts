import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { SwitchPage } from './switch-page';
import { TuyaSwitch } from '../../core/models/tuya';

describe('SwitchPage', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SwitchPage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
  });

  it('zeigt die Liste der Schalter an', async () => {
    const fixture = TestBed.createComponent(SwitchPage);
    const httpMock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();

    // Der Service pollt über timer(0, …) asynchron; ein Makrotask-Tick löst den
    // ersten Abruf aus (zoneless Test, daher kein fakeAsync).
    await new Promise((resolve) => setTimeout(resolve, 0));

    const list: TuyaSwitch[] = [
      {
        id: 'stehlampe',
        name: 'Stehlampe',
        room: 'Wohnzimmer',
        state: 'ON',
        online: true,
        critical: false,
        hint: '',
        observedAt: '2026-06-19T12:00:00Z',
      },
      {
        id: 'palmenbeleuchtung',
        name: 'Palmenbeleuchtung',
        room: 'Garten',
        state: 'OFF',
        online: false,
        critical: false,
        hint: '',
        observedAt: '2026-06-19T12:00:00Z',
      },
    ];
    httpMock.expectOne('/api/switches').flush(list);
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    expect(element.textContent).toContain('Stehlampe');
    expect(element.textContent).toContain('Palmenbeleuchtung');
    expect(element.textContent).toContain('Wohnzimmer');

    // Power-Toggles: online-Gerät aktiv, offline-Gerät gesperrt.
    const toggles = Array.from(element.querySelectorAll<HTMLButtonElement>('button.power-orb'));
    expect(toggles.length).toBe(2);
    const enabled = toggles.filter((b) => !b.disabled);
    expect(enabled.length).toBe(1);
  });
});
