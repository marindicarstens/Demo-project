import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { ConfirmationResult } from '../../core/models/booking.model';
import { SimulatedInboxService } from '../../core/services/simulated-inbox.service';
import { ConfirmPage } from './confirm';

describe('ConfirmPage', () => {
  let component: ConfirmPage;
  let fixture: ComponentFixture<ConfirmPage>;
  let httpMock: HttpTestingController;
  let inboxOpen: ReturnType<typeof vi.fn<SimulatedInboxService['open']>>;

  beforeEach(async () => {
    inboxOpen = vi.fn<SimulatedInboxService['open']>(() => true);
    await TestBed.configureTestingModule({
      imports: [ConfirmPage],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: SimulatedInboxService, useValue: { open: inboxOpen } },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ token: 'raw-test-token' }) } } },
      ],
    }).compileComponents();

    // Component construction itself fires the confirm POST (see ConfirmPage's class comment) -
    // by the time this line returns, a request is already outstanding on httpMock, before any
    // test body below has run.
    fixture = TestBed.createComponent(ConfirmPage);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('should create', () => {
    httpMock.expectOne((req) => req.url.endsWith('/confirmations/raw-test-token')).flush(confirmationResult());
    expect(component).toBeTruthy();
  });

  // The whole point of this page's new design - see its class comment. Confirming on
  // construction, with no separate in-app click, is deliberate here (unlike CancellationPage,
  // which still requires one); this test guards that it happens exactly once, not that it never
  // happens.
  it('confirms automatically on construction, exactly once', () => {
    const request = httpMock.expectOne((req) => req.url.endsWith('/confirmations/raw-test-token'));
    expect(request.request.method).toBe('POST');
    request.flush(confirmationResult());

    fixture.detectChanges();
    expect(component.state()).toBe('confirmed');
  });

  // A 410 is either a used token (e.g. a refresh after confirming) or an expired hold. The booking
  // may well be confirmed, so the page must not tell the customer to book again.
  it('on a 410 says the link expired or was used and links to the booking lookup', () => {
    httpMock.expectOne((req) => req.url.endsWith('/confirmations/raw-test-token')).flush(null, { status: 410, statusText: 'Gone' });

    fixture.detectChanges();
    expect(component.state()).toBe('error');
    const page = fixture.nativeElement as HTMLElement;
    expect(page.querySelector('h1')?.textContent?.trim()).toBe('This link has expired or was already used. Look up your booking to see its status.');
    expect(page.textContent).not.toContain('book again');
    const link = page.querySelector('a[href="/manage"]');
    expect(link?.textContent?.trim()).toBe('Look up your booking');
  });

  it('announces the confirming state in a status region that stays in the page', () => {
    fixture.detectChanges();
    const region = (fixture.nativeElement as HTMLElement).querySelector('p[role="status"]');
    expect(region?.textContent?.trim()).toBe('Confirming your appointment');
    expect((fixture.nativeElement as HTMLElement).querySelector('mat-spinner')?.getAttribute('aria-hidden')).toBe('true');

    httpMock.expectOne((req) => req.url.endsWith('/confirmations/raw-test-token')).flush(null, { status: 404, statusText: 'Not Found' });
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).querySelector('p[role="status"]')).toBe(region);
    expect(region?.textContent?.trim()).toBe('');
  });

  it('opens the receipt email, with its cancellation link, from the confirmed screen', () => {
    const result = confirmationResult();
    httpMock.expectOne((req) => req.url.endsWith('/confirmations/raw-test-token')).flush(result);
    fixture.detectChanges();

    const button = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('button')).find(
      (b) => b.textContent?.trim() === 'View receipt email',
    );
    button?.click();

    expect(inboxOpen).toHaveBeenCalledWith(result.receiptEmail, 'Cancel my appointment');
  });
});

describe('ConfirmPage after a change in another tab', () => {
  let fixture: ComponentFixture<ConfirmPage>;
  let httpMock: HttpTestingController;

  function page(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  /** The embedded ManageAppointment re-reads on focus, with the scoped token. */
  function refocusAndFlush(latest: object): void {
    window.dispatchEvent(new Event('focus'));
    const request = httpMock.expectOne((req) => req.method === 'GET' && req.url.endsWith('/appointments/a1'));
    expect(request.request.headers.get('Authorization')).toBe('Bearer token');
    request.flush(latest);
    fixture.detectChanges();
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ConfirmPage],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ token: 'raw-test-token' }) } } },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(ConfirmPage);
    httpMock = TestBed.inject(HttpTestingController);
    httpMock.expectOne((req) => req.url.endsWith('/confirmations/raw-test-token')).flush(confirmationResult());
    fixture.detectChanges();
  });

  afterEach(() => httpMock.verify());

  it('shows the re-fetched appointment', () => {
    const { branch, serviceType } = confirmationResult();
    refocusAndFlush({ id: 'a1', referenceCode: 'BR-ABC123', status: 'CONFIRMED', branch, serviceType, date: '2026-09-21', startTime: '11:00:00' });

    expect(page().textContent).toContain('General enquiry at 11:00 on Monday, 21 September');
    expect(page().textContent).not.toContain('18 September');
  });

  it('offers the receipt email only while the appointment is confirmed', () => {
    const { branch, serviceType } = confirmationResult();
    expect(page().textContent).toContain('View receipt email');

    refocusAndFlush({ id: 'a1', referenceCode: 'BR-ABC123', status: 'CANCELLED', branch, serviceType, date: '2026-09-18', startTime: '09:30:00' });

    expect(page().querySelector('[role="status"].status-badge')?.textContent?.trim()).toBe('Cancelled');
    expect(page().textContent).not.toContain('View receipt email');
  });
});

function confirmationResult(): ConfirmationResult {
  return {
    appointmentId: 'a1',
    referenceCode: 'BR-ABC123',
    status: 'CONFIRMED',
    accessToken: 'token',
    accessTokenExpiresAt: new Date().toISOString(),
    receiptEmail: { subject: 's', from: 'f', bodyText: 'b', actionLink: null, sentAt: new Date().toISOString() },
    branch: { id: 'b1', name: 'Sandton City Branch', address: '', city: 'Johannesburg', opensAt: '08:30:00', closesAt: '16:30:00' },
    serviceType: { id: 's1', name: 'General enquiry', durationMinutes: 15, applicableClientType: 'BOTH' },
    date: '2026-09-18',
    startTime: '09:30:00',
  };
}
