import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { SimulatedEmail } from '../../core/models/booking.model';

/**
 * Styled like a real email client, always labelled SIMULATED.
 * The action link is a plain <a href>, not a routerLink: it's a real, fully-qualified URL (what
 * a genuine emailed link would be), not an internal navigation shortcut.
 */
@Component({
  selector: 'app-simulated-email-preview',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './simulated-email-preview.html',
  styleUrl: './simulated-email-preview.scss',
})
export class SimulatedEmailPreview {
  readonly email = input.required<SimulatedEmail>();
  readonly actionLabel = input.required<string>();
}
