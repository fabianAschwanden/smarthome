import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ForecastService } from './forecast.service';
import { PvForecast, Surplus } from '../models/forecast';

const PROGNOSE: PvForecast = {
  hours: [
    { hour: '2026-08-04T09:00:00Z', expectedPvWatt: 3200, gti: 520 },
    { hour: '2026-08-04T10:00:00Z', expectedPvWatt: 4800, gti: 760 },
  ],
  todayKwh: 21.4,
  tomorrowKwh: 18.2,
  confidence: 'LEARNED',
  learnedAt: '2026-08-04T03:00:00Z',
  computedAt: '2026-08-04T08:00:00Z',
};

const UEBERSCHUSS: Surplus = {
  baselineWeekdayWatt: new Array(24).fill(400),
  baselineWeekendWatt: new Array(24).fill(600),
  windows: [
    { from: '2026-08-04T09:00:00Z', to: '2026-08-04T13:00:00Z', expectedKwh: 6.4, peakWatt: 2100 },
  ],
  recommendation: {
    from: '2026-08-04T09:00:00Z',
    to: '2026-08-04T13:00:00Z',
    expectedKwh: 6.4,
    peakWatt: 2100,
    confidence: 'LEARNED',
  },
};

const AUTOMATIK = {
  enabled: false,
  lastRunDay: null,
  lastOutcome: null,
  lastDetail: '',
};

describe('ForecastService', () => {
  let service: ForecastService;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ForecastService);
    httpMock = TestBed.inject(HttpTestingController);
    // Der Service pollt über timer(0, …) asynchron; ein Makrotask-Tick löst den
    // ersten Abruf aus (zoneless Test, daher kein fakeAsync).
    await new Promise((resolve) => setTimeout(resolve, 0));
    // Der Zustand der Lade-Automatik wird einmal beim Start geholt, damit die
    // Nachrichtenzentrale ihn kennt, ohne dass jemand die Batterie-Seite geöffnet hat.
    httpMock.expectOne('/api/forecast/auto-apply').flush(AUTOMATIK);
  });

  it('stellt Prognose und Überschuss als Signals bereit', () => {
    httpMock.expectOne('/api/forecast/pv').flush(PROGNOSE);
    httpMock.expectOne('/api/forecast/surplus').flush(UEBERSCHUSS);

    expect(service.pv()?.todayKwh).toBe(21.4);
    expect(service.pv()?.hours.length).toBe(2);
    expect(service.surplus()?.recommendation?.expectedKwh).toBe(6.4);
    expect(service.surplus()?.baselineWeekdayWatt.length).toBe(24);
  });

  it('behandelt 204 (noch nie gerechnet) als leere Prognose', () => {
    // Das Backend antwortet mit 204, solange kein Refresh durch ist – das darf keinen
    // Fehler auslösen, sondern muss schlicht "noch nichts da" bedeuten.
    httpMock.expectOne('/api/forecast/pv').flush(null, { status: 204, statusText: 'No Content' });
    httpMock.expectOne('/api/forecast/surplus').flush(UEBERSCHUSS);

    expect(service.pv()).toBeNull();
  });

  it('schaltet die Lade-Automatik per PUT um', () => {
    httpMock.expectOne('/api/forecast/pv').flush(PROGNOSE);
    httpMock.expectOne('/api/forecast/surplus').flush(UEBERSCHUSS);
    expect(service.autoApply()?.enabled).toBe(false);

    service.setAutoApply(true);

    const request = httpMock.expectOne('/api/forecast/auto-apply');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({ enabled: true });
    request.flush({ ...AUTOMATIK, enabled: true });

    expect(service.autoApply()?.enabled).toBe(true);
  });

  it('übernimmt die Empfehlung per POST', () => {
    httpMock.expectOne('/api/forecast/pv').flush(PROGNOSE);
    httpMock.expectOne('/api/forecast/surplus').flush(UEBERSCHUSS);

    let angelegt: string | undefined;
    service.applyRecommendation().subscribe((schedule) => (angelegt = schedule.id));

    const request = httpMock.expectOne('/api/forecast/recommendation/apply');
    expect(request.request.method).toBe('POST');
    request.flush({
      id: 'abc',
      type: 'COUNTDOWN',
      action: 'ON',
      enabled: true,
      time: null,
      weekdays: [],
      fireAt: '2026-08-04T09:00:00Z',
    });

    expect(angelegt).toBe('abc');
  });

  afterEach(() => httpMock.verify());
});
