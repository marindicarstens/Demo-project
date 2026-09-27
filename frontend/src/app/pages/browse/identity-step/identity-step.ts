import { ChangeDetectionStrategy, Component, inject, input, output } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';

export interface IdentityDetails {
  email: string;
  idNumber: string;
  accountNumber: string;
}

/** Existing Client's last step: identity, checked against the seeded directory on booking. */
@Component({
  selector: 'app-identity-step',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, MatButtonModule, MatFormFieldModule, MatInputModule],
  templateUrl: './identity-step.html',
  styleUrl: '../browse-step.scss',
})
export class IdentityStep {
  private readonly formBuilder = inject(FormBuilder);

  readonly booking = input(false);
  // Set only for the generic "no match" outcome, so it never hints at which field was wrong.
  readonly mismatchMessage = input<string | null>(null);
  readonly submitted = output<IdentityDetails>();
  readonly continueAsNewAccount = output<void>();

  readonly form = this.formBuilder.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    idNumber: ['', [Validators.required, Validators.pattern(/^\d{13}$/)]],
    accountNumber: ['', Validators.required],
  });

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitted.emit(this.form.getRawValue());
  }
}
