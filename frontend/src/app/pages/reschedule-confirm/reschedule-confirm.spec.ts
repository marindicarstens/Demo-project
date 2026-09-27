import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { RescheduleConfirmPage } from './reschedule-confirm';

describe('RescheduleConfirmPage', () => {
  let component: RescheduleConfirmPage;
  let fixture: ComponentFixture<RescheduleConfirmPage>;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [RescheduleConfirmPage],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ token: 'raw-reschedule-token' }) } } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(RescheduleConfirmPage);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  // Same anti-prefetch guarantee as ConfirmPage - see its spec's equivalent test. A regression
  // here (e.g. wiring the confirm call to ngOnInit) would let an email security scanner silently
  // apply a customer's reschedule just by prefetching the link.
  it('never calls the reschedule-confirmations endpoint just from loading the page', () => {
    fixture.detectChanges();
    httpMock.expectNone(() => true);
  });

  it('only calls confirm after an explicit button click, and only once', () => {
    fixture.detectChanges();
    component.confirmReschedule();

    const request = httpMock.expectOne((req) => req.url.endsWith('/reschedule-confirmations/raw-reschedule-token'));
    expect(request.request.method).toBe('POST');
    request.flush({
      id: 'a1',
      referenceCode: 'BR-ABC123',
      status: 'CONFIRMED',
      branch: { id: 'b1', name: 'Sandton City Branch', address: '', city: 'Johannesburg', opensAt: '08:30:00', closesAt: '16:30:00' },
      serviceType: { id: 's1', name: 'General enquiry', durationMinutes: 15, applicableClientType: 'BOTH' },
      date: '2026-09-18',
      startTime: '09:30:00',
      createdAt: new Date().toISOString(),
    });

    expect(component.state()).toBe('confirmed');

    // The double-click guard - a second call while already 'confirmed' must not fire again.
    component.confirmReschedule();
    httpMock.expectNone(() => true);
  });

  it('shows an expired-link message on a 410 response', () => {
    fixture.detectChanges();
    component.confirmReschedule();

    const request = httpMock.expectOne((req) => req.url.endsWith('/reschedule-confirmations/raw-reschedule-token'));
    request.flush({ title: 'Gone' }, { status: 410, statusText: 'Gone' });

    expect(component.state()).toBe('error');
    expect(component.errorMessage()).toContain('expired');
  });

  // Lost the race to the expiry sweep: the request was released as this confirm arrived.
  it('on a 409 says the request expired, the appointment is unchanged, and links to /manage', () => {
    fixture.detectChanges();
    component.confirmReschedule();

    httpMock
      .expectOne((req) => req.url.endsWith('/reschedule-confirmations/raw-reschedule-token'))
      .flush({ title: 'Conflict', status: 409 }, { status: 409, statusText: 'Conflict' });
    fixture.detectChanges();

    const page = fixture.nativeElement as HTMLElement;
    expect(page.textContent).toContain(
      'This reschedule request expired just as you confirmed it. Your appointment is unchanged. Please request the new time again.',
    );
    expect(page.querySelector('a[href="/manage"]')).not.toBeNull();
  });
});
