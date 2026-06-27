import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { CoverSchedule, CreateCoverScheduleRequest } from '../models/cover-schedule';

/** REST-Zugriff auf die Storen-Zeitsteuerungs-Regeln (BFF). RxJS nur an dieser Grenze. */
@Injectable({ providedIn: 'root' })
export class CoverScheduleService {
  private readonly http = inject(HttpClient);

  all(coverId?: string): Observable<CoverSchedule[]> {
    const query = coverId ? `?coverId=${encodeURIComponent(coverId)}` : '';
    return this.http.get<CoverSchedule[]>(`/api/cover-schedules${query}`);
  }

  create(request: CreateCoverScheduleRequest): Observable<CoverSchedule> {
    return this.http.post<CoverSchedule>('/api/cover-schedules', request);
  }

  setEnabled(id: string, enabled: boolean): Observable<CoverSchedule> {
    return this.http.put<CoverSchedule>(`/api/cover-schedules/${id}/enabled/${enabled}`, {});
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`/api/cover-schedules/${id}`);
  }
}
