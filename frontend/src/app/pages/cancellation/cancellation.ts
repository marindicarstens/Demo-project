import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { ActivatedRoute } from '@angular/router';
import { apiErrorDetail } from '../../core/http/api-error';
import { TimePipe } from '../../core/pipes/time.pipe';
import { CancellationPreview } from '../../core/models/booking.model';
import { BookingService } from '../../core/services/booking.service';

type CancellationState = 'loading' | 'preview' | 'cancelling' | 'cancelled' | 'error';

/**
 * The page behind the emailed cancellation link. The preview GET is read-only, so it loads
 * straight away; the cancellation POST waits for a click, so a mail scanner prefetching the link
 * cannot cancel the appointment.
 */
@Component({
  selector: 'app-cancellation',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, MatButtonModule, MatProgressSpinnerModule, TimePipe],
  templateUrl: './cancellation.html',
  styleUrl: './cancellation.scss',
})
export class CancellationPage {
  private readonly route = inject(ActivatedRoute);
  private readonly bookingService = inject(BookingService);

  private readonly token = this.route.snapshot.paramMap.get('token') ?? '';

  readonly state = signal<CancellationState>('loading');
  readonly preview = signal<CancellationPreview | null>(null);
  readonly errorMessage = signal<string | null>(null);

  constructor() {
    this.bookingService.previewCancellation(this.token).subscribe({
      next: (preview) => {
        this.preview.set(preview);
        this.state.set('preview');
      },
      error: (err: HttpErrorResponse) => {
        this.state.set('error');
        this.errorMessage.set(this.errorMessageFor(err));
      },
    });
  }

  confirmCancellation(): void {
    // Guards against a double-click firing this twice before [disabled] updates the DOM.
    if (this.state() !== 'preview') {
      return;
    }
    this.state.set('cancelling');
    this.bookingService.confirmCancellation(this.token).subscribe({
      next: () => this.state.set('cancelled'),
      error: (err: HttpErrorResponse) => {
        this.state.set('error');
        this.errorMessage.set(this.errorMessageFor(err));
      },
    });
  }

  private errorMessageFor(err: HttpErrorResponse): string {
    const { status } = apiErrorDetail(err);
    if (status === 410) {
      return 'This cancellation link has expired - the appointment has already passed.';
    }
    if (status === 404) {
      return "We couldn't find that cancellation link.";
    }
    return 'Something went wrong. Please try again.';
  }
}
