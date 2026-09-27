import { ComponentFixture, TestBed } from '@angular/core/testing';
import { IdentityDetails, IdentityStep } from './identity-step';

describe('IdentityStep', () => {
  let fixture: ComponentFixture<IdentityStep>;
  let emitted: IdentityDetails[];

  function page(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  function button(label: string): HTMLButtonElement | undefined {
    return Array.from(page().querySelectorAll('button')).find((b) => b.textContent?.trim() === label);
  }

  function type(control: string, value: string): void {
    const input = page().querySelector(`input[formcontrolname="${control}"]`) as HTMLInputElement;
    input.value = value;
    input.dispatchEvent(new Event('input'));
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [IdentityStep] }).compileComponents();
    fixture = TestBed.createComponent(IdentityStep);
    emitted = [];
    fixture.componentInstance.submitted.subscribe((details) => emitted.push(details));
    fixture.detectChanges();
  });

  it('needs a 13-digit ID number before it emits', () => {
    type('email', 'thandiwe@example.com');
    type('idNumber', '12345');
    type('accountNumber', '4051234567');
    button('Book this appointment')?.click();
    fixture.detectChanges();

    expect(page().querySelector('mat-error')?.textContent?.trim()).toBe('Enter your 13-digit ID number');
    expect(emitted).toEqual([]);

    type('idNumber', '9203015800082');
    button('Book this appointment')?.click();
    expect(emitted).toEqual([{ email: 'thandiwe@example.com', idNumber: '9203015800082', accountNumber: '4051234567' }]);
  });

  it('shows the mismatch alert with the New Account fallback, and disables booking while busy', () => {
    let continued = false;
    fixture.componentInstance.continueAsNewAccount.subscribe(() => (continued = true));
    fixture.componentRef.setInput('mismatchMessage', "We couldn't find a client record matching those details.");
    fixture.componentRef.setInput('booking', true);
    fixture.detectChanges();

    expect(page().querySelector('[role="alert"]')?.textContent).toContain("We couldn't find a client record");
    expect(button('Checking…')?.disabled).toBe(true);
    button('Continue as a New Account instead')?.click();
    expect(continued).toBe(true);
  });
});
