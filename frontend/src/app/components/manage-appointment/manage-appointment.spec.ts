import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNativeDateAdapter } from '@angular/material/core';
import { provideRouter } from '@angular/router';
import { environment } from '../../../environments/environment';
import { AppointmentCredential, AppointmentDetails, RescheduleRequestResult } from '../../core/models/booking.model';
import { SimulatedInboxService } from '../../core/services/simulated-inbox.service';
import { ManageAppointment } from './manage-appointment';

const APPOINTMENT: AppointmentDetails = {
  id: 'a1',
  referenceCode: 'BR-ABC123',
  status: 'CONFIRMED',
  branch: { id: 'b1', name: 'Sandton City Branch', address: '', city: 'Johannesburg', opensAt: '08:30:00', closesAt: '16:30:00' },
  serviceType: { id: 's1', name: 'General enquiry', durationMinutes: 15, applicableClientType: 'BOTH' },
  date: '2026-09-18',
  startTime: '09:30:00',
  createdAt: '2026-09-10T08:00:00Z',
};

const SLOT = { id: 'slot2', date: '2026-09-19', startTime: '10:00:00', remainingCapacity: 1 };

const RESCHEDULE_RESULT: RescheduleRequestResult = {
  appointmentId: 'a1',
  referenceCode: 'BR-ABC123',
  status: 'CONFIRMED',
  // Still live when the tests run; see the expiry tests for a lapsed one.
  rescheduleRequestExpiresAt: new Date(Date.now() + 60 * 60 * 1000).toISOString(),
  simulatedEmail: { subject: 's', from: 'f', bodyText: 'b', actionLink: null, sentAt: '2026-09-18T07:30:00Z' },
};

const REF_EMAIL: AppointmentCredential = { reference: 'BR-ABC123', email: 'lindiwe@example.com' };

