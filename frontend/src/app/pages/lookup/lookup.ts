import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { ManageAppointment } from '../../components/manage-appointment/manage-appointment';
import { apiErrorDetail } from '../../core/http/api-error';
import { AppointmentCredential, AppointmentDetails } from '../../core/models/booking.model';
import { BookingService } from '../../core/services/booking.service';

/** "Look up my booking" - reference code + email, per docs/USER-GUIDE.md §5. */
@Component({
  selector: 'app-lookup',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, MatButtonModule, MatFormFieldModule, MatInputModule, ManageAppointment],
  templateUrl: './lookup.html',
  styleUrl: './lookup.scss',
})
export class LookupPage {
  private readonly formBuilder = inject(FormBuilder);
  private readonly bookingService = inject(BookingService);

  readonly lookupForm = this.formBuilder.nonNullable.group({
    reference: ['', Validators.required],
    email: ['', [Validators.required, Validators.email]],
  });

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly appointment = signal<AppointmentDetails | null>(null);
  readonly credential = signal<AppointmentCredential | null>(null);

  lookup(): void {
    if (this.loading()) {
      return;
    }
    // Reference codes are issued in upper case; people often paste them with stray spaces. Normalised
    // before validation, since the email validator rejects surrounding spaces.
    const raw = this.lookupForm.getRawValue();
    this.lookupForm.setValue({ reference: raw.reference.trim().toUpperCase(), email: raw.email.trim() });
    if (this.lookupForm.invalid) {
      this.lookupForm.markAllAsTouched();
      return;
    }
    const { reference, email } = this.lookupForm.getRawValue();
    this.loading.set(true);
    this.error.set(null);
    this.bookingService.lookup(reference, email).subscribe({
      next: (found) => {
        this.loading.set(false);
        this.appointment.set(found);
        this.credential.set({ reference, email });
      },
      error: (err: HttpErrorResponse) => {
        this.loading.set(false);
        this.error.set(this.errorMessageFor(err));
      },
    });
  }

  onCancelled(): void {
    const current = this.appointment();
    if (current) {
      this.appointment.set({ ...current, status: 'CANCELLED' });
    }
  }

  private errorMessageFor(err: HttpErrorResponse): string {
    const { status } = apiErrorDetail(err);
    if (status === 404) {
      return "We couldn't find a booking matching that reference and email.";
    }
    if (status === 429) {
      return 'Too many attempts, try again in a few minutes.';
    }
    return 'Something went wrong. Please try again.';
  }

  searchAgain(): void {
    this.appointment.set(null);
    this.credential.set(null);
    this.error.set(null);
  }
}
