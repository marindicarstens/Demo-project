import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentHarness, HarnessLoader } from '@angular/cdk/testing';
import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatAutocompleteHarness } from '@angular/material/autocomplete/testing';
import { MatChipListboxHarness } from '@angular/material/chips/testing';
import { provideNativeDateAdapter } from '@angular/material/core';
import { TestRequest } from '@angular/common/http/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { environment } from '../../../environments/environment';
import { AppointmentDetails, AppointmentHold } from '../../core/models/booking.model';
import { Branch } from '../../core/models/branch.model';
import { ServiceType } from '../../core/models/service-type.model';
import { SimulatedInboxService } from '../../core/services/simulated-inbox.service';
import { BrowsePage } from './browse';

const BRANCHES: Branch[] = [
  { id: 'b1', name: 'Sandton City Branch', address: '', city: 'Johannesburg', opensAt: '08:30:00', closesAt: '16:30:00' },
  { id: 'b2', name: 'Gateway Branch', address: '', city: 'Durban', opensAt: '08:30:00', closesAt: '16:30:00' },
];
const LOAN: ServiceType = { id: 's1', name: 'Loan consultation', durationMinutes: 30, applicableClientType: 'EXISTING_CLIENT' };
const SLOT = { id: 'slot1', date: '2026-09-18', startTime: '09:30:00', remainingCapacity: 1 };
const OTHER_SLOT = { id: 'slot2', date: '2026-09-18', startTime: '10:00:00', remainingCapacity: 1 };
const IDENTITY = { email: 'thandiwe.test@example.com', idNumber: '9203015800082', accountNumber: '4051234567' };

/** The focusable part of a chip, which handles its keyboard selection. */
class ChipActionHarness extends ComponentHarness {
  static hostSelector = 'mat-chip-option .mat-mdc-chip-action';
}

function hold(holdExpiresAt = new Date().toISOString()): AppointmentHold {
  return {
    appointmentId: 'a1',
    referenceCode: 'BR-ABC123',
    status: 'PENDING_CONFIRMATION',
    holdExpiresAt,
    simulatedEmail: { subject: 's', from: 'f', bodyText: 'b', actionLink: null, sentAt: new Date().toISOString() },
  };
}

function lookedUp(status: AppointmentDetails['status']): AppointmentDetails {
  return {
    id: 'a1',
    referenceCode: 'BR-ABC123',
    status,
    branch: BRANCHES[0],
    serviceType: LOAN,
    date: SLOT.date,
    startTime: SLOT.startTime,
    createdAt: new Date().toISOString(),
  };
}

