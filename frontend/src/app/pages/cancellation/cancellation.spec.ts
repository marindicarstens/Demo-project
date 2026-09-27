import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { CancellationPage } from './cancellation';

describe('CancellationPage', () => {
  let component: CancellationPage;
  let fixture: ComponentFixture<CancellationPage>;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CancellationPage],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ token: 'raw-cancel-token' }) } } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(CancellationPage);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  // Unlike ConfirmPage, this preview IS safe to fire on load - it's read-only server-side, so
  // it's fetched eagerly, without waiting for a click.
  it('fetches the safe preview automatically on construction, but never the cancel action itself', () => {
    const request = httpMock.expectOne((req) => req.url.endsWith('/cancellations/raw-cancel-token') && req.method === 'GET');
    request.flush({
      referenceCode: 'BR-ABC123',
      branchName: 'Sandton City Branch',
      serviceTypeName: 'General enquiry',
      date: '2026-09-18',
      startTime: '09:30:00',
      alreadyCancelled: false,
    });

    expect(component.state()).toBe('preview');
    httpMock.expectNone((req) => req.method === 'POST');
  });

  it('only cancels after an explicit button click', () => {
    const previewRequest = httpMock.expectOne((req) => req.method === 'GET');
    previewRequest.flush({
      referenceCode: 'BR-ABC123',
      branchName: 'Sandton City Branch',
      serviceTypeName: 'General enquiry',
      date: '2026-09-18',
      startTime: '09:30:00',
      alreadyCancelled: false,
    });

    component.confirmCancellation();

    const cancelRequest = httpMock.expectOne((req) => req.url.endsWith('/cancellations/raw-cancel-token') && req.method === 'POST');
    cancelRequest.flush({ referenceCode: 'BR-ABC123', status: 'CANCELLED' });

    expect(component.state()).toBe('cancelled');
  });

  it('shows the date in words, not as a raw ISO date', () => {
    httpMock.expectOne((req) => req.method === 'GET').flush({
      referenceCode: 'BR-ABC123',
      branchName: 'Sandton City Branch',
      serviceTypeName: 'General enquiry',
      date: '2026-09-18',
      startTime: '09:30:00',
      alreadyCancelled: false,
    });
    fixture.detectChanges();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Friday, 18 September');
    expect(text).not.toContain('2026-09-18');
  });
});
