import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { SimulatedEmailPreview } from '../../components/simulated-email-preview/simulated-email-preview';
import { SimulatedInboxService, StoredSimulatedEmail } from '../../core/services/simulated-inbox.service';

/**
 * The "you got mail" tab - a genuinely separate browser tab from whichever booking-flow page
 * opened it (see SimulatedInboxService), reading the email it stashed in localStorage. Read once
 * on construction: this tab represents a single simulated email, not a live inbox that updates.
 */
@Component({
  selector: 'app-simulated-inbox',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [SimulatedEmailPreview],
  templateUrl: './simulated-inbox.html',
  styleUrl: './simulated-inbox.scss',
})
export class SimulatedInboxPage {
  private readonly inboxService = inject(SimulatedInboxService);

  readonly stored = signal<StoredSimulatedEmail | null>(this.inboxService.read());
}
