import { APIRequestContext } from '@playwright/test';

export const API_BASE_URL = process.env.E2E_API_BASE_URL || 'http://localhost:4200/api/v1';

// Seeded in the backend's V4 migration - see docs/SEED-DATA.md § Existing-client demo directory.
export const SEEDED_EXISTING_CLIENT = {
  email: 'thandiwe.demo@example.com',
  idNumber: '9203015800082',
  accountNumber: '4051234567',
  name: 'Thandiwe Nkosi',
};

export interface AvailableSlot {
  branchId: string;
  branchName: string;
  serviceTypeId: string;
  serviceTypeName: string;
  slotId: string;
  date: string;
  startTime: string;
  remainingCapacity: number;
}

/** Each spec file books at its own branch, so parallel workers never compete for a slot. Tests
 * inside one file run one after another, and each looks for a free slot after the previous one
 * booked, so they cannot collide with each other either. Indexes the branch list, which the API
 * returns sorted by name. */
export const E2E_BRANCH = {
  goldenPath: 0,
  bookingManagement: 1,
  edgeCases: 2,
  holdExpiry: 3,
  accessibility: 4,
} as const;

// The backend dates slots in Africa/Johannesburg, so "today" must be that zone's calendar date,
// whatever the machine running the tests is set to. en-CA formats as yyyy-MM-dd.
const BRANCH_DATE_FORMAT = new Intl.DateTimeFormat('en-CA', { timeZone: 'Africa/Johannesburg', year: 'numeric', month: '2-digit', day: '2-digit' });

/** The branch-local date `offset` days from today, as yyyy-MM-dd. The day arithmetic runs in UTC
 * on that date, so neither the machine's zone nor DST can shift it. */
function branchDatePlusDays(offset: number): string {
  const date = new Date(`${BRANCH_DATE_FORMAT.format(new Date())}T00:00:00Z`);
  date.setUTCDate(date.getUTCDate() + offset);
  return date.toISOString().slice(0, 10);
}

/** A real, currently-available slot from the live stack - the given branch, the first service
 * type for the client type, and the first slot with remaining capacity, searching forward from
 * `daysAhead` until one is found. Mirrors the pattern the backend's own integration tests use (see
 * AppointmentLifecycleIntegrationTest). */
export async function findAvailableSlot(
  request: APIRequestContext,
  clientType: 'NEW_CLIENT' | 'EXISTING_CLIENT',
  branchIndex: number,
  daysAhead = 1,
): Promise<AvailableSlot> {
  const branches = await (await request.get(`${API_BASE_URL}/branches`)).json();
  const serviceTypes = await (await request.get(`${API_BASE_URL}/service-types?clientType=${clientType}`)).json();
  const branch = branches[branchIndex];
  if (!branch) {
    throw new Error(`No branch at index ${branchIndex} - is the backend seeded?`);
  }
  const serviceType = serviceTypes.find((s: { applicableClientType: string }) => s.applicableClientType === clientType) ?? serviceTypes[0];

  for (let offset = daysAhead; offset < daysAhead + 13; offset++) {
    const isoDate = branchDatePlusDays(offset);
    const slots = await (
      await request.get(`${API_BASE_URL}/branches/${branch.id}/availability?date=${isoDate}&serviceTypeId=${serviceType.id}`)
    ).json();
    if (slots.length > 0) {
      return {
        branchId: branch.id,
        branchName: branch.name,
        serviceTypeId: serviceType.id,
        serviceTypeName: serviceType.name,
        slotId: slots[0].id,
        date: isoDate,
        startTime: slots[0].startTime,
        remainingCapacity: slots[0].remainingCapacity,
      };
    }
  }
  throw new Error(`No available slot at ${branch.name} in the next 13 days - is the backend seeded and running?`);
}

/** An appointment a test created, recorded so the fixture in fixtures.ts can cancel it afterwards. */
export interface CreatedBooking {
  appointmentId: string;
  referenceCode: string;
  email: string;
}

