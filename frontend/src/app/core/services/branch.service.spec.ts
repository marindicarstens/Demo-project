import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../../environments/environment';
import { Branch } from '../models/branch.model';
import { BranchService } from './branch.service';

const BRANCHES: Branch[] = [
  { id: '1', name: 'Sandton City Branch', address: '', city: 'Johannesburg', opensAt: '08:30:00', closesAt: '16:30:00' },
  { id: '2', name: 'Gateway Branch', address: '', city: 'Durban', opensAt: '08:30:00', closesAt: '16:30:00' },
];

describe('BranchService', () => {
  let service: BranchService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(BranchService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('fetches the list once and reuses it for later loads', () => {
    service.load();
    httpMock.expectOne(`${environment.apiBaseUrl}/branches`).flush(BRANCHES);

    service.load();
    httpMock.expectNone(`${environment.apiBaseUrl}/branches`);
    expect(service.branches()).toEqual(BRANCHES);
  });

  it('retries on the next load after a failure', () => {
    service.load();
    httpMock.expectOne(`${environment.apiBaseUrl}/branches`).flush(null, { status: 500, statusText: 'Server Error' });
    expect(service.branches()).toEqual([]);
    expect(service.loadFailed()).toBe(true);

    service.load();
    expect(service.loadFailed()).toBe(false);
    httpMock.expectOne(`${environment.apiBaseUrl}/branches`).flush(BRANCHES);
    expect(service.branches()).toEqual(BRANCHES);
    expect(service.loadFailed()).toBe(false);
  });

  it('matches the query against name or city, ignoring case and spaces', () => {
    service.load();
    httpMock.expectOne(`${environment.apiBaseUrl}/branches`).flush(BRANCHES);

    expect(service.search('  durban ').map((b) => b.name)).toEqual(['Gateway Branch']);
    expect(service.search('')).toEqual(BRANCHES);
  });
});
