import { bookingErrorFor, SLOT_FULL_CODE } from './booking-error';

const TAKEN = 'That time was just taken by someone else - please pick another.';
const GENERIC = 'Something went wrong creating your booking. Please try again.';

describe('bookingErrorFor', () => {
  it('offers the New Account fallback on a directory mismatch, without saying which field failed', () => {
    expect(bookingErrorFor({ status: 422, detail: 'No matching client record found for those details', suggestNewAccount: true })).toEqual({
      message: "We couldn't find a client record matching those details.",
      directoryMismatch: true,
      clearSlot: false,
      newIdempotencyKey: false,
    });
  });

  it('treats a 409 with the SLOT_FULL code as a taken slot, whatever the detail says', () => {
    expect(bookingErrorFor({ status: 409, detail: 'Reworded server text', code: SLOT_FULL_CODE })).toEqual({
      message: TAKEN,
      directoryMismatch: false,
      clearSlot: true,
      newIdempotencyKey: false,
    });
  });

  it('falls back to the detail text for a 409 without a code', () => {
    expect(bookingErrorFor({ status: 409, detail: 'Requested slot has no remaining capacity' })).toMatchObject({ message: TAKEN, clearSlot: true });
  });

  it('treats a 409 without any detail as a taken slot', () => {
    expect(bookingErrorFor({ status: 409 })).toMatchObject({ message: TAKEN, clearSlot: true });
  });

  it('shows the server detail for any other 409 and keeps the slot and key', () => {
    const detail = 'A request with this Idempotency-Key is already being processed - please retry shortly';
    expect(bookingErrorFor({ status: 409, detail })).toEqual({ message: detail, directoryMismatch: false, clearSlot: false, newIdempotencyKey: false });
  });

  it('starts a new request after a 422 without the New Account hint', () => {
    expect(bookingErrorFor({ status: 422, detail: 'This Idempotency-Key was already used with a different request' })).toEqual({
      message: GENERIC,
      directoryMismatch: false,
      clearSlot: false,
      newIdempotencyKey: true,
    });
  });

  it('keeps the key for a retry after a server or network error', () => {
    expect(bookingErrorFor({ status: 500 })).toEqual({ message: GENERIC, directoryMismatch: false, clearSlot: false, newIdempotencyKey: false });
    expect(bookingErrorFor({ status: 0 })).toMatchObject({ newIdempotencyKey: false });
  });
});
