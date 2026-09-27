import { Pipe, PipeTransform } from '@angular/core';
import { AppointmentStatus } from '../models/booking.model';

const LABELS: Record<AppointmentStatus, string> = {
  PENDING_CONFIRMATION: 'Awaiting confirmation',
  CONFIRMED: 'Confirmed',
  CANCELLED: 'Cancelled',
  EXPIRED: 'Expired',
};

/** Customer-facing wording for an appointment status, instead of the raw enum name. */
@Pipe({ name: 'statusLabel' })
export class StatusLabelPipe implements PipeTransform {
  transform(status: AppointmentStatus): string {
    return LABELS[status];
  }
}
