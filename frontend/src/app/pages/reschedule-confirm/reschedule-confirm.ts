import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { apiErrorDetail } from '../../core/http/api-error';
import { TimePipe } from '../../core/pipes/time.pipe';
import { AppointmentDetails } from '../../core/models/booking.model';
import { BookingService } from '../../core/services/booking.service';

type RescheduleConfirmState = 'idle' | 'confirming' | 'confirmed' | 'error';

/**
 * The page behind the "please confirm this reschedule" email link. Unlike ConfirmPage, which
 * confirms on load, loading this page changes nothing: only the button click sends the POST, so
 * a mail scanner prefetching the link cannot use up the token.
 *
 * The response is a plain appointment with no new access token, so this page reports the outcome
 * instead of offering ManageAppointment.
 */
@Component({
  selector: 'app-reschedule-confirm',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, MatButtonModule, MatProgressSpinnerModule, RouterLink, TimePipe],
  templateUrl: './reschedule-confirm.html',
  styleUrl: './reschedule-confirm.scss',
})
export class RescheduleConfirmPage {
  private readonly route = inject(ActivatedRoute);
  private readonly bookingService = inject(BookingService);

  private readonly token = this.route.snapshot.paramMap.get('token') ?? '';

  readonly state = signal<RescheduleConfirmState>('idle');
  readonly result = signal<AppointmentDetails | null>(null);
  readonly errorMessage = signal<string | null>(null);
  // Only the lost-race 409 needs a way back: the customer has to request the new time again.
  readonly requestAgain = signal(false);

  confirmReschedule(): void {
    // Guards against a double-click firing this twice before [disabled] updates the DOM; the
    // reschedule-confirm token is single-use too.
    if (this.state() !== 'idle') {
      return;
    }
    this.state.set('confirming');
    this.bookingService.confirmReschedule(this.token).subscribe({
      next: (result) => {
        this.state.set('confirmed');
        this.result.set(result);
      },
      error: (err: HttpErrorResponse) => {
        this.state.set('error');
        this.errorMessage.set(this.errorMessageFor(err));
      },
    });
  }

  private errorMessageFor(err: HttpErrorResponse): string {
    const { status } = apiErrorDetail(err);
    if (status === 409) {
      // The expiry sweep released the request in the same instant this confirm arrived.
      this.requestAgain.set(true);
      return 'This reschedule request expired just as you confirmed it. Your appointment is unchanged. Please request the new time again.';
    }
    if (status === 410) {
      return 'This reschedule link has expired or was already used - your appointment is unchanged.';
    }
    if (status === 404) {
      return "We couldn't find that reschedule link.";
    }
    if (status === 422) {
      return 'That change is no longer possible - it may now be too close to the appointment time, or the appointment may no longer be active.';
    }
    return 'Something went wrong confirming your reschedule. Please try again.';
  }
}
