import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { App } from './app';

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      // RoomService (via App) injiziert die Geräte-Services, die /api/* pollen –
      // Testing-Backend fängt diese Requests ab, statt echte fetch-Fehler zu werfen.
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
  });

  it('erzeugt die App-Shell mit Navigation', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const compiled = fixture.nativeElement as HTMLElement;
    // Die Shell rendert die Icon-Rail mit Links auf die Feature-Routen.
    expect(compiled.querySelector('a[href="/battery"]')).toBeTruthy();
    expect(compiled.querySelector('router-outlet')).toBeTruthy();
  });
});
