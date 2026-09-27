import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, input, output, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { RouterLink } from '@angular/router';
import { apiErrorDetail } from '../../core/http/api-error';
import { AvailabilitySlot } from '../../core/models/availability-slot.model';
import { AppointmentCredential, AppointmentDetails, RescheduleRequestResult } from '../../core/models/booking.model';
import { StatusLabelPipe } from '../../core/pipes/status-label.pipe';
import { TimePipe } from '../../core/pipes/time.pipe';
import { BookingService } from '../../core/services/booking.service';
import { SimulatedInboxService } from '../../core/services/simulated-inbox.service';
import { maxBookableDate } from '../../shared/date-utils';
import { SlotPicker } from '../../shared/slot-picker/slot-picker';

let nextInstanceId = 0;

/**
 * Reschedule and cancel for a CONFIRMED appointment, shared by the lookup page (reference and
 * email) and the post-confirmation screen (scoped access token); see docs/USER-GUIDE.md §5-6.
 * Only a new date and time can be picked: the reschedule request takes just a new slot id.
 *
 * Rescheduling is a request/confirm pair, like a booking hold: this component sends the request,
 * and the emailed link (RescheduleConfirmPage) applies it. A move made there reaches this view
 * through refresh(), which emits `changed`.
 */
@Component({
  selector: 'app-manage-appointment',
  changeDetection: ChangeDetectionStrategy.OnPush,
  // Another tab may confirm a reschedule or cancel; re-read when the customer comes back to this one.
  host: {
    '(window:focus)': 'refresh()',
    '(document:visibilitychange)': 'onVisibilityChange()',
  },
  imports: [DatePipe, MatButtonModule, RouterLink, SlotPicker, StatusLabelPipe, TimePipe],
  templateUrl: './manage-appointment.html',
  styleUrl: './manage-appointment.scss',
})
export class ManageAppointment {
  private readonly bookingService = inject(BookingService);
  private readonly simulatedInboxService = inject(SimulatedInboxService);

  readonly appointment = input.required<AppointmentDetails>();
  readonly credential = input.required<AppointmentCredential>();
  readonly cancelled = output<void>();
  readonly changed = output<AppointmentDetails>();

  readonly mode = signal<'idle' | 'rescheduling' | 'requested' | 'confirmingCancel'>('idle');
  readonly selectedDate = signal<Date>(new Date());
  readonly selectedSlot = signal<AvailabilitySlot | null>(null);
  readonly working = signal(false);
  readonly error = signal<string | null>(null);
  // Set when an access token stopped working: the only way back in is a fresh lookup.
  readonly sessionExpired = signal(false);
  private refreshing = false;

  readonly pendingReschedule = signal<RescheduleRequestResult | null>(null);
  // An expired request no longer holds its slot, so its email can no longer confirm anything.
  readonly livePendingReschedule = computed(() => {
    const pending = this.pendingReschedule();
    return pending && !isExpired(pending) ? pending : null;
  });
  // True if the browser blocked the automatic new-tab open (e.g. a popup blocker) - the "Reopen
  // the email" button still works from here, since a direct click always gets through.
  readonly emailPopupBlocked = signal(false);

  readonly minDate = new Date();
  readonly maxDate = maxBookableDate();

  private readonly instanceId = nextInstanceId++;
  readonly statusLabelId = `appointment-status-label-${this.instanceId}`;
  readonly statusValueId = `appointment-status-${this.instanceId}`;

  startReschedule(): void {
    this.error.set(null);
    this.selectedSlot.set(null);
    this.mode.set('rescheduling');
  }

  onDateChanged(date: Date): void {
    this.selectedDate.set(date);
    this.selectedSlot.set(null);
  }

  selectSlot(slot: AvailabilitySlot): void {
    this.selectedSlot.set(slot);
  }

  /** Keeps pendingReschedule: the request is still live server-side until confirmed or expired,
   * so the summary keeps offering its email. */
  backToSummary(): void {
    this.error.set(null);
    this.mode.set('idle');
  }

