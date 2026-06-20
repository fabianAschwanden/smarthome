import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { CreateScheduleRequest, Schedule } from '../models/schedule';

/** REST-Zugriff auf die Zeitsteuerungs-Regeln (BFF). RxJS nur an dieser Grenze. */
@Injectable({ providedIn: 'root' })
export class ScheduleService {
  private readonly http = inject(HttpClient);

  forSwitch(switchId: string): Observable<Schedule[]> {
    return this.http.get<Schedule[]>('/api/schedules', { params: { switchId } });
  }

  create(request: CreateScheduleRequest): Observable<Schedule> {
    return this.http.post<Schedule>('/api/schedules', request);
  }

  setEnabled(id: string, enabled: boolean): Observable<Schedule> {
    return this.http.put<Schedule>(`/api/schedules/${id}/enabled/${enabled}`, {});
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`/api/schedules/${id}`);
  }
}
