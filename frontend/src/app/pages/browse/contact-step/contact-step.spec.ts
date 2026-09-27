import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ContactDetails, ContactStep } from './contact-step';

describe('ContactStep', () => {
  let fixture: ComponentFixture<ContactStep>;
  let emitted: ContactDetails[];

  function page(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  function type(control: string, value: string): void {
    const input = page().querySelector(`input[formcontrolname="${control}"]`) as HTMLInputElement;
    input.value = value;
    input.dispatchEvent(new Event('input'));
  }

  function submit(): void {
    (page().querySelector('button[type="submit"]') as HTMLButtonElement).click();
    fixture.detectChanges();
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [ContactStep] }).compileComponents();
    fixture = TestBed.createComponent(ContactStep);
    emitted = [];
    fixture.componentInstance.confirmed.subscribe((details) => emitted.push(details));
    fixture.detectChanges();
  });

  it('shows an error for every field and emits nothing when submitted empty', () => {
    submit();

    const errors = Array.from(page().querySelectorAll('mat-error')).map((e) => e.textContent?.trim());
    expect(errors).toEqual(['Enter your full name', 'Enter a valid email address', 'Enter a valid SA number, e.g. 082 123 4567 or +27 82 123 4567']);
    expect(emitted).toEqual([]);
  });

  it('rejects a phone number that is not South African', () => {
    type('fullName', 'Lindiwe Dube');
    type('email', 'lindiwe@example.com');
    type('phone', '+44 20 7946 0958');
    submit();

    expect(page().querySelector('mat-error')?.textContent).toContain('Enter a valid SA number');
    expect(emitted).toEqual([]);
  });

  it('emits the details with the phone spaces removed', () => {
    type('fullName', 'Lindiwe Dube');
    type('email', 'lindiwe@example.com');
    type('phone', '082 123 4567');
    submit();

    expect(emitted).toEqual([{ fullName: 'Lindiwe Dube', email: 'lindiwe@example.com', phone: '0821234567' }]);
  });
});
