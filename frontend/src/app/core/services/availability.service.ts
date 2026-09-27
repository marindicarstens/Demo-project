import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { apiUrl } from '../http/api-url';
import { AvailabilitySlot } from '../models/availability-slot.model';

@Injectable({ providedIn: 'root' })
export class AvailabilityService {
  private readonly http = inject(HttpClient);

  /** @param date ISO format (yyyy-MM-dd) */
  getAvailability(branchId: string, serviceTypeId: string, date: string): Observable<AvailabilitySlot[]> {
    return this.http.get<AvailabilitySlot[]>(apiUrl(`/branches/${branchId}/availability`), {
      params: { date, serviceTypeId },
    });
  }
}
