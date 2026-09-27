import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { apiUrl } from '../http/api-url';
import { ClientType, ServiceType } from '../models/service-type.model';

@Injectable({ providedIn: 'root' })
export class ServiceTypeService {
  private readonly http = inject(HttpClient);

  listFor(clientType: ClientType): Observable<ServiceType[]> {
    return this.http.get<ServiceType[]>(apiUrl('/service-types'), {
      params: { clientType },
    });
  }
}
