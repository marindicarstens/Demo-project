import { Branch } from './branch.model';
import { ServiceType } from './service-type.model';

// Matches the backend AppointmentStatus enum names, which the API sends as plain strings.
export type AppointmentStatus = 'PENDING_CONFIRMATION' | 'CONFIRMED' | 'CANCELLED' | 'EXPIRED';

// Matches docs/api/openapi.yaml.
export interface NewClientBookingRequest {
  clientType: 'NEW_CLIENT';
  branchId: string;
  serviceTypeId: string;
  slotId: string;
  fullName: string;
  email: string;
  phone: string;
}

export interface ExistingClientBookingRequest {
  clientType: 'EXISTING_CLIENT';
  branchId: string;
  serviceTypeId: string;
  slotId: string;
  email: string;
  idNumber: string;
  accountNumber: string;
}

export type BookingRequest = NewClientBookingRequest | ExistingClientBookingRequest;

export interface SimulatedEmail {
  subject: string;
  from: string;
  bodyText: string;
  actionLink: string | null;
  sentAt: string;
}

export interface AppointmentHold {
  appointmentId: string;
  referenceCode: string;
  status: AppointmentStatus;
  holdExpiresAt: string;
  simulatedEmail: SimulatedEmail;
}

export interface ConfirmationResult {
  appointmentId: string;
  referenceCode: string;
  status: AppointmentStatus;
  accessToken: string;
  accessTokenExpiresAt: string;
  receiptEmail: SimulatedEmail;
  branch: Branch;
  serviceType: ServiceType;
  date: string;
  startTime: string;
}

// Matches the Appointment schema in docs/api/openapi.yaml - the shape returned by lookup,
// reschedule, and (as the appointment nested inside a hold/confirmation) implicitly elsewhere.
export interface AppointmentDetails {
  id: string;
  referenceCode: string;
  status: AppointmentStatus;
  branch: Branch;
  serviceType: ServiceType;
  date: string;
  startTime: string;
  /** Sent by lookup and GET; a confirmation result has no such field, so it is absent there. */
  createdAt?: string;
}

/** The credential for reschedule and cancel: exactly one of the two shapes, matching the
 * referenceEmailAuth and appointmentAccessToken security schemes in docs/api/openapi.yaml. */
export type AppointmentCredential = { accessToken: string } | { reference: string; email: string };

// Matches the RescheduleRequestResult schema in docs/api/openapi.yaml: the response to
// POST /appointments/{id}/reschedule-request, the "hold" half of a reschedule. The appointment has
// not moved yet, so status stays CONFIRMED.
export interface RescheduleRequestResult {
  appointmentId: string;
  referenceCode: string;
  status: AppointmentStatus;
  rescheduleRequestExpiresAt: string;
  simulatedEmail: SimulatedEmail;
}

export interface CancellationPreview {
  referenceCode: string;
  branchName: string;
  serviceTypeName: string;
  date: string;
  startTime: string;
  alreadyCancelled: boolean;
}

export interface CancellationResult {
  referenceCode: string;
  status: AppointmentStatus;
}
