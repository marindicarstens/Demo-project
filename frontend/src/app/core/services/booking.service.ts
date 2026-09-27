import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { apiUrl } from '../http/api-url';
import {
  AppointmentCredential,
  AppointmentDetails,
  AppointmentHold,
  BookingRequest,
  CancellationPreview,
  CancellationResult,
  ConfirmationResult,
  RescheduleRequestResult,
} from '../models/booking.model';

@Injectable({ providedIn: 'root' })
export class BookingService {
  private readonly http = inject(HttpClient);

  /** Creates a hold for either client type. Retries of one attempt must reuse the idempotency key,
   * so the backend replays the first result instead of creating a second hold. */
  createAppointment(request: BookingRequest, idempotencyKey: string): Observable<AppointmentHold> {
    return this.http.post<AppointmentHold>(apiUrl('/appointments'), request, {
      headers: { 'Idempotency-Key': idempotencyKey },
    });
  }

  /** Fires automatically when ConfirmPage constructs - i.e. on a bare page load, with no
   * separate in-app click. See ConfirmPage's class doc for why. */
  confirm(token: string): Observable<ConfirmationResult> {
    return this.http.post<ConfirmationResult>(apiUrl(`/confirmations/${encodeURIComponent(token)}`), null);
  }

  lookup(reference: string, email: string): Observable<AppointmentDetails> {
    const params = new HttpParams().set('reference', reference).set('email', email);
    return this.http.get<AppointmentDetails>(apiUrl('/appointments/lookup'), { params });
  }

  /** Re-reads one appointment with the credential the caller already holds, so a view that was
   * open while another tab changed the appointment can catch up. */
  get(appointmentId: string, credential: AppointmentCredential): Observable<AppointmentDetails> {
    const { params, headers } = this.authFor(credential);
    return this.http.get<AppointmentDetails>(apiUrl(`/appointments/${appointmentId}`), { params, headers });
  }

  /** Starts a reschedule: reserves the new slot and emails a confirm link, like createAppointment's
   * hold, without moving the appointment yet. */
  requestReschedule(appointmentId: string, newSlotId: string, credential: AppointmentCredential): Observable<RescheduleRequestResult> {
    const { params, headers } = this.authFor(credential);
    return this.http.post<RescheduleRequestResult>(apiUrl(`/appointments/${appointmentId}/reschedule-request`), { newSlotId }, { params, headers });
  }

  /** Fires only on an explicit user click - never on page load. See RescheduleConfirmPage. */
  confirmReschedule(token: string): Observable<AppointmentDetails> {
    return this.http.post<AppointmentDetails>(apiUrl(`/reschedule-confirmations/${encodeURIComponent(token)}`), null);
  }

  /** Cancel from the website (either the post-confirmation screen or the lookup page) - see
   * docs/USER-GUIDE.md §6. Distinct from confirmCancellation(), which is the email-link path. */
  cancel(appointmentId: string, credential: AppointmentCredential): Observable<void> {
    const { params, headers } = this.authFor(credential);
    return this.http.delete<void>(apiUrl(`/appointments/${appointmentId}`), { params, headers });
  }

  /** Safe, read-only. Fine to call on page load, unlike confirmCancellation(). */
  previewCancellation(token: string): Observable<CancellationPreview> {
    return this.http.get<CancellationPreview>(apiUrl(`/cancellations/${encodeURIComponent(token)}`));
  }

  /** Fires only on an explicit "Yes, cancel my appointment" click - never on page load. */
  confirmCancellation(token: string): Observable<CancellationResult> {
    return this.http.post<CancellationResult>(apiUrl(`/cancellations/${encodeURIComponent(token)}`), null);
  }

  private authFor(credential: AppointmentCredential): { params?: HttpParams; headers?: Record<string, string> } {
    if ('accessToken' in credential) {
      return { headers: { Authorization: `Bearer ${credential.accessToken}` } };
    }
    return { params: new HttpParams().set('reference', credential.reference).set('email', credential.email) };
  }
}
