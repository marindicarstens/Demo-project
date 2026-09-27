import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { environment } from '../../../environments/environment';
import { LookupPage } from './lookup';

describe('LookupPage', () => {
  let component: LookupPage;
  let fixture: ComponentFixture<LookupPage>;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [LookupPage],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    fixture = TestBed.createComponent(LookupPage);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('never looks anything up just from loading the page', () => {
    fixture.detectChanges();
    httpMock.expectNone(() => true);
  });

  it('shows a matching appointment after a successful lookup', () => {
    component.lookupForm.setValue({ reference: 'BR-ABC123', email: 'lindiwe@example.com' });

    component.lookup();

    const request = httpMock.expectOne((req) => req.url === `${environment.apiBaseUrl}/appointments/lookup`);
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

    expect(component.appointment()?.referenceCode).toBe('BR-ABC123');
    expect(component.credential()).toEqual({ reference: 'BR-ABC123', email: 'lindiwe@example.com' });
  });

  it('sends one lookup for a double submit', () => {
    fixture.detectChanges();
    component.lookupForm.setValue({ reference: 'BR-ABC123', email: 'lindiwe@example.com' });
    const form = (fixture.nativeElement as HTMLElement).querySelector('form') as HTMLFormElement;

    form.dispatchEvent(new Event('submit'));
    form.dispatchEvent(new Event('submit'));

    httpMock.expectOne((req) => req.url === `${environment.apiBaseUrl}/appointments/lookup`).flush(null, { status: 404, statusText: 'Not Found' });
  });

  it('shows a generic error when nothing matches', () => {
    component.lookupForm.setValue({ reference: 'BR-NOPE99', email: 'nobody@example.com' });

    component.lookup();

    const request = httpMock.expectOne((req) => req.url === `${environment.apiBaseUrl}/appointments/lookup`);
    request.flush({ type: 'about:blank', title: 'Not Found', status: 404 }, { status: 404, statusText: 'Not Found' });

    expect(component.appointment()).toBeNull();
    expect(component.error()).toContain("couldn't find");
  });

  it('trims both fields and upper-cases the reference before looking up', () => {
    component.lookupForm.setValue({ reference: '  br-abc123 ', email: ' lindiwe@example.com  ' });

    component.lookup();

    const request = httpMock.expectOne((req) => req.url === `${environment.apiBaseUrl}/appointments/lookup`);
    expect(request.request.params.get('reference')).toBe('BR-ABC123');
    expect(request.request.params.get('email')).toBe('lindiwe@example.com');
    request.flush(null, { status: 404, statusText: 'Not Found' });
  });

  it('says to wait on a 429 and shows a generic error for anything else, as an alert', () => {
    component.lookupForm.setValue({ reference: 'BR-ABC123', email: 'lindiwe@example.com' });

    component.lookup();
    httpMock
      .expectOne((req) => req.url === `${environment.apiBaseUrl}/appointments/lookup`)
      .flush({ status: 429 }, { status: 429, statusText: 'Too Many Requests' });
    fixture.detectChanges();
    const alert = (fixture.nativeElement as HTMLElement).querySelector('[role="alert"]');
    expect(alert?.textContent).toContain('Too many attempts, try again in a few minutes');

    component.lookup();
    httpMock
      .expectOne((req) => req.url === `${environment.apiBaseUrl}/appointments/lookup`)
      .flush(null, { status: 500, statusText: 'Server Error' });
    expect(component.error()).toBe('Something went wrong. Please try again.');
  });
});
