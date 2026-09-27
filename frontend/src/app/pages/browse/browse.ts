import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatChipSelectionChange, MatChipsModule } from '@angular/material/chips';
import { MatIconModule } from '@angular/material/icon';
import { ActivatedRoute, Router } from '@angular/router';
import { apiErrorDetail } from '../../core/http/api-error';
import { AvailabilitySlot } from '../../core/models/availability-slot.model';
import { AppointmentHold, BookingRequest } from '../../core/models/booking.model';
import { Branch } from '../../core/models/branch.model';
import { ClientType, ServiceType } from '../../core/models/service-type.model';
import { TimePipe } from '../../core/pipes/time.pipe';
import { BookingService } from '../../core/services/booking.service';
import { ServiceTypeService } from '../../core/services/service-type.service';
import { SimulatedInboxService } from '../../core/services/simulated-inbox.service';
import { maxBookableDate } from '../../shared/date-utils';
import { SlotPicker } from '../../shared/slot-picker/slot-picker';
import { bookingErrorFor } from './booking-error';
import { BranchStep } from './branch-step/branch-step';
import { ContactDetails, ContactStep } from './contact-step/contact-step';
import { IdentityDetails, IdentityStep } from './identity-step/identity-step';

/** Booking for both client types. New Account gives contact details first and then browses; Existing Client browses
 * first and identifies itself just before booking. */
@Component({
  selector: 'app-browse',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [BranchStep, ContactStep, DatePipe, IdentityStep, MatButtonModule, MatChipsModule, MatIconModule, SlotPicker, TimePipe],
  templateUrl: './browse.html',
  styleUrls: ['./browse-step.scss', './browse.scss'],
})
export class BrowsePage {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly serviceTypeService = inject(ServiceTypeService);
  private readonly bookingService = inject(BookingService);
  private readonly simulatedInboxService = inject(SimulatedInboxService);

  readonly clientType = signal<ClientType>(this.route.snapshot.paramMap.get('clientType') === 'existing-client' ? 'EXISTING_CLIENT' : 'NEW_CLIENT');
  readonly clientTypeLabel = computed(() => (this.clientType() === 'EXISTING_CLIENT' ? 'Existing Client' : 'New Account'));
  readonly contact = signal<ContactDetails | null>(null);
  readonly showBrowseSteps = computed(() => !this.hold() && (this.clientType() === 'EXISTING_CLIENT' || this.contact() !== null));

  readonly selectedBranch = signal<Branch | null>(null);

  protected readonly serviceTypesResource = rxResource({
    params: () => this.clientType(),
    stream: ({ params }) => this.serviceTypeService.listFor(params),
  });
  readonly serviceTypes = computed(() => (this.serviceTypesResource.hasValue() ? this.serviceTypesResource.value() : []));
  readonly selectedServiceType = signal<ServiceType | null>(null);

  readonly selectedDate = signal<Date>(new Date());
  readonly minDate = new Date();
  readonly maxDate = maxBookableDate();
  readonly selectedSlot = signal<AvailabilitySlot | null>(null);
  // Kept across retries of the same slot so the backend replays the first result rather than creating a second hold.
  private idempotencyKey: string | null = null;

  readonly booking = signal(false);
  readonly bookingError = signal<string | null>(null);
  readonly directoryMismatch = signal(false);
  readonly hold = signal<AppointmentHold | null>(null);
  private holdEmail = '';
  // A blocked new tab (popup blocker) still leaves the "Reopen the email" click, which always works.
  readonly emailPopupBlocked = signal(false);
  readonly alreadyConfirmedElsewhere = signal(false);
  readonly holdExpired = signal(false);

  onBranchSelected(branch: Branch): void {
    this.selectedBranch.set(branch);
    this.clearSlot();
  }

  selectServiceType(serviceType: ServiceType): void {
    this.selectedServiceType.set(serviceType);
    this.clearSlot();
  }

  /** Fires for mouse and keyboard alike, and also when [selected] changes. A service is always
   * required, so a chip the customer un-picks is picked again. */
  onServiceChipChange(change: MatChipSelectionChange, serviceType: ServiceType): void {
    if (change.selected && this.selectedServiceType()?.id !== serviceType.id) {
      this.selectServiceType(serviceType);
    } else if (!change.selected && change.isUserInput) {
      change.source.selected = true;
    }
  }

