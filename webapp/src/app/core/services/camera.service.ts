import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Camera } from '../models/camera';

/** Lädt die Kamera-Metadaten vom eigenen Backend (BFF). Statisch – einmaliger Abruf. */
@Injectable({ providedIn: 'root' })
export class CameraService {
  private readonly http = inject(HttpClient);

  private readonly state = signal<Camera[] | null>(null);
  readonly cameras = this.state.asReadonly();

  constructor() {
    this.http
      .get<Camera[]>('/api/cameras')
      .pipe(takeUntilDestroyed())
      .subscribe((value) => this.state.set(value));
  }
}
