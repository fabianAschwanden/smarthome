import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { CoverPage } from './cover-page';
import { Cover } from '../../core/models/cover';

describe('CoverPage', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CoverPage],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
  });

  it('zeigt die Liste der Storen mit Position', async () => {
    const fixture = TestBed.createComponent(CoverPage);
    const httpMock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();

    // timer(0, …) ist asynchron; ein Makrotask-Tick löst den ersten Abruf aus.
    await new Promise((resolve) => setTimeout(resolve, 0));

    const list: Cover[] = [
      { id: 'store1', name: 'Store 1', room: 'Wohnzimmer', position: 60, online: true, observedAt: 'x' },
    ];
    httpMock.expectOne('/api/covers').flush(list);
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Store 1');
    // UI zeigt „% zu" = 100 − Geräteposition (Gerät 60 -> 40 % zu).
    expect(el.textContent).toContain('40%');

    // Online -> Auf-Button aktiv.
    const buttons = Array.from(el.querySelectorAll('button'));
    const open = buttons.find((b) => b.textContent?.includes('Auf'));
    expect(open?.disabled).toBe(false);
  });
});
