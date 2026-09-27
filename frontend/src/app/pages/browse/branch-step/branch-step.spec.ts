import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { environment } from '../../../../environments/environment';
import { BranchStep } from './branch-step';

describe('BranchStep', () => {
  let fixture: ComponentFixture<BranchStep>;
  let httpMock: HttpTestingController;

  function page(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [BranchStep],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    fixture = TestBed.createComponent(BranchStep);
    httpMock = TestBed.inject(HttpTestingController);
    fixture.componentRef.setInput('stepNumber', 1);
    fixture.detectChanges();
  });

  afterEach(() => httpMock.verify());

  it('says the branch list failed to load and retries on "Try again"', () => {
    httpMock.expectOne(`${environment.apiBaseUrl}/branches`).flush(null, { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();

    expect(page().querySelector('[role="alert"]')?.textContent).toContain("Couldn't load the branch list.");
    const retry = Array.from(page().querySelectorAll('button')).find((b) => b.textContent?.trim() === 'Try again');
    retry?.click();
    fixture.detectChanges();

    httpMock.expectOne(`${environment.apiBaseUrl}/branches`).flush([]);
    fixture.detectChanges();
    expect(page().querySelector('[role="alert"]')).toBeNull();
  });
});
