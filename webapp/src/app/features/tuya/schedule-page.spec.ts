import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { SchedulePage } from './schedule-page';
import { Schedule } from '../../core/models/schedule';

describe('SchedulePage', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SchedulePage],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: { get: () => 'stehlampe' } } },
        },
      ],
    }).compileComponents();
  });

  it('lädt und zeigt bestehende Zeitpläne', () => {
    const fixture = TestBed.createComponent(SchedulePage);
    const httpMock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();

    const list: Schedule[] = [
      {
        id: '1',
        switchId: 'stehlampe',
        type: 'SCHEDULE',
        action: 'ON',
        enabled: true,
        time: '07:00',
        weekdays: ['MONDAY'],
        fireAt: null,
        windowStart: null,
        windowEnd: null,
        pulseSeconds: null,
      },
    ];
    httpMock.expectOne((r) => r.url === '/api/schedules').flush(list);
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('07:00');
    expect(el.textContent).toContain('Zeitsteuerung');
    httpMock.verify();
  });
});