/** Everything the current test booked, through the API helpers below or through the browser.
 * Each worker is its own process and runs its tests one at a time, so one list per worker is
 * enough. */
export const createdBookings: CreatedBooking[] = [];

/** Cancels every recorded booking, so test runs don't fill the calendar. Only a CONFIRMED
 * appointment can be cancelled: a hold that was never confirmed gets a 422 here, and the backend's
 * expiry sweep releases it within the confirmation TTL instead. Already-cancelled ones are a
 * no-op. */
export async function cancelCreatedBookings(request: APIRequestContext): Promise<void> {
  for (const booking of createdBookings.splice(0)) {
    await request.delete(`${API_BASE_URL}/appointments/${booking.appointmentId}`, {
      params: { reference: booking.referenceCode, email: booking.email },
    });
  }
}

export interface ConfirmedAppointment {
  appointmentId: string;
  referenceCode: string;
  accessToken: string;
  email: string;
}

export interface HeldAppointment {
  appointmentId: string;
  referenceCode: string;
  /** The app-relative confirm link from the simulated email, e.g. /confirm/<token>. */
  confirmPath: string;
  email: string;
}

/** Fills a slot's remaining capacity with direct API holds, to stage "someone else took it"
 * without real concurrent traffic. The holds are never confirmed: a PENDING_CONFIRMATION hold
 * already occupies the capacity the UI's booking attempt competes for. */
export async function exhaustSlotCapacity(request: APIRequestContext, slot: AvailableSlot): Promise<void> {
  for (let i = 0; i < slot.remainingCapacity; i++) {
    await request.post(`${API_BASE_URL}/appointments`, {
      data: {
        clientType: 'NEW_CLIENT',
        branchId: slot.branchId,
        serviceTypeId: slot.serviceTypeId,
        slotId: slot.slotId,
        fullName: 'Capacity Filler',
        email: `e2e-filler-${Date.now()}-${i}@example.com`,
        phone: '+27825550000',
      },
    });
  }
}

/** Creates an unconfirmed New Account hold directly via the API, for tests that start at the
 * emailed confirm link. */
export async function holdViaApi(request: APIRequestContext, slot: AvailableSlot): Promise<HeldAppointment> {
  const email = `e2e-${Date.now()}-${Math.random().toString(36).slice(2, 8)}@example.com`;
  const holdResponse = await request.post(`${API_BASE_URL}/appointments`, {
    data: {
      clientType: 'NEW_CLIENT',
      branchId: slot.branchId,
      serviceTypeId: slot.serviceTypeId,
      slotId: slot.slotId,
      fullName: 'E2E Setup',
      email,
      phone: '+27825550000',
    },
  });
  const hold = await holdResponse.json();
  createdBookings.push({ appointmentId: hold.appointmentId, referenceCode: hold.referenceCode, email });
  return {
    appointmentId: hold.appointmentId,
    referenceCode: hold.referenceCode,
    confirmPath: new URL(hold.simulatedEmail.actionLink).pathname,
    email,
  };
}

/** Books and confirms a New Account appointment directly via the API - used to set up
 * preconditions for reschedule/cancel tests without re-driving the whole booking UI each time
 * (that flow is covered end to end by golden-path.spec.ts itself). */
export async function bookAndConfirmViaApi(request: APIRequestContext, slot: AvailableSlot): Promise<ConfirmedAppointment> {
  const hold = await holdViaApi(request, slot);
  const confirmToken = hold.confirmPath.split('/').pop();
  const confirmResponse = await request.post(`${API_BASE_URL}/confirmations/${confirmToken}`);
  const confirmed = await confirmResponse.json();
  return {
    appointmentId: confirmed.appointmentId,
    referenceCode: confirmed.referenceCode,
    accessToken: confirmed.accessToken,
    email: hold.email,
  };
}
