import { ApiErrorDetail } from '../../core/http/api-error';

/** The ProblemDetail code the backend sends on a 409 for a slot with no remaining capacity. */
export const SLOT_FULL_CODE = 'SLOT_FULL';

/** What the booking page shows and resets after a failed booking attempt. */
export interface BookingErrorOutcome {
  message: string;
  /** The existing-client details matched no one, so offer the New Account fallback. */
  directoryMismatch: boolean;
  /** The slot is gone, so the selection must be cleared. */
  clearSlot: boolean;
  /** The request was rejected as sent (or its key was already used for another), so the next
   * attempt must use a new Idempotency-Key. */
  newIdempotencyKey: boolean;
}

/** Maps a failed booking to its outcome. A 409 is a full slot when it carries the SLOT_FULL code;
 * a server that predates the code is recognised by its detail text instead. */
export function bookingErrorFor({ status, detail, code, suggestNewAccount }: ApiErrorDetail): BookingErrorOutcome {
  const outcome: BookingErrorOutcome = { message: '', directoryMismatch: false, clearSlot: false, newIdempotencyKey: false };
  if (status === 422 && suggestNewAccount) {
    // Deliberately generic: it never hints which field did not match.
    return { ...outcome, message: "We couldn't find a client record matching those details.", directoryMismatch: true };
  }
  if (status === 409) {
    const slotFull = code === SLOT_FULL_CODE || !detail || /capacity/i.test(detail);
    if (slotFull) {
      return { ...outcome, message: 'That time was just taken by someone else - please pick another.', clearSlot: true };
    }
    // Not a full slot (e.g. the same Idempotency-Key still in flight): the slot stays picked.
    return { ...outcome, message: detail };
  }
  return { ...outcome, message: 'Something went wrong creating your booking. Please try again.', newIdempotencyKey: status === 422 };
}
