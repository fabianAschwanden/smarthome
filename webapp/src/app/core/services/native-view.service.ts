import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NativeView } from '../models/native-view';

/** Lädt die Metadaten der nativen Geräte-Weboberflächen vom eigenen Backend (BFF). */
@Injectable({ providedIn: 'root' })
export class NativeViewService {
  private readonly http = inject(HttpClient);

  private readonly state = signal<NativeView[] | null>(null);
  readonly views = this.state.asReadonly();

  constructor() {
    this.http
      .get<NativeView[]>('/api/native')
      .pipe(takeUntilDestroyed())
      .subscribe((value) => this.state.set(value));
  }
}
