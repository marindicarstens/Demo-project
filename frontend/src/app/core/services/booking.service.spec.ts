import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../../environments/environment';
import { BookingService } from './booking.service';

// authFor() is private, so these go through cancel(), one of the public methods that uses it.
describe('BookingService credentials', () => {
  let service: BookingService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(BookingService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('sends an access token as a Bearer header and never as a query parameter', () => {
    service.cancel('a1', { accessToken: 'scoped-token' }).subscribe();

    const request = httpMock.expectOne((req) => req.url === `${environment.apiBaseUrl}/appointments/a1`);
    expect(request.request.method).toBe('DELETE');
    expect(request.request.headers.get('Authorization')).toBe('Bearer scoped-token');
    expect(request.request.params.keys()).toEqual([]);
    request.flush(null, { status: 204, statusText: 'No Content' });
  });

  it('sends reference and email as query parameters and no Authorization header', () => {
    service.cancel('a1', { reference: 'BR-ABC123', email: 'lindiwe@example.com' }).subscribe();

    const request = httpMock.expectOne((req) => req.url === `${environment.apiBaseUrl}/appointments/a1`);
    expect(request.request.params.get('reference')).toBe('BR-ABC123');
    expect(request.request.params.get('email')).toBe('lindiwe@example.com');
    expect(request.request.headers.has('Authorization')).toBe(false);
    request.flush(null, { status: 204, statusText: 'No Content' });
  });
});