describe('ManageAppointment', () => {
  let component: ManageAppointment;
  let fixture: ComponentFixture<ManageAppointment>;
  let httpMock: HttpTestingController;
  let inboxOpen: ReturnType<typeof vi.fn<SimulatedInboxService['open']>>;

  function render(appointment: AppointmentDetails = APPOINTMENT, credential: AppointmentCredential = REF_EMAIL): void {
    fixture.componentRef.setInput('appointment', appointment);
    fixture.componentRef.setInput('credential', credential);
    fixture.detectChanges();
  }

  function text(): string {
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
  }

  function button(label: string): HTMLButtonElement | undefined {
    return Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('button')).find((b) => b.textContent?.trim() === label);
  }

  function failCancel(status: number, body: object | null = null): void {
    component.cancelAppointment();
    httpMock
      .expectOne((req) => req.method === 'DELETE' && req.url === `${environment.apiBaseUrl}/appointments/a1`)
      .flush(body, { status, statusText: 'Error' });
    fixture.detectChanges();
  }

  function requestRescheduleOk(): void {
    component.selectedSlot.set(SLOT);
    component.requestReschedule();
    httpMock.expectOne((req) => req.url === `${environment.apiBaseUrl}/appointments/a1/reschedule-request`).flush(RESCHEDULE_RESULT);
    fixture.detectChanges();
  }

  beforeEach(async () => {
    inboxOpen = vi.fn<SimulatedInboxService['open']>(() => true);
    await TestBed.configureTestingModule({
      imports: [ManageAppointment],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideNativeDateAdapter(),
        provideRouter([]),
        { provide: SimulatedInboxService, useValue: { open: inboxOpen } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ManageAppointment);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('shows the status as a label, never the raw enum name', () => {
    render({ ...APPOINTMENT, status: 'PENDING_CONFIRMATION' });

    const badge = (fixture.nativeElement as HTMLElement).querySelector('[role="status"]');
    expect(badge?.textContent?.trim()).toBe('Awaiting confirmation');
    // Named "Status: <label>" by a visually hidden prefix, not a fixed aria-label.
    expect(badge?.hasAttribute('aria-label')).toBe(false);
    const names = (badge?.getAttribute('aria-labelledby') ?? '').split(' ').map((id) => document.getElementById(id)?.textContent?.trim());
    expect(names).toEqual(['Status:', 'Awaiting confirmation']);
    expect(text()).not.toContain('PENDING_CONFIRMATION');
  });

  it('offers no actions for an appointment that is no longer confirmed', () => {
    render({ ...APPOINTMENT, status: 'CANCELLED' });

    expect(button('Reschedule')).toBeUndefined();
    expect(button('Cancel appointment')).toBeUndefined();
  });

  describe('error mapping', () => {
    it('409 shows the server detail when there is one, as an alert', () => {
      render();
      failCancel(409, { status: 409, detail: 'A reschedule is already pending confirmation for this appointment' });
      const alert = (fixture.nativeElement as HTMLElement).querySelector('[role="alert"]');
      expect(alert?.textContent).toContain('A reschedule is already pending confirmation for this appointment');
      expect(text()).not.toContain('That time was just taken');
    });

    it('404 with an access token says the session expired and links to /manage', () => {
      render(APPOINTMENT, { accessToken: 'expired-token' });
      failCancel(404);
      expect(text()).toContain('Your session has expired. Look up your appointment again.');
      expect((fixture.nativeElement as HTMLElement).querySelector('a[href="/manage"]')).not.toBeNull();
    });

    it('409 without a server detail falls back to "time taken"', () => {
      render();
      failCancel(409);
      expect(text()).toContain('That time was just taken by someone else');
    });

    it('422 explains the change is not allowed', () => {
      render();
      failCancel(422, { status: 422, detail: 'Too close' });
      expect(text()).toContain('That change is not allowed');
    });

    it('404 with a reference and email says the appointment was not found', () => {
      render();
      failCancel(404);
      expect(text()).toContain("We couldn't find that appointment.");
    });

    it('anything else shows a generic retry message', () => {
      render();
      failCancel(500);
      expect(text()).toContain('Something went wrong. Please try again.');
    });
  });

  // Both clicks land before change detection can disable the button.
  it('ignores a second "Yes, cancel" click while the first is in flight', () => {
    render();
    button('Cancel appointment')?.click();
    fixture.detectChanges();

    const yes = button('Yes, cancel');
    yes?.click();
    yes?.click();

    httpMock.expectOne((req) => req.method === 'DELETE').flush(null, { status: 204, statusText: 'No Content' });
  });

  it('ignores a second "Confirm new time" click while the first is in flight', async () => {
    render();
    button('Reschedule')?.click();
    fixture.detectChanges();
    httpMock.expectOne((req) => req.url === `${environment.apiBaseUrl}/branches/b1/availability`).flush([SLOT]);
    await fixture.whenStable();
    fixture.detectChanges();
    button('10:00')?.click();
    fixture.detectChanges();

    const confirm = button('Confirm new time');
    confirm?.click();
    confirm?.click();

    httpMock.expectOne((req) => req.url === `${environment.apiBaseUrl}/appointments/a1/reschedule-request`).flush(RESCHEDULE_RESULT);
  });

  it('Reschedule loads times for the same branch and service, and a picked time enables "Confirm new time"', async () => {
    render();
    button('Reschedule')?.click();
    fixture.detectChanges();

    const request = httpMock.expectOne((req) => req.url === `${environment.apiBaseUrl}/branches/b1/availability`);
    expect(request.request.params.get('serviceTypeId')).toBe('s1');
    request.flush([SLOT]);
    await fixture.whenStable();
    fixture.detectChanges();
    expect(button('Confirm new time')?.disabled).toBe(true);

    button('10:00')?.click();
    fixture.detectChanges();

    expect(button('10:00')?.getAttribute('aria-pressed')).toBe('true');
    expect(button('Confirm new time')?.disabled).toBe(false);
  });

  it('moves to the requested state and opens the confirm-reschedule email', () => {
    render();
    requestRescheduleOk();

    expect(component.mode()).toBe('requested');
    expect(inboxOpen).toHaveBeenCalledWith(RESCHEDULE_RESULT.simulatedEmail, 'Confirm this reschedule');
    expect(text()).toContain('Almost there');
    expect(text()).not.toContain('Your browser blocked the new tab');
  });

  it('tells the customer when the browser blocked the email tab', () => {
    inboxOpen.mockReturnValue(false);
    render();
    requestRescheduleOk();

    expect(text()).toContain('Your browser blocked the new tab');
  });

  it('Back from the requested state returns to the summary actions', () => {
    render();
    requestRescheduleOk();

    button('Back')?.click();
    fixture.detectChanges();

    expect(component.mode()).toBe('idle');
    expect(button('Reschedule')).toBeDefined();
  });

  it('after Back, still shows the pending reschedule and can reopen its email', () => {
    render();
    requestRescheduleOk();
    button('Back')?.click();
    fixture.detectChanges();

    expect(component.pendingReschedule()).toEqual(RESCHEDULE_RESULT);
    expect(text()).toContain('A reschedule is waiting for confirmation');
    inboxOpen.mockClear();
    button('Reopen the email')?.click();
    expect(inboxOpen).toHaveBeenCalledWith(RESCHEDULE_RESULT.simulatedEmail, 'Confirm this reschedule');
  });

  it('Back clears an error from the step it leaves', () => {
    render();
    button('Cancel appointment')?.click();
    fixture.detectChanges();
    failCancel(500);
    expect(text()).toContain('Something went wrong');

    button('Keep appointment')?.click();
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).querySelector('[role="alert"]')).toBeNull();
  });

  it('on an expired session, offers only the lookup link: no actions and no pending banner', () => {
    render(APPOINTMENT, { accessToken: 'scoped-token' });
    requestRescheduleOk();
    button('Back')?.click();
    fixture.detectChanges();
    button('Cancel appointment')?.click();
    fixture.detectChanges();

    failCancel(404);

    expect(text()).toContain('Your session has expired');
    expect((fixture.nativeElement as HTMLElement).querySelector('a[href="/manage"]')).not.toBeNull();
    expect(text()).not.toContain('A reschedule is waiting for confirmation');
    expect(button('Yes, cancel')).toBeUndefined();
    expect(button('Keep appointment')).toBeUndefined();
    expect(button('Reschedule')).toBeUndefined();
  });

  describe('cancel confirmation', () => {
    it('asks before cancelling, and "Keep appointment" cancels nothing', () => {
      render();

      button('Cancel appointment')?.click();
      fixture.detectChanges();
      expect(text()).toContain("Are you sure? This can't be undone.");
      httpMock.expectNone((req) => req.method === 'DELETE');

      button('Keep appointment')?.click();
      fixture.detectChanges();
      expect(button('Reschedule')).toBeDefined();
      httpMock.expectNone((req) => req.method === 'DELETE');
    });

    it('"Yes, cancel" cancels and reports it', () => {
      render();
      let cancelled = false;
      component.cancelled.subscribe(() => (cancelled = true));

      button('Cancel appointment')?.click();
      fixture.detectChanges();
      button('Yes, cancel')?.click();

      httpMock.expectOne((req) => req.method === 'DELETE').flush(null, { status: 204, statusText: 'No Content' });
      expect(cancelled).toBe(true);
    });
  });

  describe('re-fetch when the tab regains focus', () => {
    afterEach(() => vi.restoreAllMocks());

    function expectGet() {
      return httpMock.expectOne((req) => req.method === 'GET' && req.url === `${environment.apiBaseUrl}/appointments/a1`);
    }

    it('re-reads on window focus with the same credential and emits a change', () => {
      render(APPOINTMENT, { accessToken: 'scoped-token' });
      const changes: AppointmentDetails[] = [];
      component.changed.subscribe((latest) => changes.push(latest));

      window.dispatchEvent(new Event('focus'));
      const request = expectGet();
      expect(request.request.headers.get('Authorization')).toBe('Bearer scoped-token');
      request.flush({ ...APPOINTMENT, status: 'CANCELLED' });

      expect(changes).toEqual([{ ...APPOINTMENT, status: 'CANCELLED' }]);
    });

    it('does not emit when nothing changed', () => {
      render();
      const changes: AppointmentDetails[] = [];
      component.changed.subscribe((latest) => changes.push(latest));

      window.dispatchEvent(new Event('focus'));
      expectGet().flush(APPOINTMENT);

      expect(changes).toEqual([]);
    });

    it('re-reads when the document becomes visible, not when it is hidden', () => {
      render();
      const visibility = vi.spyOn(document, 'visibilityState', 'get');

      visibility.mockReturnValue('hidden');
      document.dispatchEvent(new Event('visibilitychange'));
      httpMock.expectNone((req) => req.method === 'GET');

      visibility.mockReturnValue('visible');
      document.dispatchEvent(new Event('visibilitychange'));
      expectGet().flush(APPOINTMENT);
    });

    it('does not send a second GET when focus and visibilitychange fire back to back', () => {
      render();
      const visibility = vi.spyOn(document, 'visibilityState', 'get');
      visibility.mockReturnValue('visible');

      window.dispatchEvent(new Event('focus'));
      document.dispatchEvent(new Event('visibilitychange'));

      expectGet().flush(APPOINTMENT);
    });

    it('a reschedule confirmed in another tab clears the pending request and returns to the summary', () => {
      render();
      requestRescheduleOk();

      window.dispatchEvent(new Event('focus'));
      expectGet().flush({ ...APPOINTMENT, date: SLOT.date, startTime: SLOT.startTime });
      fixture.detectChanges();

      expect(text()).not.toContain('Almost there');
      expect(text()).not.toContain('A reschedule is waiting for confirmation');
      expect(button('Reopen the email')).toBeUndefined();
      expect(button('Reschedule')).toBeDefined();
    });

    it('after Back, a move seen on focus removes the pending banner', () => {
      render();
      requestRescheduleOk();
      button('Back')?.click();
      fixture.detectChanges();
      expect(text()).toContain('A reschedule is waiting for confirmation');

      window.dispatchEvent(new Event('focus'));
      expectGet().flush({ ...APPOINTMENT, date: SLOT.date, startTime: SLOT.startTime });
      fixture.detectChanges();

      expect(text()).not.toContain('A reschedule is waiting for confirmation');
      expect(button('Reopen the email')).toBeUndefined();
      expect(button('Reschedule')).toBeDefined();
    });

    it('a status other than CONFIRMED drops the pending request in any mode', () => {
      render();
      requestRescheduleOk();
      button('Back')?.click();
      fixture.detectChanges();

      window.dispatchEvent(new Event('focus'));
      expectGet().flush({ ...APPOINTMENT, status: 'CANCELLED' });
      fixture.detectChanges();

      expect(component.pendingReschedule()).toBeNull();
      expect(text()).not.toContain('A reschedule is waiting for confirmation');
    });

    it('never shows the banner for a request that has already expired', () => {
      render();
      component.selectedSlot.set(SLOT);
      component.requestReschedule();
      httpMock
        .expectOne((req) => req.url === `${environment.apiBaseUrl}/appointments/a1/reschedule-request`)
        .flush({ ...RESCHEDULE_RESULT, rescheduleRequestExpiresAt: new Date(Date.now() - 1000).toISOString() });
      fixture.detectChanges();
      button('Back')?.click();
      fixture.detectChanges();

      expect(text()).not.toContain('A reschedule is waiting for confirmation');
    });

    it('an expired pending request is hidden, and dropped on the next refresh', () => {
      render();
      const now = Date.now();
      const clock = vi.spyOn(Date, 'now').mockReturnValue(now);
      requestRescheduleOk();
      button('Back')?.click();
      fixture.detectChanges();
      expect(text()).toContain('A reschedule is waiting for confirmation');

      clock.mockReturnValue(Date.parse(RESCHEDULE_RESULT.rescheduleRequestExpiresAt));
      window.dispatchEvent(new Event('focus'));
      expect(component.pendingReschedule()).toBeNull();
      expectGet().flush(APPOINTMENT);
      fixture.detectChanges();

      expect(text()).not.toContain('A reschedule is waiting for confirmation');
    });
  });
});