describe('BrowsePage (existing client)', () => {
  let fixture: ComponentFixture<BrowsePage>;
  let httpMock: HttpTestingController;
  let loader: HarnessLoader;
  let inboxOpen: ReturnType<typeof vi.fn<SimulatedInboxService['open']>>;

  function page(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  function button(label: string): HTMLButtonElement | undefined {
    return Array.from(page().querySelectorAll('button')).find((b) => b.textContent?.trim() === label);
  }

  function type(selector: string, value: string): void {
    const input = page().querySelector(selector) as HTMLInputElement;
    input.value = value;
    input.dispatchEvent(new Event('input'));
  }

  async function settle(): Promise<void> {
    await fixture.whenStable();
    fixture.detectChanges();
  }

  function expectServiceTypes(clientType: string, types: ServiceType[]): void {
    httpMock.expectOne((req) => req.url === `${environment.apiBaseUrl}/service-types` && req.params.get('clientType') === clientType).flush(types);
  }

  async function pickBranch(): Promise<void> {
    const autocomplete = await loader.getHarness(MatAutocompleteHarness);
    await autocomplete.selectOption({ text: /Sandton/ });
    fixture.detectChanges();
  }

  /** Branch, service and time, the way a customer picks them. The chip harness selects with the
   * keyboard (Space), the way a mouse-free customer would. */
  async function pickSlot(slots = [SLOT]): Promise<void> {
    await pickBranch();
    const [chip] = await (await serviceChips()).getChips({ text: new RegExp(LOAN.name) });
    await answeringAvailability(chip.select(), slots);
    await settle();
    button('09:30')?.click();
    fixture.detectChanges();
  }

  function serviceChips(): Promise<MatChipListboxHarness> {
    return loader.getHarness(MatChipListboxHarness.with({ selector: '[aria-label="Service type"]' }));
  }

  /** A harness action waits for the app to settle, which includes the times request it starts. */
  async function answeringAvailability(action: Promise<void>, slots = [SLOT]): Promise<void> {
    const request = await vi.waitFor(() => httpMock.expectOne((req) => req.url === `${environment.apiBaseUrl}/branches/b1/availability`));
    request.flush(slots);
    await action;
  }

  function expectBooking(): TestRequest {
    return httpMock.expectOne(`${environment.apiBaseUrl}/appointments`);
  }

  function submitIdentity(): void {
    type('input[formcontrolname="email"]', IDENTITY.email);
    type('input[formcontrolname="idNumber"]', IDENTITY.idNumber);
    type('input[formcontrolname="accountNumber"]', IDENTITY.accountNumber);
    button('Book this appointment')?.click();
    fixture.detectChanges();
  }

  async function bookHeld(holdExpiresAt?: string): Promise<void> {
    await pickSlot();
    submitIdentity();
    httpMock.expectOne(`${environment.apiBaseUrl}/appointments`).flush(hold(holdExpiresAt));
    fixture.detectChanges();
  }

  beforeEach(async () => {
    inboxOpen = vi.fn<SimulatedInboxService['open']>(() => true);
    await TestBed.configureTestingModule({
      imports: [BrowsePage],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideNativeDateAdapter(),
        provideRouter([]),
        { provide: SimulatedInboxService, useValue: { open: inboxOpen } },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ clientType: 'existing-client' }) } } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(BrowsePage);
    httpMock = TestBed.inject(HttpTestingController);
    loader = TestbedHarnessEnvironment.loader(fixture);
    fixture.detectChanges();

    // Branches and the client type's services load up front.
    httpMock.expectOne(`${environment.apiBaseUrl}/branches`).flush(BRANCHES);
    expectServiceTypes('EXISTING_CLIENT', [LOAN]);
    await settle();
  });

  afterEach(() => httpMock.verify());

  it('reads the client type from the route (existing-client -> Existing Client)', () => {
    expect(page().querySelector('.flow-badge')?.textContent?.trim()).toBe('Existing Client');
    expect(page().textContent).toContain('1. Search for a branch');
  });

  it('filters the branch list as the user types', async () => {
    const autocomplete = await loader.getHarness(MatAutocompleteHarness);
    await autocomplete.enterText('durban');

    const options = await autocomplete.getOptions();
    expect(await Promise.all(options.map((option) => option.getText()))).toEqual(['Gateway Branch — Durban']);
  });

  it('summarises the picked slot with its own date and marks it pressed', async () => {
    await pickSlot();

    expect(button('09:30')?.getAttribute('aria-pressed')).toBe('true');
    expect(page().querySelector('.selection-summary')?.textContent).toContain('Loan consultation at 09:30 on Friday, 18 September');
  });

  it('shows the generic "no match" error and a working New Account fallback on a directory-validation failure', async () => {
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    await pickSlot();
    submitIdentity();

    httpMock
      .expectOne(`${environment.apiBaseUrl}/appointments`)
      .flush(
        { type: 'about:blank', title: 'Unprocessable Content', status: 422, detail: 'No matching client record found for those details', suggestNewAccount: true },
        { status: 422, statusText: 'Unprocessable Content' },
      );
    fixture.detectChanges();

    // Never a field-level hint.
    const alert = page().querySelector('[role="alert"]')?.textContent ?? '';
    expect(alert).toContain("We couldn't find a client record matching those details.");
    expect(alert).not.toMatch(/ID number|account number|email/i);

    button('Continue as a New Account instead')?.click();
    fixture.detectChanges();
    // The URL follows, replacing the existing-client entry; the router reuses this component.
    expect(navigate).toHaveBeenCalledWith(['/browse', 'new-client'], { replaceUrl: true });
    expectServiceTypes('NEW_CLIENT', []);
    await settle();

    expect(page().querySelector('.flow-badge')?.textContent?.trim()).toBe('New Account');
    expect(page().querySelector('[role="alert"]')).toBeNull();
    type('input[formcontrolname="fullName"]', 'Mismatch Fallback');
    type('input[formcontrolname="email"]', 'fallback@example.com');
    type('input[formcontrolname="phone"]', '082 555 0177');
    button('Continue')?.click();
    await settle();

    // Branch carried over, not re-picked; the service must be re-picked from the New Account catalog.
    expect((page().querySelector('input[type="text"]') as HTMLInputElement).value).toBe('Sandton City Branch — Johannesburg');
    expect(page().textContent).toContain('3. Choose a service');
    expect(page().textContent).not.toContain('Pick a date and time');
  });

  it('reopenEmail checks first, and shows "already confirmed" instead of reopening a stale link', async () => {
    await bookHeld();
    inboxOpen.mockClear();

    button('Reopen the email')?.click();
    // Confirmed from the other tab in the meantime - see the comment on reopenEmail().
    const lookup = httpMock.expectOne((req) => req.url === `${environment.apiBaseUrl}/appointments/lookup`);
    expect(lookup.request.params.get('email')).toBe(IDENTITY.email);
    lookup.flush(lookedUp('CONFIRMED'));
    fixture.detectChanges();

    expect(page().textContent).toContain('already confirmed from the other tab');
    expect(button('Reopen the email')).toBeUndefined();
    expect(inboxOpen).not.toHaveBeenCalled();
  });

  it('shows the confirm-by time while held, and "hold expired" once reopenEmail finds it EXPIRED', async () => {
    await bookHeld(new Date(2026, 8, 18, 9, 31).toISOString());
    expect(page().textContent).toContain('Confirm by 09:31');

    button('Reopen the email')?.click();
    httpMock.expectOne((req) => req.url === `${environment.apiBaseUrl}/appointments/lookup`).flush(lookedUp('EXPIRED'));
    fixture.detectChanges();
    // Back on the slot grid, which loads the times again.
    httpMock.expectOne((req) => req.url === `${environment.apiBaseUrl}/branches/b1/availability`).flush([SLOT]);
    await settle();

    expect(page().querySelector('[role="alert"]')?.textContent).toContain('Your hold expired, so please pick a time again.');
    expect(page().querySelector('.hold-result')).toBeNull();
    expect(page().querySelector('.selection-summary')).toBeNull();
  });

  it('selects a service with the keyboard (Space) and loads its times', async () => {
    await pickBranch();
    const [chip] = await (await serviceChips()).getChips({ text: new RegExp(LOAN.name) });

    // LOAN is the only chip. No click event is involved, unlike a harness select().
    const action = await (await loader.getHarness(ChipActionHarness)).host();
    await answeringAvailability(action.sendKeys(' '));
    await settle();

    expect(await chip.isSelected()).toBe(true);
    expect(page().textContent).toContain('2. Choose a service');
    expect(page().textContent).toContain('3. Pick a date and time');
    expect(button('09:30')).toBeDefined();
  });

  it('keeps the service picked when its chip is toggled again', async () => {
    await pickSlot();
    const [chip] = await (await serviceChips()).getChips({ text: new RegExp(LOAN.name) });

    await chip.toggle();
    fixture.detectChanges();

    expect(await chip.isSelected()).toBe(true);
    expect(page().querySelector('.selection-summary')?.textContent).toContain('Loan consultation at 09:30');
  });

  it('shows an alert and a working "Try again" when the services fail to load', async () => {
    // A second page, whose own services request fails; the branch list is already cached.
    fixture = TestBed.createComponent(BrowsePage);
    loader = TestbedHarnessEnvironment.loader(fixture);
    fixture.detectChanges();
    httpMock.expectOne((req) => req.url === `${environment.apiBaseUrl}/service-types`).flush(null, { status: 500, statusText: 'Server Error' });
    await settle();
    await pickBranch();

    expect(page().querySelector('[role="alert"]')?.textContent).toContain("Couldn't load the services.");
    button('Try again')?.click();
    fixture.detectChanges();
    expectServiceTypes('EXISTING_CLIENT', [LOAN]);
    await settle();

    expect(page().querySelector('[role="alert"]')).toBeNull();
    expect(page().querySelector('mat-chip-option')?.textContent).toContain(LOAN.name);
  });

  describe('Idempotency-Key', () => {
    it('a retry after a 500 sends the same key', async () => {
      await pickSlot();
      submitIdentity();
      const first = expectBooking();
      const key = first.request.headers.get('Idempotency-Key');
      first.flush(null, { status: 500, statusText: 'Server Error' });
      fixture.detectChanges();
      expect(page().querySelector('[role="alert"]')?.textContent).toContain('Something went wrong creating your booking');

      button('Book this appointment')?.click();
      const retry = expectBooking();

      expect(key).toBeTruthy();
      expect(retry.request.headers.get('Idempotency-Key')).toBe(key);
      retry.flush(hold());
    });

    it('a retry after a network error (status 0) sends the same key', async () => {
      await pickSlot();
      submitIdentity();
      const first = expectBooking();
      const key = first.request.headers.get('Idempotency-Key');
      first.error(new ProgressEvent('error'));
      fixture.detectChanges();

      button('Book this appointment')?.click();
      const retry = expectBooking();

      expect(retry.request.headers.get('Idempotency-Key')).toBe(key);
      retry.flush(hold());
    });

    it('picking the same slot again keeps the key; a different slot gets a new one and clears the error', async () => {
      await pickSlot([SLOT, OTHER_SLOT]);
      submitIdentity();
      const first = expectBooking();
      const key = first.request.headers.get('Idempotency-Key');
      first.flush(null, { status: 500, statusText: 'Server Error' });
      fixture.detectChanges();

      button('09:30')?.click();
      fixture.detectChanges();
      button('Book this appointment')?.click();
      const sameSlot = expectBooking();
      expect(sameSlot.request.headers.get('Idempotency-Key')).toBe(key);
      sameSlot.flush(null, { status: 500, statusText: 'Server Error' });
      fixture.detectChanges();

      button('10:00')?.click();
      fixture.detectChanges();
      expect(page().querySelector('[role="alert"]')).toBeNull();
      button('Book this appointment')?.click();
      const otherSlot = expectBooking();

      expect(otherSlot.request.body.slotId).toBe(OTHER_SLOT.id);
      expect(otherSlot.request.headers.get('Idempotency-Key')).not.toBe(key);
      otherSlot.flush(hold());
    });

    it('a 422 without the new-account hint gets a new key on the next attempt', async () => {
      await pickSlot();
      submitIdentity();
      const first = expectBooking();
      const key = first.request.headers.get('Idempotency-Key');
      first.flush({ status: 422, detail: 'This Idempotency-Key was already used with a different request' }, { status: 422, statusText: 'Unprocessable Content' });
      fixture.detectChanges();

      button('Book this appointment')?.click();
      const retry = expectBooking();

      expect(retry.request.headers.get('Idempotency-Key')).not.toBe(key);
      retry.flush(hold());
    });

    it('a double click sends one POST', async () => {
      await pickSlot();
      type('input[formcontrolname="email"]', IDENTITY.email);
      type('input[formcontrolname="idNumber"]', IDENTITY.idNumber);
      type('input[formcontrolname="accountNumber"]', IDENTITY.accountNumber);

      const book = button('Book this appointment');
      book?.click();
      book?.click();

      expectBooking().flush(hold());
    });
  });

  it('a 409 that is not about capacity shows the server detail and keeps the slot', async () => {
    await pickSlot();
    submitIdentity();
    expectBooking().flush(
      { status: 409, detail: 'A request with this Idempotency-Key is already being processed - please retry shortly' },
      { status: 409, statusText: 'Conflict' },
    );
    fixture.detectChanges();

    expect(page().querySelector('[role="alert"]')?.textContent).toContain('already being processed');
    expect(page().textContent).not.toContain('That time was just taken');
    expect(page().querySelector('.selection-summary')).not.toBeNull();
  });

  it('a 409 about capacity says the time was taken and clears the slot', async () => {
    await pickSlot();
    submitIdentity();
    expectBooking().flush({ status: 409, detail: 'Requested slot has no remaining capacity' }, { status: 409, statusText: 'Conflict' });
    fixture.detectChanges();

    expect(page().querySelector('[role="alert"]')?.textContent).toContain('That time was just taken by someone else');
    expect(page().querySelector('.selection-summary')).toBeNull();
  });
});
