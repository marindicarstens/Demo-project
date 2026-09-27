import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ManageAppointment } from '../../components/manage-appointment/manage-appointment';
import { apiErrorDetail } from '../../core/http/api-error';
import { AppointmentCredential, AppointmentDetails, ConfirmationResult } from '../../core/models/booking.model';
import { BookingService } from '../../core/services/booking.service';
import { SimulatedInboxService } from '../../core/services/simulated-inbox.service';

type ConfirmState = 'confirming' | 'confirmed' | 'error';

/**
 * Confirms on page load, a deliberate exception to the app's "only a click changes state" rule for
 * emailed links: a mail scanner that prefetches this link can use up the single-use token first.
 * A used token is not re-issued, so a refresh or replay points the customer to /manage instead.
 */
@Component({
  selector: 'app-confirm',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatButtonModule, MatProgressSpinnerModule, ManageAppointment, RouterLink],
  templateUrl: './confirm.html',
  styleUrl: './confirm.scss',
})
export class ConfirmPage {
  private readonly route = inject(ActivatedRoute);
  private readonly bookingService = inject(BookingService);
  private readonly simulatedInboxService = inject(SimulatedInboxService);

  private readonly token = this.route.snapshot.paramMap.get('token') ?? '';

  readonly state = signal<ConfirmState>('confirming');
  readonly result = signal<ConfirmationResult | null>(null);
  readonly errorMessage = signal<string | null>(null);
  // A 410 covers both a used link (maybe by this same customer, before a refresh) and an expired
  // hold. Only a lookup tells them apart, so point there rather than to booking again.
  readonly linkExpiredOrUsed = signal(false);
  readonly receiptPopupBlocked = signal(false);

  // The scoped access token issued at confirmation "stays unlocked for that browsing session" -
  // see docs/USER-GUIDE.md §6 - so reschedule/cancel are offered right here, no re-auth needed.
  readonly appointment = computed<AppointmentDetails | null>(() => {
    const confirmed = this.result();
    if (!confirmed) {
      return null;
    }
    return {
      id: confirmed.appointmentId,
      referenceCode: confirmed.referenceCode,
      status: confirmed.status,
      branch: confirmed.branch,
      serviceType: confirmed.serviceType,
      date: confirmed.date,
      startTime: confirmed.startTime,
    };
  });
  readonly credential = computed<AppointmentCredential | null>(() => {
    const confirmed = this.result();
    return confirmed ? { accessToken: confirmed.accessToken } : null;
  });

  constructor() {
    // Fires on construction, on a bare page load. See the class comment above.
    this.bookingService.confirm(this.token).subscribe({
      next: (result) => {
        this.state.set('confirmed');
        this.result.set(result);
      },
      error: (err: HttpErrorResponse) => {
        this.state.set('error');
        const { status } = apiErrorDetail(err);
        if (status === 410) {
          this.linkExpiredOrUsed.set(true);
          this.errorMessage.set('This link has expired or was already used. Look up your booking to see its status.');
        } else if (status === 404) {
          this.errorMessage.set("We couldn't find that confirmation link.");
        } else {
          this.errorMessage.set('Something went wrong confirming your appointment. Please try again.');
        }
      },
    });
  }

  /** The receipt carries the cancellation link, the email-based way to cancel later. */
  viewReceipt(): void {
    const confirmed = this.result();
    if (!confirmed) {
      return;
    }
    const opened = this.simulatedInboxService.open(confirmed.receiptEmail, 'Cancel my appointment');
    this.receiptPopupBlocked.set(!opened);
  }

  onAppointmentChanged(latest: AppointmentDetails): void {
    const current = this.result();
    if (current) {
      const { status, branch, serviceType, date, startTime } = latest;
      this.result.set({ ...current, status, branch, serviceType, date, startTime });
    }
  }

  onAppointmentCancelled(): void {
    const current = this.result();
    if (current) {
      this.result.set({ ...current, status: 'CANCELLED' });
    }
  }
}
