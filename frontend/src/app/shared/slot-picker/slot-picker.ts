import { ChangeDetectionStrategy, Component, inject, input, output } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { AbstractControl, FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { ErrorStateMatcher } from '@angular/material/core';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { AvailabilitySlot } from '../../core/models/availability-slot.model';
import { TimePipe } from '../../core/pipes/time.pipe';
import { AvailabilityService } from '../../core/services/availability.service';
import { toIsoDate } from '../date-utils';

/**
 * A date field plus the free times for one branch and service on that date. The parent owns the
 * date and the selection, so both survive this component being re-created. A selected slot
 * carries its own date, which can differ from the field if the customer changed it afterwards.
 */
@Component({
  selector: 'app-slot-picker',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, MatButtonModule, MatDatepickerModule, MatFormFieldModule, MatInputModule, MatProgressSpinnerModule, TimePipe],
  templateUrl: './slot-picker.html',
  styleUrl: './slot-picker.scss',
})
export class SlotPicker {
  private readonly availabilityService = inject(AvailabilityService);

  readonly branchId = input.required<string>();
  readonly serviceTypeId = input.required<string>();
  readonly date = input.required<Date>();
  readonly min = input.required<Date>();
  readonly max = input.required<Date>();
  readonly dateLabel = input('Date');
  readonly selectedSlotId = input<string | null>(null);
  readonly slotSelected = output<AvailabilitySlot>();
  readonly dateChanged = output<Date>();

  // A new branch, service or date cancels the request still in flight, so a late response can
  // never overwrite the grid; an error drops the previous date's slots instead of keeping them.
  protected readonly slots = rxResource({
    params: () => ({ branchId: this.branchId(), serviceTypeId: this.serviceTypeId(), date: toIsoDate(this.date()) }),
    stream: ({ params }) => this.availabilityService.getAvailability(params.branchId, params.serviceTypeId, params.date),
  });

  // The default waits for blur; a date outside the window should be flagged as it is typed.
  protected readonly showErrorWhileTyping: ErrorStateMatcher = {
    isErrorState: (control: AbstractControl | null) => !!control && control.invalid && (control.dirty || control.touched),
  };

  /** The datepicker reports null while the typed text isn't a valid date yet. A date outside
   * min..max is not reported either: the field shows why instead. */
  protected onDateInput(date: Date | null): void {
    if (date && toIsoDate(date) >= toIsoDate(this.min()) && toIsoDate(date) <= toIsoDate(this.max())) {
      this.dateChanged.emit(date);
    }
  }
}