  /** Reserves the new slot and opens the confirm-reschedule email in a new tab. The appointment
   * moves only once that email's link is confirmed. */
  requestReschedule(): void {
    // A double click can fire twice before [disabled] reaches the DOM, and the request holds a slot.
    if (this.working()) {
      return;
    }
    const slot = this.selectedSlot();
    if (!slot) {
      return;
    }
    this.working.set(true);
    this.error.set(null);
    this.bookingService.requestReschedule(this.appointment().id, slot.id, this.credential()).subscribe({
      next: (result) => {
        this.working.set(false);
        this.pendingReschedule.set(result);
        this.mode.set('requested');
        const opened = this.simulatedInboxService.open(result.simulatedEmail, 'Confirm this reschedule');
        this.emailPopupBlocked.set(!opened);
      },
      error: (err: HttpErrorResponse) => this.onActionFailed(err),
    });
  }

  reopenRescheduleEmail(): void {
    const pending = this.pendingReschedule();
    if (!pending) {
      return;
    }
    const opened = this.simulatedInboxService.open(pending.simulatedEmail, 'Confirm this reschedule');
    this.emailPopupBlocked.set(!opened);
  }

  /** Cancelling can't be undone, so the first click only asks for confirmation. */
  askToCancel(): void {
    this.error.set(null);
    this.mode.set('confirmingCancel');
  }

  cancelAppointment(): void {
    // Same double-click guard as requestReschedule() above.
    if (this.working()) {
      return;
    }
    this.working.set(true);
    this.error.set(null);
    this.bookingService.cancel(this.appointment().id, this.credential()).subscribe({
      next: () => {
        this.working.set(false);
        this.mode.set('idle');
        this.cancelled.emit();
      },
      error: (err: HttpErrorResponse) => this.onActionFailed(err),
    });
  }

  onVisibilityChange(): void {
    if (document.visibilityState === 'visible') {
      this.refresh();
    }
  }

  /** Background re-read: a failure leaves the view as it was, and the next action reports it. */
  refresh(): void {
    // window:focus and document:visibilitychange can both fire for the same tab switch, which
    // would otherwise send two overlapping GETs.
    if (this.working() || this.refreshing) {
      return;
    }
    const pending = this.pendingReschedule();
    if (pending && isExpired(pending)) {
      this.dropPendingReschedule();
    }
    this.refreshing = true;
    const current = this.appointment();
    this.bookingService.get(current.id, this.credential()).subscribe({
      next: (latest) => {
        this.refreshing = false;
        const moved = latest.date !== current.date || latest.startTime !== current.startTime;
        // Confirmed in the other tab, or the appointment is no longer active: either way the
        // pending request is gone.
        if (moved || latest.status !== 'CONFIRMED') {
          this.dropPendingReschedule();
        }
        if (moved || latest.status !== current.status) {
          this.changed.emit(latest);
        }
      },
      error: () => (this.refreshing = false),
    });
  }

  private dropPendingReschedule(): void {
    this.pendingReschedule.set(null);
    if (this.mode() === 'requested') {
      this.mode.set('idle');
    }
  }

  private onActionFailed(err: HttpErrorResponse): void {
    this.working.set(false);
    const { status, detail } = apiErrorDetail(err);
    const sessionExpired = status === 404 && 'accessToken' in this.credential();
    this.sessionExpired.set(sessionExpired);
    this.error.set(errorMessageFor(status, detail, sessionExpired));
  }
}

function errorMessageFor(status: number, detail: string | undefined, sessionExpired: boolean): string {
  if (status === 409) {
    // A 409 is either a full slot or a reschedule already pending; the server says which.
    return detail ?? 'That time was just taken by someone else - please pick another.';
  }
  if (status === 422) {
    return 'That change is not allowed - it may be too close to your appointment time, or the appointment may no longer be active.';
  }
  if (sessionExpired) {
    return 'Your session has expired. Look up your appointment again.';
  }
  if (status === 404) {
    return "We couldn't find that appointment.";
  }
  return 'Something went wrong. Please try again.';
}

function isExpired(pending: RescheduleRequestResult): boolean {
  return Date.parse(pending.rescheduleRequestExpiresAt) <= Date.now();
}