  onDateChanged(date: Date): void {
    this.selectedDate.set(date);
    this.clearSlot();
  }

  selectSlot(slot: AvailabilitySlot): void {
    this.idempotencyKey = this.selectedSlot()?.id === slot.id ? this.idempotencyKey : crypto.randomUUID();
    this.selectedSlot.set(slot);
    this.bookingError.set(null);
    this.holdExpired.set(false);
  }

  bookAsNewClient(): void {
    const contact = this.contact();
    const base = this.bookingBase();
    if (contact && base) {
      this.book({ clientType: 'NEW_CLIENT', ...base, ...contact });
    }
  }

  bookAsExistingClient(identity: IdentityDetails): void {
    const base = this.bookingBase();
    if (base) {
      this.book({ clientType: 'EXISTING_CLIENT', ...base, ...identity });
    }
  }

  book(request: BookingRequest): void {
    // A double click can fire twice before [disabled] reaches the DOM, and a hold is single-use.
    if (this.booking()) {
      return;
    }
    this.booking.set(true);
    this.bookingError.set(null);
    this.directoryMismatch.set(false);
    this.idempotencyKey ??= crypto.randomUUID();
    this.bookingService.createAppointment(request, this.idempotencyKey).subscribe({
      next: (hold) => this.onHoldCreated(hold, request.email),
      error: (err: HttpErrorResponse) => {
        const { message, directoryMismatch, clearSlot, newIdempotencyKey } = bookingErrorFor(apiErrorDetail(err));
        this.booking.set(false);
        this.bookingError.set(message);
        this.directoryMismatch.set(directoryMismatch);
        this.selectedSlot.update((slot) => (clearSlot ? null : slot));
        this.idempotencyKey = newIdempotencyKey ? null : this.idempotencyKey;
      },
    });
  }

  /** Offered on every directory mismatch. Branch and date carry over; the service resets because the New Account catalog
   * is narrower, and the identity details go with their step. The URL follows the client type; the router reuses this
   * component for the same route, so the state set here survives the navigation. */
  continueAsNewAccount(): void {
    this.clientType.set('NEW_CLIENT');
    this.selectedServiceType.set(null);
    this.selectedSlot.set(null);
    this.directoryMismatch.set(false);
    this.bookingError.set(null);
    void this.router.navigate(['/browse', 'new-client'], { replaceUrl: true });
  }

  /** Checks the hold before reopening its email: a link already used by the other tab, or of an
   * expired hold, can no longer confirm. */
  reopenEmail(): void {
    const hold = this.hold();
    if (hold) {
      this.bookingService.lookup(hold.referenceCode, this.holdEmail).subscribe({
        next: (appointment) => {
          if (appointment.status === 'CONFIRMED') {
            this.alreadyConfirmedElsewhere.set(true);
          } else if (appointment.status === 'EXPIRED') {
            this.holdExpired.set(true);
            this.hold.set(null);
            this.selectedSlot.set(null);
          } else {
            this.openSimulatedEmail(hold);
          }
        },
        // Not found, or a network error: reopening the original email is still the best fallback.
        error: () => this.openSimulatedEmail(hold),
      });
    }
  }

  private clearSlot(): void {
    this.selectedSlot.set(null);
    this.bookingError.set(null);
  }

  private bookingBase(): { branchId: string; serviceTypeId: string; slotId: string } | null {
    const [branch, serviceType, slot] = [this.selectedBranch(), this.selectedServiceType(), this.selectedSlot()];
    return branch && serviceType && slot ? { branchId: branch.id, serviceTypeId: serviceType.id, slotId: slot.id } : null;
  }

  private onHoldCreated(hold: AppointmentHold, email: string): void {
    this.booking.set(false);
    this.holdExpired.set(false);
    this.hold.set(hold);
    this.holdEmail = email;
    this.idempotencyKey = null;
    this.openSimulatedEmail(hold);
  }

  private openSimulatedEmail(hold: AppointmentHold): void {
    const opened = this.simulatedInboxService.open(hold.simulatedEmail, 'Confirm my appointment');
    this.emailPopupBlocked.set(!opened);
  }
}
