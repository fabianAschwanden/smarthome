import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { BatteryPage } from './battery-page';
import { BatteryControl } from '../../core/models/battery';

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
});
