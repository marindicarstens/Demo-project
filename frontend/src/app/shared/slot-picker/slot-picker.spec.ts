import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNativeDateAdapter } from '@angular/material/core';
import { environment } from '../../../environments/environment';
import { AvailabilitySlot } from '../../core/models/availability-slot.model';
import { SlotPicker } from './slot-picker';

const SLOTS: AvailabilitySlot[] = [
  { id: 'slot1', date: '2026-09-18', startTime: '09:00:00', remainingCapacity: 2 },
  { id: 'slot2', date: '2026-09-18', startTime: '09:30:00', remainingCapacity: 1 },
];

describe('SlotPicker', () => {
  let fixture: ComponentFixture<SlotPicker>;
  let httpMock: HttpTestingController;

  function page(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  function slotButtons(): HTMLButtonElement[] {
    return Array.from(page().querySelectorAll<HTMLButtonElement>('button.slot-button'));
  }

  function expectAvailability(date: string): TestRequest {
    return httpMock.expectOne(
      (req) =>
        req.url === `${environment.apiBaseUrl}/branches/b1/availability` &&
        req.params.get('date') === date &&
        req.params.get('serviceTypeId') === 's1',
    );
  }

  function tryAgainButton(): HTMLButtonElement | undefined {
    return Array.from(page().querySelectorAll('button')).find((b) => b.textContent?.trim() === 'Try again');
  }

  // The field parses dates in the app's locale, Angular's default en-US (M/D/YYYY).
  function typeDate(date: Date): void {
    const input = page().querySelector('input') as HTMLInputElement;
    input.value = new Intl.DateTimeFormat('en-US').format(date);
    input.dispatchEvent(new Event('input'));
  }

  function render(date: Date): void {
    fixture.componentRef.setInput('date', date);
    fixture.detectChanges();
  }

  // The resource applies a response asynchronously.
  async function settle(): Promise<void> {
    await fixture.whenStable();
    fixture.detectChanges();
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SlotPicker],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideNativeDateAdapter()],
    }).compileComponents();

    fixture = TestBed.createComponent(SlotPicker);
    httpMock = TestBed.inject(HttpTestingController);
    fixture.componentRef.setInput('branchId', 'b1');
    fixture.componentRef.setInput('serviceTypeId', 's1');
    fixture.componentRef.setInput('min', new Date(2026, 8, 18));
    fixture.componentRef.setInput('max', new Date(2026, 9, 1));
  });

  afterEach(() => httpMock.verify());

  it('shows a loading indicator, announced by a persistent status region, until the times arrive', async () => {
    render(new Date(2026, 8, 18));

    const status = page().querySelector('[role="status"]');
    expect(status?.textContent?.trim()).toBe('Loading available times');
    expect(page().querySelector('mat-spinner')?.getAttribute('aria-hidden')).toBe('true');
    expectAvailability('2026-09-18').flush(SLOTS);
    await settle();

    expect(page().querySelector('[role="status"]')).toBe(status);
    expect(status?.textContent?.trim()).toBe('');
    expect(page().querySelector('mat-spinner')).toBeNull();
    expect(slotButtons().map((b) => b.textContent?.trim())).toEqual(['09:00', '09:30']);
  });

  it('says so when the date has no free times', async () => {
    render(new Date(2026, 8, 18));
    expectAvailability('2026-09-18').flush([]);
    await settle();

    expect(page().textContent).toContain('No available times on this date');
    expect(slotButtons()).toHaveLength(0);
  });

  it('marks only the selected slot as pressed and emits the slot, with its date, on click', async () => {
    const selected: AvailabilitySlot[] = [];
    fixture.componentInstance.slotSelected.subscribe((slot) => selected.push(slot));
    fixture.componentRef.setInput('selectedSlotId', 'slot2');
    render(new Date(2026, 8, 18));
    expectAvailability('2026-09-18').flush(SLOTS);
    await settle();

    expect(slotButtons().map((b) => b.getAttribute('aria-pressed'))).toEqual(['false', 'true']);
    expect(slotButtons()[1].classList).toContain('selected');

    slotButtons()[0].click();
    expect(selected).toEqual([SLOTS[0]]);
  });

  it('error response clears previous slots and shows an alert', async () => {
    render(new Date(2026, 8, 18));
    expectAvailability('2026-09-18').flush(SLOTS);
    await settle();
    expect(slotButtons()).toHaveLength(2);

    render(new Date(2026, 8, 19));
    expectAvailability('2026-09-19').flush(null, { status: 500, statusText: 'Server Error' });
    await settle();

    expect(slotButtons()).toHaveLength(0);
    expect(page().querySelector('[role="alert"]')?.textContent).toContain("Couldn't load times");
  });

  it('"Try again" after an error loads the same date again', async () => {
    render(new Date(2026, 8, 18));
    expectAvailability('2026-09-18').flush(null, { status: 500, statusText: 'Server Error' });
    await settle();

    tryAgainButton()?.click();
    fixture.detectChanges();
    expectAvailability('2026-09-18').flush(SLOTS);
    await settle();

    expect(page().querySelector('[role="alert"]')).toBeNull();
    expect(tryAgainButton()).toBeUndefined();
    expect(slotButtons()).toHaveLength(2);
  });

  it('a date change cancels the request still in flight and shows only the new date', async () => {
    render(new Date(2026, 8, 18));
    const first = expectAvailability('2026-09-18');

    render(new Date(2026, 8, 19));
    expect(first.cancelled).toBe(true);
    expectAvailability('2026-09-19').flush([{ ...SLOTS[0], id: 'slot3', date: '2026-09-19', startTime: '11:00:00' }]);
    await settle();

    expect(slotButtons().map((b) => b.textContent?.trim())).toEqual(['11:00']);
  });

  it('reports a date typed into the field', async () => {
    const dates: Date[] = [];
    fixture.componentInstance.dateChanged.subscribe((date) => dates.push(date));
    render(new Date(2026, 8, 18));
    expectAvailability('2026-09-18').flush(SLOTS);

    typeDate(new Date(2026, 8, 20));

    expect(dates.map((d) => d.toDateString())).toEqual([new Date(2026, 8, 20).toDateString()]);
  });

  it('does not report a typed date outside min..max, and says why', async () => {
    const dates: Date[] = [];
    fixture.componentInstance.dateChanged.subscribe((date) => dates.push(date));
    render(new Date(2026, 8, 18));
    expectAvailability('2026-09-18').flush(SLOTS);

    typeDate(new Date(2026, 9, 2));
    fixture.detectChanges();
    expect(page().querySelector('mat-error')?.textContent?.trim()).toBe('Pick a date within the next 14 days');

    typeDate(new Date(2026, 8, 17));
    fixture.detectChanges();
    expect(dates).toEqual([]);
    expect(page().querySelector('mat-error')?.textContent?.trim()).toBe('Pick a date within the next 14 days');

    // The bounds themselves are bookable.
    typeDate(new Date(2026, 9, 1));
    fixture.detectChanges();
    expect(dates.map((d) => d.toDateString())).toEqual([new Date(2026, 9, 1).toDateString()]);
    expect(page().querySelector('mat-error')).toBeNull();
  });
});
