import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { BatterySchedule, CreateBatteryScheduleRequest } from '../models/battery-schedule';

/** REST-Zugriff auf die Batterie-Zeitsteuerungs-Regeln (BFF). RxJS nur an dieser Grenze. */
@Injectable({ providedIn: 'root' })
export class BatteryScheduleService {
  private readonly http = inject(HttpClient);

  all(): Observable<BatterySchedule[]> {
    return this.http.get<BatterySchedule[]>('/api/battery-schedules');
  }

  create(request: CreateBatteryScheduleRequest): Observable<BatterySchedule> {
    return this.http.post<BatterySchedule>('/api/battery-schedules', request);
  }

  setEnabled(id: string, enabled: boolean): Observable<BatterySchedule> {
    return this.http.put<BatterySchedule>(`/api/battery-schedules/${id}/enabled/${enabled}`, {});
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`/api/battery-schedules/${id}`);
  }
}
