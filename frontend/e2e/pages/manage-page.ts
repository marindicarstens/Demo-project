import { Locator, Page } from '@playwright/test';
import { pickSlot } from './slot-picker';

/** /manage and the ManageAppointment view, which the confirm page also shows. */
export class ManagePage {
  readonly status: Locator;

  constructor(private readonly page: Page) {
    this.status = page.getByRole('status', { name: /status/i });
  }

  async lookUp(referenceCode: string, email: string): Promise<void> {
    await this.page.goto('/manage');
    await this.fillLookup(referenceCode, email);
  }

  /** Fills and submits the lookup form on the current page. */
  async fillLookup(referenceCode: string, email: string): Promise<void> {
    await this.page.getByLabel('Reference code').fill(referenceCode);
    await this.page.getByLabel('Email').fill(email);
    await this.page.getByRole('button', { name: 'Find my booking' }).click();
  }

  async cancel(): Promise<void> {
    await this.page.getByRole('button', { name: 'Cancel appointment' }).click();
    await this.page.getByRole('button', { name: 'Yes, cancel' }).click();
  }

  async chooseNewTime(slot: { date: string; startTime: string }): Promise<void> {
    await this.page.getByRole('button', { name: 'Reschedule' }).click();
    await pickSlot(this.page, 'New date', slot);
  }

  /** Requests the reschedule, and returns the tab the confirm-reschedule email opens in. */
  async requestRescheduleAndOpenEmail(): Promise<Page> {
    const [emailTab] = await Promise.all([
      this.page.context().waitForEvent('page'),
      this.page.getByRole('button', { name: 'Confirm new time' }).click(),
    ]);
    await emailTab.waitForLoadState();
    return emailTab;
  }
}
