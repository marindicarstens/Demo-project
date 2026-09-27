import { ChangeDetectionStrategy, Component, inject, output } from '@angular/core';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';

export interface ContactDetails {
  fullName: string;
  email: string;
  phone: string;
}

// SA local (0821234567) or international (+27821234567): 9 digits after the prefix, not starting with 0.
const SA_PHONE_PATTERN = /^(\+27|0)[1-9]\d{8}$/;

// People type spaces ("082 123 4567"), so they are ignored when matching.
function saPhoneValidator(control: AbstractControl): ValidationErrors | null {
  const value = (control.value ?? '').replace(/\s+/g, '');
  return value === '' || SA_PHONE_PATTERN.test(value) ? null : { saPhone: true };
}

/** New Account's first step: contact details, collected before browsing. */
@Component({
  selector: 'app-contact-step',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, MatButtonModule, MatFormFieldModule, MatInputModule],
  templateUrl: './contact-step.html',
  styleUrl: '../browse-step.scss',
})
export class ContactStep {
  private readonly formBuilder = inject(FormBuilder);

  readonly confirmed = output<ContactDetails>();

  readonly form = this.formBuilder.nonNullable.group({
    fullName: ['', Validators.required],
    email: ['', [Validators.required, Validators.email]],
    phone: ['', [Validators.required, saPhoneValidator]],
  });

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const { fullName, email, phone } = this.form.getRawValue();
    // The backend expects the bare pattern, without the spaces the validator tolerates.
    this.confirmed.emit({ fullName, email, phone: phone.replace(/\s+/g, '') });
  }
}
