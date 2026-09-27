import { Locator, Page } from '@playwright/test';
import { AvailableSlot } from '../helpers';
import { pickSlot } from './slot-picker';

/** /browse/:clientType, from branch search to "Book this appointment". */
export class BrowsePage {
  readonly bookButton: Locator;
  readonly branchField: Locator;
  readonly dateField: Locator;

  constructor(private readonly page: Page) {
    this.bookButton = page.getByRole('button', { name: 'Book this appointment' });
    this.branchField = page.getByLabel('Branch name or city');
    this.dateField = page.getByLabel('Date');
  }

  async goto(clientType: 'new-client' | 'existing-client'): Promise<void> {
    await this.page.goto(`/browse/${clientType}`);
  }

  async chooseBranch(branchName: string): Promise<void> {
    await this.branchField.fill(branchName);
    await this.page.getByRole('option', { name: new RegExp(branchName) }).click();
  }

  async chooseService(serviceTypeName: string): Promise<void> {
    await this.page.getByRole('option', { name: new RegExp(`^${serviceTypeName}`) }).click();
  }

  /** Branch, service, date and time for the given slot. */
  async pick(slot: AvailableSlot): Promise<void> {
    await this.chooseBranch(slot.branchName);
    await this.chooseService(slot.serviceTypeName);
    await pickSlot(this.page, 'Date', slot);
  }

  async fillIdentity(identity: { email: string; idNumber: string; accountNumber: string }): Promise<void> {
    await this.page.getByLabel('Email').fill(identity.email);
    await this.page.getByLabel('ID number').fill(identity.idNumber);
    await this.page.getByLabel('Account number').fill(identity.accountNumber);
  }

  /** Books, and returns the tab the simulated confirmation email opens in. A booking error (such
   * as a 409 for a slot someone else took) fails straight away with its message, rather than
   * leaving the wait for a new tab to run into the test timeout. */
  async bookAndOpenEmail(): Promise<Page> {
    const emailTab = this.page.context().waitForEvent('page');
    const alert = this.page.getByRole('alert').first();
    const bookingError = alert.waitFor();
    await this.bookButton.click();
    const outcome = await Promise.race([emailTab, bookingError.then(() => null)]);
    if (!outcome) {
      throw new Error(`Booking failed instead of opening the email: ${await alert.textContent()}`);
    }
    await outcome.waitForLoadState();
    return outcome;
  }
}
